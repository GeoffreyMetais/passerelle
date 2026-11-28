use crate::domain::entities::AdminSession;
use crate::domain::errors::DomainError;
use crate::domain::ports::{PasswordHasher, SessionRepository, TokenService};
use chrono::{Duration, Utc};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

pub const ACCESS_TOKEN_TTL_SECONDS: i64 = 900;
pub const REFRESH_TOKEN_TTL_DAYS: i64 = 30;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LoginCommand {
    pub username: String,
    pub password: String,
    pub device_name: Option<String>,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AuthTokensResult {
    pub access_token: String,
    pub refresh_token: String,
    pub expires_in_seconds: i64,
    pub public_base_url: String,
}

#[derive(Clone)]
pub struct AuthUseCases {
    admin_username: String,
    admin_password_hash: String,
    public_base_url: String,
    session_repo: Arc<dyn SessionRepository>,
    hasher: Arc<dyn PasswordHasher>,
    token_service: Arc<dyn TokenService>,
}

impl AuthUseCases {
    pub fn new(
        admin_username: String,
        admin_password_hash: String,
        public_base_url: String,
        session_repo: Arc<dyn SessionRepository>,
        hasher: Arc<dyn PasswordHasher>,
        token_service: Arc<dyn TokenService>,
    ) -> Self {
        Self {
            admin_username,
            admin_password_hash,
            public_base_url,
            session_repo,
            hasher,
            token_service,
        }
    }

    pub async fn login(&self, cmd: LoginCommand) -> Result<AuthTokensResult, DomainError> {
        if cmd.username != self.admin_username {
            return Err(DomainError::InvalidCredentials);
        }
        let valid = self
            .hasher
            .verify_password(&cmd.password, &self.admin_password_hash)?;
        if !valid {
            return Err(DomainError::InvalidCredentials);
        }

        let now = Utc::now();
        let session_id = Uuid::now_v7();
        let refresh_token = self.token_service.generate_refresh_token();
        let refresh_hash = self.token_service.hash_refresh_token(&refresh_token);
        let device_name = cmd
            .device_name
            .filter(|s| !s.trim().is_empty())
            .unwrap_or_else(|| "Unknown device".to_string());

        let session = AdminSession {
            id: session_id,
            refresh_token_hash: refresh_hash,
            device_name,
            expires_at: now + Duration::days(REFRESH_TOKEN_TTL_DAYS),
            created_at: now,
        };
        self.session_repo.create(&session).await?;

        let access_token = self
            .token_service
            .issue_access_token(session_id, &self.admin_username)?;

        Ok(AuthTokensResult {
            access_token,
            refresh_token,
            expires_in_seconds: ACCESS_TOKEN_TTL_SECONDS,
            public_base_url: self.public_base_url.clone(),
        })
    }

    pub async fn refresh(&self, refresh_token: &str) -> Result<AuthTokensResult, DomainError> {
        if refresh_token.trim().is_empty() {
            return Err(DomainError::Unauthorized);
        }
        let token_hash = self.token_service.hash_refresh_token(refresh_token);
        let session = self
            .session_repo
            .find_by_token_hash(&token_hash)
            .await?
            .ok_or(DomainError::Unauthorized)?;

        let now = Utc::now();
        if session.is_expired(now) {
            self.session_repo.delete_by_id(session.id).await?;
            return Err(DomainError::TokenExpired);
        }

        let new_refresh = self.token_service.generate_refresh_token();
        let new_hash = self.token_service.hash_refresh_token(&new_refresh);
        let new_expires_at = now + Duration::days(REFRESH_TOKEN_TTL_DAYS);

        self.session_repo
            .rotate_token(session.id, &new_hash, new_expires_at)
            .await?;

        let access_token = self
            .token_service
            .issue_access_token(session.id, &self.admin_username)?;

        Ok(AuthTokensResult {
            access_token,
            refresh_token: new_refresh,
            expires_in_seconds: ACCESS_TOKEN_TTL_SECONDS,
            public_base_url: self.public_base_url.clone(),
        })
    }

    pub async fn logout(&self, session_id: Uuid) -> Result<(), DomainError> {
        self.session_repo.delete_by_id(session_id).await
    }

    pub fn verify_access_token(&self, token: &str) -> Result<(Uuid, String), DomainError> {
        self.token_service.verify_access_token(token)
    }
}

#[cfg(test)]
mod tests;
