use super::db::{fmt_dt, parse_dt};
use crate::domain::entities::ShareLink;
use crate::domain::errors::DomainError;
use crate::domain::ports::{SharePatch, ShareRepository};
use crate::domain::value_objects::{RelativeFilePath, Slug};
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use sqlx::{FromRow, SqlitePool};
use std::collections::{HashMap, HashSet};
use uuid::Uuid;

#[derive(FromRow)]
struct ShareRow {
    id: String,
    slug: String,
    relative_path: String,
    target_name: String,
    is_directory: i64,
    password_hash: Option<String>,
    expires_at: Option<String>,
    max_downloads: Option<i64>,
    download_count: i64,
    is_revoked: i64,
    created_at: String,
    last_accessed_at: Option<String>,
}

impl TryFrom<ShareRow> for ShareLink {
    type Error = DomainError;

    fn try_from(r: ShareRow) -> Result<Self, Self::Error> {
        let id = Uuid::parse_str(&r.id)
            .map_err(|e| DomainError::Internal(format!("Invalid share UUID: {e}")))?;
        let created_at = parse_dt(&r.created_at)?;
        let expires_at = r.expires_at.as_deref().map(parse_dt).transpose()?;
        let last_accessed_at = r.last_accessed_at.as_deref().map(parse_dt).transpose()?;

        Ok(ShareLink {
            id,
            slug: Slug::from_trusted(r.slug),
            relative_path: RelativeFilePath::from_trusted(r.relative_path),
            target_name: r.target_name,
            is_directory: r.is_directory != 0,
            password_hash: r.password_hash,
            expires_at,
            max_downloads: r.max_downloads.and_then(|v| u64::try_from(v).ok()),
            download_count: u64::try_from(r.download_count).unwrap_or(0),
            is_revoked: r.is_revoked != 0,
            created_at,
            last_accessed_at,
        })
    }
}

#[derive(Clone)]
pub struct SqliteShareRepository {
    pool: SqlitePool,
}

impl SqliteShareRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ShareRepository for SqliteShareRepository {
    async fn create(&self, share: &ShareLink) -> Result<(), DomainError> {
        let res = sqlx::query(
            r#"
            INSERT INTO share_links (
                id, slug, relative_path, target_name, is_directory,
                password_hash, expires_at, max_downloads, download_count,
                is_revoked, created_at, last_accessed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
        )
        .bind(share.id.to_string())
        .bind(share.slug.as_str())
        .bind(share.relative_path.as_str())
        .bind(&share.target_name)
        .bind(i64::from(share.is_directory))
        .bind(&share.password_hash)
        .bind(share.expires_at.map(fmt_dt))
        .bind(share.max_downloads.and_then(|v| i64::try_from(v).ok()))
        .bind(i64::try_from(share.download_count).unwrap_or(0))
        .bind(i64::from(share.is_revoked))
        .bind(fmt_dt(share.created_at))
        .bind(share.last_accessed_at.map(fmt_dt))
        .execute(&self.pool)
        .await;

        match res {
            Ok(_) => Ok(()),
            Err(sqlx::Error::Database(db_err)) if db_err.is_unique_violation() => {
                Err(DomainError::SlugAlreadyExists)
            }
            Err(e) => Err(DomainError::Internal(format!("DB insert share error: {e}"))),
        }
    }

    async fn find_by_id(&self, id: Uuid) -> Result<Option<ShareLink>, DomainError> {
        let row = sqlx::query_as::<_, ShareRow>(
            r#"
            SELECT id, slug, relative_path, target_name, is_directory,
                   password_hash, expires_at, max_downloads, download_count,
                   is_revoked, created_at, last_accessed_at
            FROM share_links
            WHERE id = ?
            "#,
        )
        .bind(id.to_string())
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB find_by_id error: {e}")))?;

        row.map(ShareLink::try_from).transpose()
    }

    async fn find_by_slug(&self, slug: &Slug) -> Result<Option<ShareLink>, DomainError> {
        let row = sqlx::query_as::<_, ShareRow>(
            r#"
            SELECT id, slug, relative_path, target_name, is_directory,
                   password_hash, expires_at, max_downloads, download_count,
                   is_revoked, created_at, last_accessed_at
            FROM share_links
            WHERE slug = ?
            "#,
        )
        .bind(slug.as_str())
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB find_by_slug error: {e}")))?;

        row.map(ShareLink::try_from).transpose()
    }

    async fn list(
        &self,
        path_filter: Option<&RelativeFilePath>,
        active_only: bool,
        now: DateTime<Utc>,
    ) -> Result<Vec<ShareLink>, DomainError> {
        let rows = match path_filter {
            Some(path) => sqlx::query_as::<_, ShareRow>(
                r#"
                SELECT id, slug, relative_path, target_name, is_directory,
                       password_hash, expires_at, max_downloads, download_count,
                       is_revoked, created_at, last_accessed_at
                FROM share_links
                WHERE relative_path = ?
                ORDER BY created_at DESC
                "#,
            )
            .bind(path.as_str())
            .fetch_all(&self.pool)
            .await
            .map_err(|e| DomainError::Internal(format!("DB list shares error: {e}")))?,
            None => sqlx::query_as::<_, ShareRow>(
                r#"
                SELECT id, slug, relative_path, target_name, is_directory,
                       password_hash, expires_at, max_downloads, download_count,
                       is_revoked, created_at, last_accessed_at
                FROM share_links
                ORDER BY created_at DESC
                "#,
            )
            .fetch_all(&self.pool)
            .await
            .map_err(|e| DomainError::Internal(format!("DB list shares error: {e}")))?,
        };

        let mut shares = Vec::with_capacity(rows.len());
        for row in rows {
            let share = ShareLink::try_from(row)?;
            if !active_only || share.is_active(now) {
                shares.push(share);
            }
        }
        Ok(shares)
    }

    async fn count_active_by_paths(
        &self,
        paths: &[String],
        now: DateTime<Utc>,
    ) -> Result<HashMap<String, u64>, DomainError> {
        if paths.is_empty() {
            return Ok(HashMap::new());
        }
        let target_set: HashSet<&str> = paths.iter().map(String::as_str).collect();
        let rows = sqlx::query_as::<_, ShareRow>(
            r#"
            SELECT id, slug, relative_path, target_name, is_directory,
                   password_hash, expires_at, max_downloads, download_count,
                   is_revoked, created_at, last_accessed_at
            FROM share_links
            WHERE is_revoked = 0
            "#,
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB count_active_by_paths error: {e}")))?;

        let mut map = HashMap::new();
        for row in rows {
            if !target_set.contains(row.relative_path.as_str()) {
                continue;
            }
            let share = ShareLink::try_from(row)?;
            if share.is_active(now) {
                *map.entry(share.relative_path.as_str().to_string())
                    .or_insert(0) += 1;
            }
        }
        Ok(map)
    }

    async fn update(&self, id: Uuid, patch: SharePatch) -> Result<ShareLink, DomainError> {
        let mut current = self.find_by_id(id).await?.ok_or(DomainError::NotFound)?;

        if let Some(revoked) = patch.is_revoked {
            current.is_revoked = revoked;
        }
        if patch.clear_expiration {
            current.expires_at = None;
        } else if let Some(exp) = patch.expires_at {
            current.expires_at = Some(exp);
        }
        if patch.clear_max_downloads {
            current.max_downloads = None;
        } else if let Some(max) = patch.max_downloads {
            current.max_downloads = Some(max);
        }
        if patch.clear_password {
            current.password_hash = None;
        } else if let Some(hash) = patch.new_password_hash {
            current.password_hash = Some(hash);
        }

        sqlx::query(
            r#"
            UPDATE share_links
            SET is_revoked = ?,
                expires_at = ?,
                max_downloads = ?,
                password_hash = ?
            WHERE id = ?
            "#,
        )
        .bind(i64::from(current.is_revoked))
        .bind(current.expires_at.map(fmt_dt))
        .bind(current.max_downloads.and_then(|v| i64::try_from(v).ok()))
        .bind(&current.password_hash)
        .bind(id.to_string())
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB update share error: {e}")))?;

        Ok(current)
    }

    async fn delete(&self, id: Uuid) -> Result<(), DomainError> {
        let res = sqlx::query("DELETE FROM share_links WHERE id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::Internal(format!("DB delete share error: {e}")))?;

        if res.rows_affected() == 0 {
            return Err(DomainError::NotFound);
        }
        Ok(())
    }

    async fn increment_download_atomic(
        &self,
        id: Uuid,
        now: DateTime<Utc>,
    ) -> Result<bool, DomainError> {
        let now_str = fmt_dt(now);
        // Use julianday() / datetime() or RFC3339 UTC comparison because fmt_dt produces RFC3339 Z
        // To avoid any ISO format drift with offsets, compare via unixepoch() / julianday() in SQLite.
        let res = sqlx::query(
            r#"
            UPDATE share_links
            SET download_count = download_count + 1,
                last_accessed_at = ?
            WHERE id = ?
              AND is_revoked = 0
              AND (expires_at IS NULL OR julianday(expires_at) > julianday(?))
              AND (max_downloads IS NULL OR download_count < max_downloads)
            "#,
        )
        .bind(&now_str)
        .bind(id.to_string())
        .bind(&now_str)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB increment_download error: {e}")))?;

        Ok(res.rows_affected() == 1)
    }
}
