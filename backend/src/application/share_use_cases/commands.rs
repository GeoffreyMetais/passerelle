use crate::domain::entities::ShareLink;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub const UNLOCK_COOKIE_TTL_SECONDS: i64 = 3600;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateShareCommand {
    pub relative_path: String,
    pub custom_slug: Option<String>,
    pub password: Option<String>,
    pub expires_in_seconds: Option<i64>,
    pub max_downloads: Option<i64>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateShareCommand {
    pub is_revoked: Option<bool>,
    pub expires_at: Option<DateTime<Utc>>,
    #[serde(default)]
    pub clear_expiration: bool,
    pub max_downloads: Option<i64>,
    #[serde(default)]
    pub clear_max_downloads: bool,
    pub new_password: Option<String>,
    #[serde(default)]
    pub clear_password: bool,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ShareDto {
    pub id: Uuid,
    pub slug: String,
    pub public_url: String,
    pub direct_download_url: String,
    pub target_name: String,
    pub relative_path: String,
    pub is_directory: bool,
    pub has_password: bool,
    pub expires_at: Option<DateTime<Utc>>,
    pub max_downloads: Option<u64>,
    pub download_count: u64,
    pub is_revoked: bool,
    pub is_active: bool,
    pub created_at: DateTime<Utc>,
    pub last_accessed_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PublicShareStatus {
    Locked {
        share: ShareLink,
        total_size_bytes: u64,
    },
    Ready {
        share: ShareLink,
        total_size_bytes: u64,
    },
}
