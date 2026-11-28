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
        // Directories are ALWAYS grouped before files.
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
mod tests;
