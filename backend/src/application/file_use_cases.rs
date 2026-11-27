use crate::domain::entities::{BreadcrumbItem, DirectoryListing, FileEntry, SortBy, SortOrder};
use crate::domain::errors::DomainError;
use crate::domain::ports::{
    FileMetadata, FileSystemPort, ShareRepository, ThumbnailPayload, ThumbnailPort,
};
use crate::domain::value_objects::RelativeFilePath;
use chrono::Utc;
use std::cmp::Ordering;
use std::sync::Arc;

#[derive(Clone)]
pub struct FileUseCases {
    fs: Arc<dyn FileSystemPort>,
    shares: Arc<dyn ShareRepository>,
    thumbnailer: Arc<dyn ThumbnailPort>,
}

impl FileUseCases {
    pub fn new(
        fs: Arc<dyn FileSystemPort>,
        shares: Arc<dyn ShareRepository>,
        thumbnailer: Arc<dyn ThumbnailPort>,
    ) -> Self {
        Self {
            fs,
            shares,
            thumbnailer,
        }
    }

    pub async fn list_directory(
        &self,
        raw_path: &str,
        sort_by: SortBy,
        sort_order: SortOrder,
        show_hidden: bool,
    ) -> Result<DirectoryListing, DomainError> {
        let rel_path = RelativeFilePath::parse(raw_path)?;
        let mut entries = self.fs.list_directory(&rel_path, show_hidden).await?;

        self.enrich_share_counts(&mut entries).await?;
        sort_entries(&mut entries, sort_by, sort_order);

        let breadcrumbs = build_breadcrumbs(&rel_path);
        let parent_path = rel_path.parent().map(|p| p.as_str().to_string());

        Ok(DirectoryListing {
            current_path: rel_path.as_str().to_string(),
            parent_path,
            breadcrumbs,
            entries,
        })
    }

    pub async fn search_files(
        &self,
        query: &str,
        raw_base_path: &str,
        show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError> {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return Err(DomainError::ValidationError(
                "Le paramètre de recherche 'q' est obligatoire.".to_string(),
            ));
        }

        let base_path = RelativeFilePath::parse(raw_base_path)?;
        let mut results = self.fs.search(&base_path, trimmed, show_hidden).await?;
        self.enrich_share_counts(&mut results).await?;
        sort_entries(&mut results, SortBy::Name, SortOrder::Asc);
        Ok(results)
    }

    pub async fn get_thumbnail(&self, raw_path: &str) -> Result<ThumbnailPayload, DomainError> {
        let rel_path = RelativeFilePath::parse(raw_path)?;
        if rel_path.is_root() {
            return Err(DomainError::NotFound);
        }
        let meta = self.fs.get_metadata(&rel_path).await?;
        if meta.is_directory || !meta.has_thumbnail {
            return Err(DomainError::NotFound);
        }
        self.thumbnailer.get_or_generate_webp(&rel_path).await
    }

    pub async fn resolve_download(&self, raw_path: &str) -> Result<FileMetadata, DomainError> {
        let rel_path = RelativeFilePath::parse(raw_path)?;
        if rel_path.is_root() {
            return Err(DomainError::NotFound);
        }
        let meta = self.fs.get_metadata(&rel_path).await?;
        if meta.is_directory {
            return Err(DomainError::ValidationError(
                "Le téléchargement direct admin ne supporte que les fichiers.".to_string(),
            ));
        }
        Ok(meta)
    }

    async fn enrich_share_counts(&self, entries: &mut [FileEntry]) -> Result<(), DomainError> {
        if entries.is_empty() {
            return Ok(());
        }
        let paths: Vec<String> = entries.iter().map(|e| e.path.clone()).collect();
        let counts = self
            .shares
            .count_active_by_paths(&paths, Utc::now())
            .await?;
        for entry in entries.iter_mut() {
            entry.active_share_count = counts.get(&entry.path).copied().unwrap_or(0);
        }
        Ok(())
    }
}

fn build_breadcrumbs(path: &RelativeFilePath) -> Vec<BreadcrumbItem> {
    let mut crumbs = vec![BreadcrumbItem {
        name: "Racine".to_string(),
        path: String::new(),
    }];

    if path.is_root() {
        return crumbs;
    }

    let mut acc = String::new();
    for segment in path.as_str().split('/') {
        if !acc.is_empty() {
            acc.push('/');
        }
        acc.push_str(segment);
        crumbs.push(BreadcrumbItem {
            name: segment.to_string(),
            path: acc.clone(),
        });
    }
    crumbs
}

fn sort_entries(entries: &mut [FileEntry], sort_by: SortBy, sort_order: SortOrder) {
    entries.sort_by(|a, b| {
        // Les dossiers sont TOUJOURS groupés avant les fichiers.
        match (a.is_directory, b.is_directory) {
            (true, false) => return Ordering::Less,
            (false, true) => return Ordering::Greater,
            _ => {}
        }

        let ord = match sort_by {
            SortBy::Name => a
                .name
                .to_ascii_lowercase()
                .cmp(&b.name.to_ascii_lowercase())
                .then_with(|| a.name.cmp(&b.name)),
            SortBy::Size => a
                .size_bytes
                .unwrap_or(0)
                .cmp(&b.size_bytes.unwrap_or(0))
                .then_with(|| {
                    a.name
                        .to_ascii_lowercase()
                        .cmp(&b.name.to_ascii_lowercase())
                }),
            SortBy::ModifiedAt => a.modified_at.cmp(&b.modified_at).then_with(|| {
                a.name
                    .to_ascii_lowercase()
                    .cmp(&b.name.to_ascii_lowercase())
            }),
        };

        match sort_order {
            SortOrder::Asc => ord,
            SortOrder::Desc => ord.reverse(),
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::application::fakes::{FakeFileSystem, FakeShareRepository, FakeThumbnailer};
    use crate::domain::entities::ShareLink;
    use crate::domain::value_objects::Slug;
    use chrono::Duration;
    use uuid::Uuid;

    fn setup_fs() -> (FileUseCases, Arc<FakeShareRepository>) {
        let now = Utc::now();
        let fs = Arc::new(FakeFileSystem::default());
        let shares = Arc::new(FakeShareRepository::default());
        let thumb = Arc::new(FakeThumbnailer);

        let entries = vec![
            FileEntry {
                name: "zeta.txt".to_string(),
                path: "Photos/Vacances/zeta.txt".to_string(),
                is_directory: false,
                size_bytes: Some(100),
                modified_at: now - Duration::hours(5),
                mime_type: Some("text/plain".to_string()),
                has_thumbnail: false,
                active_share_count: 0,
            },
            FileEntry {
                name: "Japon".to_string(),
                path: "Photos/Vacances/Japon".to_string(),
                is_directory: true,
                size_bytes: None,
                modified_at: now - Duration::hours(1),
                mime_type: None,
                has_thumbnail: false,
                active_share_count: 0,
            },
            FileEntry {
                name: "alpha.jpg".to_string(),
                path: "Photos/Vacances/alpha.jpg".to_string(),
                is_directory: false,
                size_bytes: Some(5000),
                modified_at: now,
                mime_type: Some("image/jpeg".to_string()),
                has_thumbnail: true,
                active_share_count: 0,
            },
        ];

        {
            let mut lock = fs.entries.lock().unwrap();
            lock.push((
                "Photos/Vacances".to_string(),
                FileMetadata {
                    name: "Vacances".to_string(),
                    relative_path: RelativeFilePath::from_trusted("Photos/Vacances"),
                    is_directory: true,
                    size_bytes: None,
                    total_size_bytes: 5100,
                    modified_at: now,
                    mime_type: None,
                    has_thumbnail: false,
                },
                Some(entries),
            ));
            lock.push((
                "Photos/Vacances/alpha.jpg".to_string(),
                FileMetadata {
                    name: "alpha.jpg".to_string(),
                    relative_path: RelativeFilePath::from_trusted("Photos/Vacances/alpha.jpg"),
                    is_directory: false,
                    size_bytes: Some(5000),
                    total_size_bytes: 5000,
                    modified_at: now,
                    mime_type: Some("image/jpeg".to_string()),
                    has_thumbnail: true,
                },
                None,
            ));
        }

        (FileUseCases::new(fs, shares.clone(), thumb), shares)
    }

    #[tokio::test]
    async fn list_directory_groups_folders_first_builds_breadcrumbs_and_enriches_shares() {
        let (uc, shares) = setup_fs();
        shares
            .create(&ShareLink {
                id: Uuid::now_v7(),
                slug: Slug::from_trusted("japon-2026"),
                relative_path: RelativeFilePath::from_trusted("Photos/Vacances/Japon"),
                target_name: "Japon".to_string(),
                is_directory: true,
                password_hash: None,
                expires_at: None,
                max_downloads: None,
                download_count: 0,
                is_revoked: false,
                created_at: Utc::now(),
                last_accessed_at: None,
            })
            .await
            .unwrap();

        let listing = uc
            .list_directory("Photos/Vacances", SortBy::Name, SortOrder::Desc, false)
            .await
            .unwrap();

        assert_eq!(listing.current_path, "Photos/Vacances");
        assert_eq!(listing.parent_path.as_deref(), Some("Photos"));
        assert_eq!(listing.breadcrumbs.len(), 3);
        assert_eq!(listing.breadcrumbs[0].name, "Racine");
        assert_eq!(listing.breadcrumbs[1].path, "Photos");
        assert_eq!(listing.breadcrumbs[2].path, "Photos/Vacances");

        // Folder "Japon" stays first even in Desc order!
        assert_eq!(listing.entries[0].name, "Japon");
        assert_eq!(listing.entries[0].active_share_count, 1);
        assert_eq!(listing.entries[1].name, "zeta.txt");
        assert_eq!(listing.entries[2].name, "alpha.jpg");
    }

    #[tokio::test]
    async fn search_files_rejects_empty_query_and_returns_matches() {
        let (uc, _) = setup_fs();
        assert_eq!(
            uc.search_files("   ", "", false).await,
            Err(DomainError::ValidationError(
                "Le paramètre de recherche 'q' est obligatoire.".to_string()
            ))
        );

        let results = uc.search_files("ALPHA", "", false).await.unwrap();
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].name, "alpha.jpg");
    }

    #[tokio::test]
    async fn thumbnail_only_allowed_for_supported_files() {
        let (uc, _) = setup_fs();
        assert_eq!(
            uc.get_thumbnail("Photos/Vacances").await.unwrap_err(),
            DomainError::NotFound
        );
        let payload = uc.get_thumbnail("Photos/Vacances/alpha.jpg").await.unwrap();
        assert!(!payload.bytes.is_empty());
    }
}
