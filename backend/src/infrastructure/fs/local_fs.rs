use super::{PathGuard, has_thumbnail_support, is_hidden_name};
use crate::domain::entities::FileEntry;
use crate::domain::errors::DomainError;
use crate::domain::ports::{FileMetadata, FileSystemPort};
use crate::domain::value_objects::RelativeFilePath;
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use std::path::Path;
use walkdir::WalkDir;

#[derive(Clone)]
pub struct LocalFileSystem {
    guard: PathGuard,
}

impl LocalFileSystem {
    pub fn new(guard: PathGuard) -> Self {
        Self { guard }
    }

    pub fn guard(&self) -> &PathGuard {
        &self.guard
    }
}

#[async_trait]
impl FileSystemPort for LocalFileSystem {
    async fn list_directory(
        &self,
        path: &RelativeFilePath,
        show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError> {
        let guard = self.guard.clone();
        let rel = path.clone();

        tokio::task::spawn_blocking(move || {
            let target = guard.resolve(&rel)?;
            let meta = std::fs::metadata(&target).map_err(|_| DomainError::NotFound)?;
            if !meta.is_dir() {
                return Err(DomainError::NotFound);
            }

            let read_dir = std::fs::read_dir(&target)
                .map_err(|e| DomainError::Internal(format!("read_dir error: {e}")))?;

            let mut out = Vec::new();
            for entry_res in read_dir {
                let Ok(entry) = entry_res else {
                    continue;
                };
                let name = entry.file_name().to_string_lossy().to_string();
                if !show_hidden && is_hidden_name(&name) {
                    continue;
                }

                // Ensure symlinks do not point outside DATA_DIR
                let entry_path = entry.path();
                if !guard.is_within_root(&entry_path) {
                    continue;
                }

                let Ok(entry_meta) = std::fs::metadata(&entry_path) else {
                    continue;
                };

                let child_rel = if rel.is_root() {
                    name.clone()
                } else {
                    format!("{}/{}", rel.as_str(), name)
                };

                out.push(build_file_entry(name, child_rel, &entry_meta));
            }

            Ok(out)
        })
        .await
        .map_err(|e| DomainError::Internal(format!("spawn_blocking error: {e}")))?
    }

    async fn search(
        &self,
        base_path: &RelativeFilePath,
        query: &str,
        show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError> {
        let guard = self.guard.clone();
        let base_rel = base_path.clone();
        let q_lower = query.to_ascii_lowercase();

        tokio::task::spawn_blocking(move || {
            let start_dir = guard.resolve(&base_rel)?;
            let start_meta = std::fs::metadata(&start_dir).map_err(|_| DomainError::NotFound)?;
            if !start_meta.is_dir() {
                return Err(DomainError::NotFound);
            }

            let mut results = Vec::new();
            let walker = WalkDir::new(&start_dir).follow_links(false).into_iter();
            for item in walker.filter_entry(|e| {
                if e.depth() == 0 {
                    return true;
                }
                let name = e.file_name().to_string_lossy();
                if !show_hidden && is_hidden_name(&name) {
                    return false;
                }
                guard.is_within_root(e.path())
            }) {
                let Ok(entry) = item else {
                    continue;
                };
                if entry.depth() == 0 {
                    continue;
                }
                let name = entry.file_name().to_string_lossy().to_string();
                if !name.to_ascii_lowercase().contains(&q_lower) {
                    continue;
                }
                let Ok(rel_from_start) = entry.path().strip_prefix(&start_dir) else {
                    continue;
                };
                let rel_sub = rel_from_start.to_string_lossy().replace('\\', "/");
                let full_rel = if base_rel.is_root() {
                    rel_sub
                } else {
                    format!("{}/{}", base_rel.as_str(), rel_sub)
                };
                let Ok(meta) = entry.metadata() else {
                    continue;
                };
                results.push(build_file_entry(name, full_rel, &meta));
                if results.len() >= 500 {
                    break;
                }
            }

            Ok(results)
        })
        .await
        .map_err(|e| DomainError::Internal(format!("spawn_blocking search error: {e}")))?
    }

    async fn get_metadata(&self, path: &RelativeFilePath) -> Result<FileMetadata, DomainError> {
        let guard = self.guard.clone();
        let rel = path.clone();

        tokio::task::spawn_blocking(move || {
            let target = guard.resolve(&rel)?;
            let meta = std::fs::metadata(&target).map_err(|_| DomainError::NotFound)?;
            let is_dir = meta.is_dir();
            let modified_at: DateTime<Utc> = meta
                .modified()
                .map(DateTime::<Utc>::from)
                .unwrap_or_else(|_| Utc::now());

            let name = rel
                .file_name()
                .map(ToString::to_string)
                .unwrap_or_else(|| "Racine".to_string());

            if is_dir {
                let total_size = compute_dir_size(&guard, &target);
                Ok(FileMetadata {
                    name,
                    relative_path: rel,
                    is_directory: true,
                    size_bytes: None,
                    total_size_bytes: total_size,
                    modified_at,
                    mime_type: None,
                    has_thumbnail: false,
                })
            } else {
                let mime_type = mime_guess::from_path(&name)
                    .first_raw()
                    .map(ToString::to_string);
                let has_thumbnail = has_thumbnail_support(&name);
                Ok(FileMetadata {
                    name,
                    relative_path: rel,
                    is_directory: false,
                    size_bytes: Some(meta.len()),
                    total_size_bytes: meta.len(),
                    modified_at,
                    mime_type,
                    has_thumbnail,
                })
            }
        })
        .await
        .map_err(|e| DomainError::Internal(format!("spawn_blocking metadata error: {e}")))?
    }
}

fn build_file_entry(name: String, path: String, meta: &std::fs::Metadata) -> FileEntry {
    let is_dir = meta.is_dir();
    let modified_at = meta
        .modified()
        .map(DateTime::<Utc>::from)
        .unwrap_or_else(|_| Utc::now());

    if is_dir {
        FileEntry {
            name,
            path,
            is_directory: true,
            size_bytes: None,
            modified_at,
            mime_type: None,
            has_thumbnail: false,
            active_share_count: 0,
        }
    } else {
        let mime_type = mime_guess::from_path(&name)
            .first_raw()
            .map(ToString::to_string);
        let has_thumbnail = has_thumbnail_support(&name);
        FileEntry {
            name,
            path,
            is_directory: false,
            size_bytes: Some(meta.len()),
            modified_at,
            mime_type,
            has_thumbnail,
            active_share_count: 0,
        }
    }
}

fn compute_dir_size(guard: &PathGuard, dir: &Path) -> u64 {
    let mut total = 0u64;
    for entry in WalkDir::new(dir)
        .follow_links(false)
        .into_iter()
        .filter_entry(|e| {
            if e.depth() == 0 {
                return true;
            }
            let name = e.file_name().to_string_lossy();
            !is_hidden_name(&name) && guard.is_within_root(e.path())
        })
        .flatten()
    {
        if entry.file_type().is_file() {
            if let Ok(m) = entry.metadata() {
                total = total.saturating_add(m.len());
            }
        }
    }
    total
}
