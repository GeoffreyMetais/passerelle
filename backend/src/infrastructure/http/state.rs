use crate::application::auth_use_cases::AuthUseCases;
use crate::application::file_use_cases::FileUseCases;
use crate::application::share_use_cases::ShareUseCases;
use crate::domain::errors::DomainError;
use crate::infrastructure::fs::PathGuard;
use crate::infrastructure::http::error::ApiError;
use crate::infrastructure::http::rate_limit::RateLimiter;
use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum::http::{HeaderMap, header};
use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;
use uuid::Uuid;

#[derive(Clone)]
pub struct AppState {
    pub auth: AuthUseCases,
    pub files: FileUseCases,
    pub shares: ShareUseCases,
    pub path_guard: PathGuard,
    pub public_base_url: String,
    pub login_limiter: Arc<RateLimiter>,
    pub unlock_limiter: Arc<RateLimiter>,
}

/// Extracteur d'authentification Bearer JWT pour les routes `/api/v1/*`.
#[derive(Debug, Clone)]
pub struct AdminAuth {
    pub session_id: Uuid,
    pub username: String,
}

impl FromRequestParts<AppState> for AdminAuth {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let auth_header = parts
            .headers
            .get(header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .ok_or(ApiError(DomainError::Unauthorized))?;

        let token = auth_header
            .strip_prefix("Bearer ")
            .map(str::trim)
            .filter(|t| !t.is_empty())
            .ok_or(ApiError(DomainError::Unauthorized))?;

        let (session_id, username) = state.auth.verify_access_token(token).map_err(ApiError)?;

        Ok(Self {
            session_id,
            username,
        })
    }
}

/// Extrait l'IP client depuis `X-Forwarded-For` / `X-Real-IP` ou `Extensions` (`ConnectInfo<SocketAddr>`, absent en tests `oneshot`).
pub fn extract_client_ip(
    headers: &HeaderMap,
    extensions: &axum::http::Extensions,
) -> Option<IpAddr> {
    if let Some(xff) = headers.get("x-forwarded-for").and_then(|v| v.to_str().ok()) {
        if let Some(first) = xff.split(',').next().map(str::trim) {
            if let Ok(ip) = first.parse::<IpAddr>() {
                return Some(ip);
            }
        }
    }
    if let Some(real_ip) = headers.get("x-real-ip").and_then(|v| v.to_str().ok()) {
        if let Ok(ip) = real_ip.trim().parse::<IpAddr>() {
            return Some(ip);
        }
    }
    extensions
        .get::<axum::extract::ConnectInfo<SocketAddr>>()
        .map(|c| c.0.ip())
}
