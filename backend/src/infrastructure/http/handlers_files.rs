use crate::domain::entities::{DirectoryListing, FileEntry, SortBy, SortOrder};
use crate::domain::errors::DomainError;
use crate::infrastructure::http::error::ApiError;
use crate::infrastructure::http::state::{AdminAuth, AppState};
use axum::Json;
use axum::body::Body;
use axum::extract::{Query, Request, State};
use axum::http::{HeaderMap, HeaderValue, StatusCode, header};
use axum::response::{IntoResponse, Response};
use serde::{Deserialize, Serialize};
use tower::ServiceExt;
use tower_http::services::ServeFile;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListFilesQuery {
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub sort_by: SortBy,
    #[serde(default)]
    pub sort_order: SortOrder,
    #[serde(default)]
    pub show_hidden: bool,
}

pub async fn list_files(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Query(query): Query<ListFilesQuery>,
) -> Result<Json<DirectoryListing>, ApiError> {
    let listing = state
        .files
        .list_directory(
            &query.path,
            query.sort_by,
            query.sort_order,
            query.show_hidden,
        )
        .await?;
    Ok(Json(listing))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchFilesQuery {
    pub q: String,
    #[serde(default)]
    pub path: String,
    #[serde(default)]
    pub show_hidden: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchFilesResponse {
    pub query: String,
    pub results: Vec<FileEntry>,
}

pub async fn search_files(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Query(query): Query<SearchFilesQuery>,
) -> Result<Json<SearchFilesResponse>, ApiError> {
    let results = state
        .files
        .search_files(&query.q, &query.path, query.show_hidden)
        .await?;
    Ok(Json(SearchFilesResponse {
        query: query.q,
        results,
    }))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PathQuery {
    pub path: String,
}

pub async fn get_thumbnail(
    State(state): State<AppState>,
    _auth: AdminAuth,
    headers: HeaderMap,
    Query(query): Query<PathQuery>,
) -> Result<Response, ApiError> {
    let payload = state.files.get_thumbnail(&query.path).await?;

    if let Some(if_none_match) = headers
        .get(header::IF_NONE_MATCH)
        .and_then(|v| v.to_str().ok())
    {
        if if_none_match.trim() == payload.etag {
            return Ok((
                StatusCode::NOT_MODIFIED,
                [
                    (header::CACHE_CONTROL, "private, max-age=86400"),
                    (header::ETAG, payload.etag.as_str()),
                ],
            )
                .into_response());
        }
    }

    let mut resp = Response::new(Body::from(payload.bytes));
    *resp.status_mut() = StatusCode::OK;
    resp.headers_mut()
        .insert(header::CONTENT_TYPE, HeaderValue::from_static("image/webp"));
    resp.headers_mut().insert(
        header::CACHE_CONTROL,
        HeaderValue::from_static("private, max-age=86400"),
    );
    if let Ok(etag_val) = HeaderValue::from_str(&payload.etag) {
        resp.headers_mut().insert(header::ETAG, etag_val);
    }
    Ok(resp)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminDownloadQuery {
    pub path: String,
    #[serde(default)]
    pub inline: bool,
}

pub async fn download_admin_file(
    State(state): State<AppState>,
    _auth: AdminAuth,
    Query(query): Query<AdminDownloadQuery>,
    req: Request,
) -> Result<Response, ApiError> {
    let meta = state.files.resolve_download(&query.path).await?;
    let abs_path = state.path_guard.resolve(&meta.relative_path)?;

    let mut response = ServeFile::new(abs_path)
        .oneshot(req)
        .await
        .map_err(|e| ApiError(DomainError::Internal(format!("ServeFile error: {e}"))))?;

    let disposition_kind = if query.inline { "inline" } else { "attachment" };
    let encoded_name = urlencoding::encode(&meta.name);
    let disp = format!(
        "{disposition_kind}; filename=\"{}\"; filename*=UTF-8''{}",
        sanitize_filename(&meta.name),
        encoded_name
    );
    if let Ok(val) = HeaderValue::from_str(&disp) {
        response
            .headers_mut()
            .insert(header::CONTENT_DISPOSITION, val);
    }

    Ok(response.map(Body::new))
}

pub fn sanitize_filename(name: &str) -> String {
    name.chars()
        .map(|c| {
            if c == '"' || c == '\\' || c.is_control() {
                '_'
            } else {
                c
            }
        })
        .collect()
}
