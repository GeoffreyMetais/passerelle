use crate::domain::errors::DomainError;
use crate::domain::value_objects::{RelativeFilePath, Slug};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ShareLink {
    pub id: Uuid,
    pub slug: Slug,
    pub relative_path: RelativeFilePath,
    pub target_name: String,
    pub is_directory: bool,
    pub password_hash: Option<String>,
    pub expires_at: Option<DateTime<Utc>>,
    pub max_downloads: Option<u64>,
    pub download_count: u64,
    pub is_revoked: bool,
    pub created_at: DateTime<Utc>,
    pub last_accessed_at: Option<DateTime<Utc>>,
}

impl ShareLink {
    pub fn has_password(&self) -> bool {
        self.password_hash.is_some()
    }

    pub fn is_expired(&self, now: DateTime<Utc>) -> bool {
        self.expires_at.is_some_and(|exp| now >= exp)
    }

    pub fn is_quota_reached(&self) -> bool {
        self.max_downloads
            .is_some_and(|max| self.download_count >= max)
    }

    pub fn is_active(&self, now: DateTime<Utc>) -> bool {
        !self.is_revoked && !self.is_expired(now) && !self.is_quota_reached()
    }

    pub fn verify_access(&self, now: DateTime<Utc>) -> Result<(), DomainError> {
        if self.is_revoked {
            return Err(DomainError::ShareRevoked);
        }
        if self.is_expired(now) {
            return Err(DomainError::ShareExpired);
        }
        if self.is_quota_reached() {
            return Err(DomainError::QuotaExceeded);
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AdminSession {
    pub id: Uuid,
    pub refresh_token_hash: String,
    pub device_name: String,
    pub expires_at: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
}

impl AdminSession {
    pub fn is_expired(&self, now: DateTime<Utc>) -> bool {
        now >= self.expires_at
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SortBy {
    #[default]
    Name,
    Size,
    ModifiedAt,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SortOrder {
    #[default]
    Asc,
    Desc,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BreadcrumbItem {
    pub name: String,
    pub path: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileEntry {
    pub name: String,
    pub path: String,
    pub is_directory: bool,
    pub size_bytes: Option<u64>,
    pub modified_at: DateTime<Utc>,
    pub mime_type: Option<String>,
    pub has_thumbnail: bool,
    pub active_share_count: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DirectoryListing {
    pub current_path: String,
    pub parent_path: Option<String>,
    pub breadcrumbs: Vec<BreadcrumbItem>,
    pub entries: Vec<FileEntry>,
}

#[cfg(test)]
mod tests;
