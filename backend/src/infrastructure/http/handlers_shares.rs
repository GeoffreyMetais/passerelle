use crate::application::share_use_cases::{CreateShareCommand, ShareDto, UpdateShareCommand};
use crate::infrastructure::http::error::ApiError;
use crate::infrastructure::http::state::{AdminAuth, AppState};
use axum::Json;
use axum::extract::{Path, Query, State};
use axum::http::{StatusCode, header};
use axum::response::{IntoResponse, Response};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListSharesQuery {
    pub path: Option<String>,
    #[serde(default)]
    pub active_only: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ListSharesResponse {
    pub shares: Vec<ShareDto>,
}

pub async fn create_share(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Json(payload): Json<CreateShareCommand>,
) -> Result<(StatusCode, Json<ShareDto>), ApiError> {
    let dto = state.shares.create_share(payload).await?;
    Ok((StatusCode::CREATED, Json(dto)))
}

pub async fn list_shares(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Query(query): Query<ListSharesQuery>,
) -> Result<Json<ListSharesResponse>, ApiError> {
    let shares = state
        .shares
        .list_shares(query.path.as_deref(), query.active_only)
        .await?;
    Ok(Json(ListSharesResponse { shares }))
}

pub async fn update_share(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Path(id): Path<Uuid>,
    Json(payload): Json<UpdateShareCommand>,
) -> Result<Json<ShareDto>, ApiError> {
    let dto = state.shares.update_share(id, payload).await?;
    Ok(Json(dto))
}

pub async fn delete_share(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    state.shares.delete_share(id).await?;
    Ok(StatusCode::NO_CONTENT)
}

pub async fn get_share_qr(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Path(id): Path<Uuid>,
) -> Result<Response, ApiError> {
    let svg = state.shares.generate_qr_svg(id).await?;
    Ok((
        StatusCode::OK,
        [(header::CONTENT_TYPE, "image/svg+xml; charset=utf-8")],
        svg,
    )
        .into_response())
}
