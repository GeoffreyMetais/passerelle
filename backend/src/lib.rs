//! Passerelle Backend — Pure Clean Architecture (Domain ← Application ← Infrastructure).

pub mod application;
pub mod domain;
pub mod infrastructure;

use crate::application::auth_use_cases::{ACCESS_TOKEN_TTL_SECONDS, AuthUseCases};
use crate::application::file_use_cases::FileUseCases;
use crate::application::share_use_cases::ShareUseCases;
use crate::domain::errors::DomainError;
use crate::domain::ports::PasswordHasher;
use crate::infrastructure::config::Config;
use crate::infrastructure::fs::{LocalFileSystem, PathGuard, WebpThumbnailer};
use crate::infrastructure::http::build_router;
use crate::infrastructure::http::rate_limit::RateLimiter;
use crate::infrastructure::http::state::AppState;
use crate::infrastructure::persistence::{
    SqliteSessionRepository, SqliteShareRepository, init_sqlite_pool,
};
use crate::infrastructure::security::{Argon2Hasher, JwtTokenService};
use axum::Router;
use std::sync::Arc;
use std::time::Duration;

pub async fn build_app(config: &Config) -> Result<Router, DomainError> {
    build_app_with_limits(config, 15, 10, Duration::from_secs(60)).await
}

pub async fn build_app_with_limits(
    config: &Config,
    login_max_requests: usize,
    unlock_max_requests: usize,
    rate_window: Duration,
) -> Result<Router, DomainError> {
    let pool = init_sqlite_pool(&config.state_dir).await?;
    let path_guard = PathGuard::new(&config.data_dir)?;
    let local_fs = Arc::new(LocalFileSystem::new(path_guard.clone()));
    let thumbnailer = Arc::new(WebpThumbnailer::new(path_guard.clone(), &config.state_dir).await?);

    let share_repo = Arc::new(SqliteShareRepository::new(pool.clone()));
    let session_repo = Arc::new(SqliteSessionRepository::new(pool));
    let hasher = Arc::new(Argon2Hasher);
    let token_service = Arc::new(JwtTokenService::new(
        config.jwt_secret.as_bytes().to_vec(),
        ACCESS_TOKEN_TTL_SECONDS,
    ));

    let admin_password_hash = hasher.hash_password(&config.admin_password)?;

    let auth = AuthUseCases::new(
        config.admin_username.clone(),
        admin_password_hash,
        config.public_base_url.clone(),
        session_repo,
        hasher.clone(),
        token_service.clone(),
    );
    let files = FileUseCases::new(local_fs.clone(), share_repo.clone(), thumbnailer);
    let shares = ShareUseCases::new(
        share_repo,
        local_fs,
        hasher,
        token_service,
        config.public_base_url.clone(),
        config.max_zip_size_bytes,
    );

    let state = AppState {
        auth,
        files,
        shares,
        path_guard,
        public_base_url: config.public_base_url.clone(),
        login_limiter: Arc::new(RateLimiter::new(login_max_requests, rate_window)),
        unlock_limiter: Arc::new(RateLimiter::new(unlock_max_requests, rate_window)),
    };

    Ok(build_router(state, &config.allowed_origins))
}
