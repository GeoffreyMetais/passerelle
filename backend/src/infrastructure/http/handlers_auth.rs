use crate::application::auth_use_cases::{AuthTokensResult, LoginCommand};
use crate::domain::errors::DomainError;
use crate::infrastructure::http::error::ApiError;
use crate::infrastructure::http::state::{AdminAuth, AppState, extract_client_ip};
use axum::Json;
use axum::extract::State;
use axum::http::{Extensions, HeaderMap, StatusCode};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HealthResponse {
    pub status: &'static str,
    pub version: &'static str,
}

pub async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "ok",
        version: env!("CARGO_PKG_VERSION"),
    })
}

pub async fn login(
    State(state): State<AppState>,
    headers: HeaderMap,
    extensions: Extensions,
    Json(payload): Json<LoginCommand>,
) -> Result<Json<AuthTokensResult>, ApiError> {
    let ip = extract_client_ip(&headers, &extensions);
    if !state.login_limiter.check_and_record(ip) {
        return Err(ApiError(DomainError::RateLimited));
    }

    let tokens = state.auth.login(payload).await?;
    Ok(Json(tokens))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshRequest {
    pub refresh_token: String,
}

pub async fn refresh(
    State(state): State<AppState>,
    Json(payload): Json<RefreshRequest>,
) -> Result<Json<AuthTokensResult>, ApiError> {
    let tokens = state.auth.refresh(&payload.refresh_token).await?;
    Ok(Json(tokens))
}

pub async fn logout(
    State(state): State<AppState>,
    auth: AdminAuth,
) -> Result<StatusCode, ApiError> {
    state.auth.logout(auth.session_id).await?;
    Ok(StatusCode::NO_CONTENT)
}
