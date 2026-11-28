//! Share use cases: admin-side CRUD/QR and public-side access/download, split by responsibility.

mod admin;
mod commands;
mod public;

#[cfg(test)]
mod tests;

pub use commands::*;

use crate::domain::entities::ShareLink;
use crate::domain::ports::{FileSystemPort, PasswordHasher, ShareRepository, TokenService};
use chrono::{DateTime, Utc};
use std::sync::Arc;

#[derive(Clone)]
pub struct ShareUseCases {
    pub(crate) shares: Arc<dyn ShareRepository>,
    pub(crate) fs: Arc<dyn FileSystemPort>,
    pub(crate) hasher: Arc<dyn PasswordHasher>,
    pub(crate) token_service: Arc<dyn TokenService>,
    pub(crate) public_base_url: String,
    pub(crate) max_zip_size_bytes: u64,
}

impl ShareUseCases {
    pub fn new(
        shares: Arc<dyn ShareRepository>,
        fs: Arc<dyn FileSystemPort>,
        hasher: Arc<dyn PasswordHasher>,
        token_service: Arc<dyn TokenService>,
        public_base_url: String,
        max_zip_size_bytes: u64,
    ) -> Self {
        Self {
            shares,
            fs,
            hasher,
            token_service,
            public_base_url: public_base_url.trim_end_matches('/').to_string(),
            max_zip_size_bytes,
        }
    }

    pub fn to_dto(&self, share: &ShareLink, now: DateTime<Utc>) -> ShareDto {
        ShareDto {
            id: share.id,
            slug: share.slug.as_str().to_string(),
            public_url: format!("{}/{}", self.public_base_url, share.slug.as_str()),
            direct_download_url: format!("{}/d/{}", self.public_base_url, share.slug.as_str()),
            target_name: share.target_name.clone(),
            relative_path: share.relative_path.as_str().to_string(),
            is_directory: share.is_directory,
            has_password: share.has_password(),
            expires_at: share.expires_at,
            max_downloads: share.max_downloads,
            download_count: share.download_count,
            is_revoked: share.is_revoked,
            is_active: share.is_active(now),
            created_at: share.created_at,
            last_accessed_at: share.last_accessed_at,
        }
    }
}
