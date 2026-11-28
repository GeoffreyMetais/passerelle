use super::db::{fmt_dt, parse_dt};
use crate::domain::entities::AdminSession;
use crate::domain::errors::DomainError;
use crate::domain::ports::SessionRepository;
use async_trait::async_trait;
use chrono::{DateTime, Utc};
use sqlx::{FromRow, SqlitePool};
use uuid::Uuid;

#[derive(FromRow)]
struct SessionRow {
    id: String,
    refresh_token_hash: String,
    device_name: String,
    expires_at: String,
    created_at: String,
}

impl TryFrom<SessionRow> for AdminSession {
    type Error = DomainError;

    fn try_from(r: SessionRow) -> Result<Self, Self::Error> {
        let id = Uuid::parse_str(&r.id)
            .map_err(|e| DomainError::Internal(format!("Invalid session UUID: {e}")))?;
        Ok(AdminSession {
            id,
            refresh_token_hash: r.refresh_token_hash,
            device_name: r.device_name,
            expires_at: parse_dt(&r.expires_at)?,
            created_at: parse_dt(&r.created_at)?,
        })
    }
}

#[derive(Clone)]
pub struct SqliteSessionRepository {
    pool: SqlitePool,
}

impl SqliteSessionRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl SessionRepository for SqliteSessionRepository {
    async fn create(&self, session: &AdminSession) -> Result<(), DomainError> {
        sqlx::query(
            r#"
            INSERT INTO api_sessions (id, refresh_token_hash, device_name, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?)
            "#,
        )
        .bind(session.id.to_string())
        .bind(&session.refresh_token_hash)
        .bind(&session.device_name)
        .bind(fmt_dt(session.expires_at))
        .bind(fmt_dt(session.created_at))
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB insert session error: {e}")))?;

        Ok(())
    }

    async fn find_by_token_hash(
        &self,
        token_hash: &str,
    ) -> Result<Option<AdminSession>, DomainError> {
        let row = sqlx::query_as::<_, SessionRow>(
            r#"
            SELECT id, refresh_token_hash, device_name, expires_at, created_at
            FROM api_sessions
            WHERE refresh_token_hash = ?
            "#,
        )
        .bind(token_hash)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB find session error: {e}")))?;

        row.map(AdminSession::try_from).transpose()
    }

    async fn rotate_token(
        &self,
        id: Uuid,
        new_token_hash: &str,
        new_expires_at: DateTime<Utc>,
    ) -> Result<(), DomainError> {
        let res = sqlx::query(
            r#"
            UPDATE api_sessions
            SET refresh_token_hash = ?, expires_at = ?
            WHERE id = ?
            "#,
        )
        .bind(new_token_hash)
        .bind(fmt_dt(new_expires_at))
        .bind(id.to_string())
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::Internal(format!("DB rotate session error: {e}")))?;

        if res.rows_affected() == 0 {
            return Err(DomainError::Unauthorized);
        }
        Ok(())
    }

    async fn delete_by_id(&self, id: Uuid) -> Result<(), DomainError> {
        sqlx::query("DELETE FROM api_sessions WHERE id = ?")
            .bind(id.to_string())
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::Internal(format!("DB delete session error: {e}")))?;
        Ok(())
    }
}
