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
mod tests {
    use super::*;
    use chrono::Duration;

    fn sample_share() -> ShareLink {
        let now = Utc::now();
        ShareLink {
            id: Uuid::now_v7(),
            slug: Slug::from_trusted("test-slug"),
            relative_path: RelativeFilePath::root(),
            target_name: "Vacances".to_string(),
            is_directory: true,
            password_hash: None,
            expires_at: None,
            max_downloads: None,
            download_count: 0,
            is_revoked: false,
            created_at: now,
            last_accessed_at: None,
        }
    }

    #[test]
    fn share_link_active_when_not_revoked_not_expired_under_quota() {
        let now = Utc::now();
        let mut share = sample_share();
        share.expires_at = Some(now + Duration::hours(1));
        share.max_downloads = Some(5);
        share.download_count = 4;

        assert!(!share.is_expired(now));
        assert!(!share.is_quota_reached());
        assert!(share.is_active(now));
        assert_eq!(share.verify_access(now), Ok(()));
    }

    #[test]
    fn share_link_revoked_returns_share_revoked() {
        let now = Utc::now();
        let mut share = sample_share();
        share.is_revoked = true;

        assert!(!share.is_active(now));
        assert_eq!(share.verify_access(now), Err(DomainError::ShareRevoked));
    }

    #[test]
    fn share_link_expired_returns_share_expired() {
        let now = Utc::now();
        let mut share = sample_share();
        share.expires_at = Some(now - Duration::seconds(1));

        assert!(share.is_expired(now));
        assert!(!share.is_active(now));
        assert_eq!(share.verify_access(now), Err(DomainError::ShareExpired));

        // Exact boundary timestamp is also expired
        share.expires_at = Some(now);
        assert!(share.is_expired(now));
        assert_eq!(share.verify_access(now), Err(DomainError::ShareExpired));
    }

    #[test]
    fn share_link_quota_reached_returns_quota_exceeded() {
        let now = Utc::now();
        let mut share = sample_share();
        share.max_downloads = Some(2);
        share.download_count = 2;

        assert!(share.is_quota_reached());
        assert!(!share.is_active(now));
        assert_eq!(share.verify_access(now), Err(DomainError::QuotaExceeded));
    }
}
