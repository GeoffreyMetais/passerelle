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
mod tests {
    use super::*;
    use crate::application::fakes::{FakePasswordHasher, FakeSessionRepository, FakeTokenService};

    fn make_auth() -> (AuthUseCases, Arc<FakeSessionRepository>) {
        let repo = Arc::new(FakeSessionRepository::default());
        let hasher = Arc::new(FakePasswordHasher);
        let token_svc = Arc::new(FakeTokenService::default());
        let hash = hasher.hash_password("secret-pass").unwrap();
        let uc = AuthUseCases::new(
            "admin".to_string(),
            hash,
            "https://s.domaine.fr".to_string(),
            repo.clone(),
            hasher,
            token_svc,
        );
        (uc, repo)
    }

    #[tokio::test]
    async fn login_succeeds_and_creates_session() {
        let (uc, repo) = make_auth();
        let res = uc
            .login(LoginCommand {
                username: "admin".to_string(),
                password: "secret-pass".to_string(),
                device_name: Some("PWA Web".to_string()),
            })
            .await
            .expect("login succeeds");

        assert_eq!(res.expires_in_seconds, 900);
        assert_eq!(res.public_base_url, "https://s.domaine.fr");
        assert!(res.refresh_token.starts_with("rt_"));
        assert_eq!(repo.sessions.lock().unwrap().len(), 1);
    }

    #[tokio::test]
    async fn login_rejects_invalid_credentials() {
        let (uc, _repo) = make_auth();
        let err = uc
            .login(LoginCommand {
                username: "admin".to_string(),
                password: "wrong".to_string(),
                device_name: None,
            })
            .await
            .unwrap_err();
        assert_eq!(err, DomainError::InvalidCredentials);

        let err2 = uc
            .login(LoginCommand {
                username: "other".to_string(),
                password: "secret-pass".to_string(),
                device_name: None,
            })
            .await
            .unwrap_err();
        assert_eq!(err2, DomainError::InvalidCredentials);
    }

    #[tokio::test]
    async fn refresh_rotates_token_and_invalidates_old_one() {
        let (uc, _repo) = make_auth();
        let first = uc
            .login(LoginCommand {
                username: "admin".to_string(),
                password: "secret-pass".to_string(),
                device_name: None,
            })
            .await
            .unwrap();

        let second = uc.refresh(&first.refresh_token).await.expect("refresh ok");
        assert_ne!(second.refresh_token, first.refresh_token);

        // Old refresh token can no longer be reused
        let replay = uc.refresh(&first.refresh_token).await;
        assert_eq!(replay, Err(DomainError::Unauthorized));
    }

    #[tokio::test]
    async fn logout_deletes_session() {
        let (uc, repo) = make_auth();
        let tokens = uc
            .login(LoginCommand {
                username: "admin".to_string(),
                password: "secret-pass".to_string(),
                device_name: None,
            })
            .await
            .unwrap();

        let (session_id, _) = uc.verify_access_token(&tokens.access_token).unwrap();
        uc.logout(session_id).await.unwrap();
        assert!(repo.sessions.lock().unwrap().is_empty());
        assert_eq!(
            uc.refresh(&tokens.refresh_token).await,
            Err(DomainError::Unauthorized)
        );
    }
}
