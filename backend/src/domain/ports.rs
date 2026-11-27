use crate::domain::entities::{AdminSession, FileEntry, ShareLink};
use crate::domain::errors::DomainError;
use crate::domain::value_objects::{RelativeFilePath, Slug};
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use std::collections::HashMap;
use uuid::Uuid;

#[derive(Debug, Clone)]
pub struct FileMetadata {
    pub name: String,
    pub relative_path: RelativeFilePath,
    pub is_directory: bool,
    pub size_bytes: Option<u64>,
    pub total_size_bytes: u64,
    pub modified_at: DateTime<Utc>,
    pub mime_type: Option<String>,
    pub has_thumbnail: bool,
}

#[derive(Debug, Clone)]
pub struct ThumbnailPayload {
    pub bytes: Vec<u8>,
    pub etag: String,
}

#[derive(Debug, Clone)]
pub struct SharePatch {
    pub is_revoked: Option<bool>,
    pub expires_at: Option<DateTime<Utc>>,
    pub clear_expiration: bool,
    pub max_downloads: Option<u64>,
    pub clear_max_downloads: bool,
    pub new_password_hash: Option<String>,
    pub clear_password: bool,
}

#[async_trait]
pub trait ShareRepository: Send + Sync {
    async fn create(&self, share: &ShareLink) -> Result<(), DomainError>;
    async fn find_by_id(&self, id: Uuid) -> Result<Option<ShareLink>, DomainError>;
    async fn find_by_slug(&self, slug: &Slug) -> Result<Option<ShareLink>, DomainError>;
    async fn list(
        &self,
        path_filter: Option<&RelativeFilePath>,
        active_only: bool,
        now: DateTime<Utc>,
    ) -> Result<Vec<ShareLink>, DomainError>;
    async fn count_active_by_paths(
        &self,
        paths: &[String],
        now: DateTime<Utc>,
    ) -> Result<HashMap<String, u64>, DomainError>;
    async fn update(&self, id: Uuid, patch: SharePatch) -> Result<ShareLink, DomainError>;
    async fn delete(&self, id: Uuid) -> Result<(), DomainError>;
    async fn increment_download_atomic(
        &self,
        id: Uuid,
        now: DateTime<Utc>,
    ) -> Result<bool, DomainError>;
}

#[async_trait]
pub trait SessionRepository: Send + Sync {
    async fn create(&self, session: &AdminSession) -> Result<(), DomainError>;
    async fn find_by_token_hash(
        &self,
        token_hash: &str,
    ) -> Result<Option<AdminSession>, DomainError>;
    async fn rotate_token(
        &self,
        id: Uuid,
        new_token_hash: &str,
        new_expires_at: DateTime<Utc>,
    ) -> Result<(), DomainError>;
    async fn delete_by_id(&self, id: Uuid) -> Result<(), DomainError>;
}

#[async_trait]
pub trait FileSystemPort: Send + Sync {
    async fn list_directory(
        &self,
        path: &RelativeFilePath,
        show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError>;
    async fn search(
        &self,
        base_path: &RelativeFilePath,
        query: &str,
        show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError>;
    async fn get_metadata(&self, path: &RelativeFilePath) -> Result<FileMetadata, DomainError>;
}

pub trait PasswordHasher: Send + Sync {
    fn hash_password(&self, password: &str) -> Result<String, DomainError>;
    fn verify_password(&self, password: &str, hash: &str) -> Result<bool, DomainError>;
}

pub trait TokenService: Send + Sync {
    fn issue_access_token(&self, session_id: Uuid, username: &str) -> Result<String, DomainError>;
    fn verify_access_token(&self, token: &str) -> Result<(Uuid, String), DomainError>;
    fn generate_refresh_token(&self) -> String;
    fn hash_refresh_token(&self, token: &str) -> String;
    fn sign_unlock_cookie(&self, slug: &str, ttl_seconds: i64) -> Result<String, DomainError>;
    fn verify_unlock_cookie(&self, cookie_value: &str, expected_slug: &str) -> bool;
}

#[async_trait]
pub trait ThumbnailPort: Send + Sync {
    async fn get_or_generate_webp(
        &self,
        path: &RelativeFilePath,
    ) -> Result<ThumbnailPayload, DomainError>;
}
