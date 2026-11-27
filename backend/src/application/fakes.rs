use crate::domain::entities::{
    AdminSession, DirectoryListing, FileEntry, ShareLink, SortBy, SortOrder,
};
use crate::domain::errors::DomainError;
use crate::domain::ports::{
    FileMetadata, FileSystemPort, PasswordHasher, SessionRepository, SharePatch, ShareRepository,
    ThumbnailPayload, ThumbnailPort, TokenService,
};
use crate::domain::value_objects::{RelativeFilePath, Slug};
use async_trait::async_trait;
use chrono::{DateTime, Duration, Utc};
use sha2::{Digest, Sha256};
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use uuid::Uuid;

#[derive(Default)]
pub struct FakeShareRepository {
    pub shares: Mutex<Vec<ShareLink>>,
}

#[async_trait]
impl ShareRepository for FakeShareRepository {
    async fn create(&self, share: &ShareLink) -> Result<(), DomainError> {
        let mut shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        if shares
            .iter()
            .any(|s| s.slug.as_str().eq_ignore_ascii_case(share.slug.as_str()))
        {
            return Err(DomainError::SlugAlreadyExists);
        }
        shares.push(share.clone());
        Ok(())
    }

    async fn find_by_id(&self, id: Uuid) -> Result<Option<ShareLink>, DomainError> {
        let shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        Ok(shares.iter().find(|s| s.id == id).cloned())
    }

    async fn find_by_slug(&self, slug: &Slug) -> Result<Option<ShareLink>, DomainError> {
        let shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        Ok(shares
            .iter()
            .find(|s| s.slug.as_str().eq_ignore_ascii_case(slug.as_str()))
            .cloned())
    }

    async fn list(
        &self,
        path_filter: Option<&RelativeFilePath>,
        active_only: bool,
        now: DateTime<Utc>,
    ) -> Result<Vec<ShareLink>, DomainError> {
        let shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        Ok(shares
            .iter()
            .filter(|s| match path_filter {
                Some(p) => s.relative_path == *p,
                None => true,
            })
            .filter(|s| !active_only || s.is_active(now))
            .cloned()
            .collect())
    }

    async fn count_active_by_paths(
        &self,
        paths: &[String],
        now: DateTime<Utc>,
    ) -> Result<HashMap<String, u64>, DomainError> {
        let set: HashSet<&str> = paths.iter().map(String::as_str).collect();
        let shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let mut counts = HashMap::new();
        for share in shares.iter() {
            if share.is_active(now) && set.contains(share.relative_path.as_str()) {
                *counts
                    .entry(share.relative_path.as_str().to_string())
                    .or_insert(0) += 1;
            }
        }
        Ok(counts)
    }

    async fn update(&self, id: Uuid, patch: SharePatch) -> Result<ShareLink, DomainError> {
        let mut shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let share = shares
            .iter_mut()
            .find(|s| s.id == id)
            .ok_or(DomainError::NotFound)?;

        if let Some(revoked) = patch.is_revoked {
            share.is_revoked = revoked;
        }
        if patch.clear_expiration {
            share.expires_at = None;
        } else if let Some(exp) = patch.expires_at {
            share.expires_at = Some(exp);
        }
        if patch.clear_max_downloads {
            share.max_downloads = None;
        } else if let Some(max) = patch.max_downloads {
            share.max_downloads = Some(max);
        }
        if patch.clear_password {
            share.password_hash = None;
        } else if let Some(hash) = patch.new_password_hash {
            share.password_hash = Some(hash);
        }
        Ok(share.clone())
    }

    async fn delete(&self, id: Uuid) -> Result<(), DomainError> {
        let mut shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let before = shares.len();
        shares.retain(|s| s.id != id);
        if shares.len() == before {
            return Err(DomainError::NotFound);
        }
        Ok(())
    }

    async fn increment_download_atomic(
        &self,
        id: Uuid,
        now: DateTime<Utc>,
    ) -> Result<bool, DomainError> {
        let mut shares = self
            .shares
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let Some(share) = shares.iter_mut().find(|s| s.id == id) else {
            return Ok(false);
        };
        if !share.is_active(now) {
            return Ok(false);
        }
        share.download_count += 1;
        share.last_accessed_at = Some(now);
        Ok(true)
    }
}

#[derive(Default)]
pub struct FakeSessionRepository {
    pub sessions: Mutex<Vec<AdminSession>>,
}

#[async_trait]
impl SessionRepository for FakeSessionRepository {
    async fn create(&self, session: &AdminSession) -> Result<(), DomainError> {
        let mut sessions = self
            .sessions
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        sessions.push(session.clone());
        Ok(())
    }

    async fn find_by_token_hash(
        &self,
        token_hash: &str,
    ) -> Result<Option<AdminSession>, DomainError> {
        let sessions = self
            .sessions
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        Ok(sessions
            .iter()
            .find(|s| s.refresh_token_hash == token_hash)
            .cloned())
    }

    async fn rotate_token(
        &self,
        id: Uuid,
        new_token_hash: &str,
        new_expires_at: DateTime<Utc>,
    ) -> Result<(), DomainError> {
        let mut sessions = self
            .sessions
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let session = sessions
            .iter_mut()
            .find(|s| s.id == id)
            .ok_or(DomainError::Unauthorized)?;
        session.refresh_token_hash = new_token_hash.to_string();
        session.expires_at = new_expires_at;
        Ok(())
    }

    async fn delete_by_id(&self, id: Uuid) -> Result<(), DomainError> {
        let mut sessions = self
            .sessions
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        sessions.retain(|s| s.id != id);
        Ok(())
    }
}

pub type FakeFsEntry = (String, FileMetadata, Option<Vec<FileEntry>>);

#[derive(Default)]
pub struct FakeFileSystem {
    pub entries: Mutex<Vec<FakeFsEntry>>,
}

#[async_trait]
impl FileSystemPort for FakeFileSystem {
    async fn list_directory(
        &self,
        path: &RelativeFilePath,
        _show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError> {
        let entries = self
            .entries
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        for (p, meta, children) in entries.iter() {
            if p == path.as_str() {
                if !meta.is_directory {
                    return Err(DomainError::NotFound);
                }
                return Ok(children.clone().unwrap_or_default());
            }
        }
        Err(DomainError::NotFound)
    }

    async fn search(
        &self,
        _base_path: &RelativeFilePath,
        query: &str,
        _show_hidden: bool,
    ) -> Result<Vec<FileEntry>, DomainError> {
        let q = query.to_lowercase();
        let entries = self
            .entries
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        let mut out = Vec::new();
        for (_p, _meta, children) in entries.iter() {
            if let Some(list) = children {
                for item in list {
                    if item.name.to_lowercase().contains(&q) {
                        out.push(item.clone());
                    }
                }
            }
        }
        Ok(out)
    }

    async fn get_metadata(&self, path: &RelativeFilePath) -> Result<FileMetadata, DomainError> {
        let entries = self
            .entries
            .lock()
            .map_err(|e| DomainError::Internal(e.to_string()))?;
        entries
            .iter()
            .find(|(p, _, _)| p == path.as_str())
            .map(|(_, m, _)| m.clone())
            .ok_or(DomainError::NotFound)
    }
}

pub struct FakePasswordHasher;

impl PasswordHasher for FakePasswordHasher {
    fn hash_password(&self, password: &str) -> Result<String, DomainError> {
        Ok(format!("fakehash:{password}"))
    }

    fn verify_password(&self, password: &str, hash: &str) -> Result<bool, DomainError> {
        Ok(hash == format!("fakehash:{password}"))
    }
}

#[derive(Default)]
pub struct FakeTokenService {
    pub counter: Mutex<u64>,
}

impl TokenService for FakeTokenService {
    fn issue_access_token(&self, session_id: Uuid, username: &str) -> Result<String, DomainError> {
        Ok(format!("access:{session_id}:{username}"))
    }

    fn verify_access_token(&self, token: &str) -> Result<(Uuid, String), DomainError> {
        let parts: Vec<&str> = token.split(':').collect();
        if parts.len() == 3 && parts[0] == "access" {
            let id = Uuid::parse_str(parts[1]).map_err(|_| DomainError::Unauthorized)?;
            return Ok((id, parts[2].to_string()));
        }
        Err(DomainError::Unauthorized)
    }

    fn generate_refresh_token(&self) -> String {
        let mut c = self.counter.lock().unwrap_or_else(|e| e.into_inner());
        *c += 1;
        format!("rt_fake_{}", *c)
    }

    fn hash_refresh_token(&self, token: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(token.as_bytes());
        hex::encode(hasher.finalize())
    }

    fn sign_unlock_cookie(&self, slug: &str, _ttl_seconds: i64) -> Result<String, DomainError> {
        Ok(format!("cookie:{slug}"))
    }

    fn verify_unlock_cookie(&self, cookie_value: &str, expected_slug: &str) -> bool {
        cookie_value == format!("cookie:{expected_slug}")
    }
}

pub struct FakeThumbnailer;

#[async_trait]
impl ThumbnailPort for FakeThumbnailer {
    async fn get_or_generate_webp(
        &self,
        path: &RelativeFilePath,
    ) -> Result<ThumbnailPayload, DomainError> {
        Ok(ThumbnailPayload {
            bytes: format!("webp:{}", path.as_str()).into_bytes(),
            etag: format!("\"etag-{}\"", path.as_str()),
        })
    }
}

// Re-export helpers to avoid unused warnings in test module
#[allow(dead_code)]
fn _keep_types_used(
    _: Arc<FakeShareRepository>,
    _: DirectoryListing,
    _: SortBy,
    _: SortOrder,
    _: Duration,
) {
}
