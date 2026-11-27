pub mod error;
pub mod handlers_auth;
pub mod handlers_files;
pub mod handlers_public;
pub mod handlers_shares;
pub mod rate_limit;
pub mod state;

use crate::infrastructure::http::state::AppState;
use axum::Router;
use axum::http::{HeaderName, HeaderValue, Method, header};
use axum::routing::{delete, get, patch, post};
use tower_http::cors::{AllowOrigin, CorsLayer};
use tower_http::trace::TraceLayer;

pub fn build_router(state: AppState, allowed_origins: &[String]) -> Router {
    let cors = build_cors_layer(allowed_origins);

    Router::new()
        .route("/health", get(handlers_auth::health))
        .route("/api/v1/auth/login", post(handlers_auth::login))
        .route("/api/v1/auth/refresh", post(handlers_auth::refresh))
        .route("/api/v1/auth/logout", delete(handlers_auth::logout))
        .route("/api/v1/files", get(handlers_files::list_files))
        .route("/api/v1/files/search", get(handlers_files::search_files))
        .route(
            "/api/v1/files/thumbnail",
            get(handlers_files::get_thumbnail),
        )
        .route(
            "/api/v1/files/download",
            get(handlers_files::download_admin_file),
        )
        .route(
            "/api/v1/shares",
            post(handlers_shares::create_share).get(handlers_shares::list_shares),
        )
        .route(
            "/api/v1/shares/{id}",
            patch(handlers_shares::update_share).delete(handlers_shares::delete_share),
        )
        .route("/api/v1/shares/{id}/qr", get(handlers_shares::get_share_qr))
        .route("/d/{slug}", get(handlers_public::download_public_share))
        .route("/{slug}", get(handlers_public::get_public_share))
        .route("/{slug}/unlock", post(handlers_public::unlock_public_share))
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

fn build_cors_layer(allowed_origins: &[String]) -> CorsLayer {
    let origins: Vec<HeaderValue> = allowed_origins
        .iter()
        .filter_map(|o| HeaderValue::from_str(o).ok())
        .collect();

    let allow_origin = if origins.is_empty() {
        AllowOrigin::list(Vec::<HeaderValue>::new())
    } else {
        AllowOrigin::list(origins)
    };

    CorsLayer::new()
        .allow_origin(allow_origin)
        .allow_methods([
            Method::GET,
            Method::POST,
            Method::PATCH,
            Method::DELETE,
            Method::OPTIONS,
        ])
        .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE])
        .expose_headers([
            header::CONTENT_DISPOSITION,
            header::ETAG,
            HeaderName::from_static("content-length"),
        ])
}
