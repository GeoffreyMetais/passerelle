use crate::domain::entities::FileEntry;
use crate::domain::errors::DomainError;
use crate::domain::ports::{FileMetadata, FileSystemPort, ThumbnailPayload, ThumbnailPort};
use crate::domain::value_objects::RelativeFilePath;
use async_trait::async_trait;
use async_zip::tokio::write::ZipFileWriter;
use async_zip::{Compression, ZipEntryBuilder};
use chrono::{DateTime, Utc};
use futures::io::AsyncWriteExt as FuturesAsyncWriteExt;
use sha2::{Digest, Sha256};
use std::io::Cursor;
use std::path::{Path, PathBuf};
use tokio::io::AsyncReadExt;
use tokio_util::io::ReaderStream;
use walkdir::WalkDir;

const HIDDEN_SYSTEM_NAMES: &[&str] = &["@eaDir", "Thumbs.db", ".DS_Store", "#recycle"];
const THUMBNAIL_EXTS: &[&str] = &["jpg", "jpeg", "png", "webp", "gif"];

pub fn is_hidden_name(name: &str) -> bool {
    name.starts_with('.') || HIDDEN_SYSTEM_NAMES.contains(&name)
}

pub fn has_thumbnail_support(name: &str) -> bool {
    Path::new(name)
        .extension()
        .and_then(|e| e.to_str())
        .is_some_and(|ext| THUMBNAIL_EXTS.contains(&ext.to_ascii_lowercase().as_str()))
}

#[derive(Clone)]
pub struct PathGuard {
    canonical_root: PathBuf,
}

impl PathGuard {
    pub fn new(data_dir: &Path) -> Result<Self, DomainError> {
        let canonical_root = std::fs::canonicalize(data_dir).map_err(|e| {
            DomainError::Internal(format!(
                "DATA_DIR inaccessible ({}): {e}",
                data_dir.display()
            ))
        })?;
        if !canonical_root.is_dir() {
            return Err(DomainError::Internal(format!(
                "DATA_DIR n'est pas un dossier: {}",
                canonical_root.display()
            )));
        }
        Ok(Self { canonical_root })
    }

    pub fn root(&self) -> &Path {
        &self.canonical_root
    }

    pub fn resolve(&self, rel: &RelativeFilePath) -> Result<PathBuf, DomainError> {
        let joined = if rel.is_root() {
            self.canonical_root.clone()
        } else {
            self.canonical_root.join(rel.as_str())
        };

        let canonical = match std::fs::canonicalize(&joined) {
            Ok(p) => p,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                return Err(DomainError::NotFound);
            }
            Err(e) if e.kind() == std::io::ErrorKind::PermissionDenied => {
                return Err(DomainError::ForbiddenPath);
            }
            Err(e) => {
                return Err(DomainError::Internal(format!(
                    "Path canonicalize error: {e}"
                )));
            }
        };

        if !canonical.starts_with(&self.canonical_root) {
            return Err(DomainError::ForbiddenPath);
        }
        Ok(canonical)
    }

    pub fn is_within_root(&self, candidate: &Path) -> bool {
        std::fs::canonicalize(candidate)
            .map(|c| c.starts_with(&self.canonical_root))
            .unwrap_or(false)
    }
}

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

#[derive(Clone)]
pub struct WebpThumbnailer {
    guard: PathGuard,
    cache_dir: PathBuf,
}

impl WebpThumbnailer {
    pub async fn new(guard: PathGuard, state_dir: &Path) -> Result<Self, DomainError> {
        let cache_dir = state_dir.join("cache").join("thumbnails");
        tokio::fs::create_dir_all(&cache_dir).await.map_err(|e| {
            DomainError::Internal(format!("Impossible de créer cache/thumbnails: {e}"))
        })?;
        Ok(Self { guard, cache_dir })
    }
}

#[async_trait]
impl ThumbnailPort for WebpThumbnailer {
    async fn get_or_generate_webp(
        &self,
        path: &RelativeFilePath,
    ) -> Result<ThumbnailPayload, DomainError> {
        let guard = self.guard.clone();
        let cache_dir = self.cache_dir.clone();
        let rel = path.clone();

        tokio::task::spawn_blocking(move || {
            let source = guard.resolve(&rel)?;
            let meta = std::fs::metadata(&source).map_err(|_| DomainError::NotFound)?;
            if !meta.is_file() {
                return Err(DomainError::NotFound);
            }
            let mtime_nanos = meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_nanos())
                .unwrap_or(0);

            let mut hasher = Sha256::new();
            hasher.update(rel.as_str().as_bytes());
            hasher.update(b":");
            hasher.update(mtime_nanos.to_le_bytes());
            let hash_hex = hex::encode(hasher.finalize());
            let etag = format!("\"{hash_hex}\"");
            let cache_path = cache_dir.join(format!("{hash_hex}.webp"));

            if cache_path.exists() {
                if let Ok(bytes) = std::fs::read(&cache_path) {
                    return Ok(ThumbnailPayload { bytes, etag });
                }
            }

            let img = image::open(&source)
                .map_err(|e| DomainError::Internal(format!("Image decode error: {e}")))?;
            let resized = img.thumbnail(256, 256);

            let mut buf = Vec::new();
            let mut cursor = Cursor::new(&mut buf);
            resized
                .write_to(&mut cursor, image::ImageFormat::WebP)
                .map_err(|e| DomainError::Internal(format!("WebP encode error: {e}")))?;

            let _ = std::fs::write(&cache_path, &buf);

            Ok(ThumbnailPayload { bytes: buf, etag })
        })
        .await
        .map_err(|e| DomainError::Internal(format!("spawn_blocking thumbnail error: {e}")))?
    }
}

/// Generates a ZIP64 stream at constant memory (`Stored` mode, no temporary disk storage).
pub fn stream_directory_zip(
    guard: PathGuard,
    dir_path: PathBuf,
    root_folder_name: String,
) -> ReaderStream<tokio::io::DuplexStream> {
    let (writer_half, reader_half) = tokio::io::duplex(65536);

    tokio::spawn(async move {
        let mut zip_writer = ZipFileWriter::with_tokio(writer_half);

        let files: Vec<(PathBuf, String)> = tokio::task::spawn_blocking({
            let guard = guard.clone();
            let dir = dir_path.clone();
            move || collect_zip_entries(&guard, &dir, &root_folder_name)
        })
        .await
        .unwrap_or_default();

        let mut buffer = vec![0u8; 65536];
        for (abs_path, archive_rel_path) in files {
            let Ok(mut file) = tokio::fs::File::open(&abs_path).await else {
                continue;
            };
            let entry_builder = ZipEntryBuilder::new(archive_rel_path.into(), Compression::Stored);
            let Ok(mut entry_writer) = zip_writer.write_entry_stream(entry_builder).await else {
                break;
            };

            while let Ok(n) = file.read(&mut buffer).await {
                if n == 0 {
                    break;
                }
                if FuturesAsyncWriteExt::write_all(&mut entry_writer, &buffer[..n])
                    .await
                    .is_err()
                {
                    return;
                }
            }

            if entry_writer.close().await.is_err() {
                return;
            }
        }

        let _ = zip_writer.close().await;
    });

    ReaderStream::new(reader_half)
}

fn collect_zip_entries(
    guard: &PathGuard,
    dir: &Path,
    root_folder_name: &str,
) -> Vec<(PathBuf, String)> {
    let mut files = Vec::new();
    for entry in WalkDir::new(dir)
        .follow_links(false)
        .sort_by_file_name()
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
            if let Ok(rel) = entry.path().strip_prefix(dir) {
                let rel_str = rel.to_string_lossy().replace('\\', "/");
                let archive_path = format!("{root_folder_name}/{rel_str}");
                files.push((entry.path().to_path_buf(), archive_path));
            }
        }
    }
    files
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;
    use tempfile::TempDir;

    #[test]
    fn path_guard_blocks_traversal_and_escaping_symlinks() {
        let data_dir = TempDir::new().unwrap();
        let outside_dir = TempDir::new().unwrap();
        let secret_file = outside_dir.path().join("secret.txt");
        std::fs::write(&secret_file, "top-secret").unwrap();

        let inside_file = data_dir.path().join("ok.txt");
        std::fs::write(&inside_file, "hello").unwrap();

        #[cfg(unix)]
        std::os::unix::fs::symlink(&secret_file, data_dir.path().join("escape_link")).unwrap();

        let guard = PathGuard::new(data_dir.path()).unwrap();
        assert!(
            guard
                .resolve(&RelativeFilePath::parse("ok.txt").unwrap())
                .is_ok()
        );

        #[cfg(unix)]
        assert_eq!(
            guard.resolve(&RelativeFilePath::parse("escape_link").unwrap()),
            Err(DomainError::ForbiddenPath)
        );
    }

    #[tokio::test]
    async fn stream_directory_zip_produces_valid_archive_excluding_hidden_files() {
        let data_dir = TempDir::new().unwrap();
        let target = data_dir.path().join("Vacances");
        let sub = target.join("Japon");
        std::fs::create_dir_all(&sub).unwrap();
        std::fs::write(target.join("notes.txt"), b"Hello Japan").unwrap();
        std::fs::write(sub.join("kyoto.txt"), b"Kyoto shrine").unwrap();
        std::fs::write(target.join(".DS_Store"), b"ignored").unwrap();
        std::fs::create_dir_all(target.join("@eaDir")).unwrap();
        std::fs::write(target.join("@eaDir").join("thumb"), b"ignored").unwrap();

        let guard = PathGuard::new(data_dir.path()).unwrap();
        let mut stream = stream_directory_zip(guard, target, "Vacances".to_string());

        use futures::StreamExt;
        let mut zip_bytes = Vec::new();
        while let Some(chunk) = stream.next().await {
            zip_bytes.extend_from_slice(&chunk.unwrap());
        }

        let reader = Cursor::new(zip_bytes);
        let mut archive = zip::ZipArchive::new(reader).unwrap();
        assert_eq!(archive.len(), 2);

        let mut names = Vec::new();
        for i in 0..archive.len() {
            let mut file = archive.by_index(i).unwrap();
            let mut content = String::new();
            file.read_to_string(&mut content).unwrap();
            names.push((file.name().to_string(), content));
        }
        names.sort_by(|a, b| a.0.cmp(&b.0));

        assert_eq!(
            names,
            vec![
                (
                    "Vacances/Japon/kyoto.txt".to_string(),
                    "Kyoto shrine".to_string()
                ),
                ("Vacances/notes.txt".to_string(), "Hello Japan".to_string()),
            ]
        );
    }
}
