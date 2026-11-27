use crate::domain::errors::DomainError;
use axum::Json;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde::Serialize;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ApiErrorBody {
    pub code: &'static str,
    pub message: String,
}

#[derive(Debug)]
pub struct ApiError(pub DomainError);

impl From<DomainError> for ApiError {
    fn from(err: DomainError) -> Self {
        Self(err)
    }
}

impl ApiError {
    pub fn status_and_code(&self) -> (StatusCode, &'static str) {
        match &self.0 {
            DomainError::InvalidCredentials => (StatusCode::UNAUTHORIZED, "INVALID_CREDENTIALS"),
            DomainError::Unauthorized => (StatusCode::UNAUTHORIZED, "UNAUTHORIZED"),
            DomainError::TokenExpired => (StatusCode::UNAUTHORIZED, "TOKEN_EXPIRED"),
            DomainError::ForbiddenPath => (StatusCode::FORBIDDEN, "FORBIDDEN_PATH"),
            DomainError::NotFound => (StatusCode::NOT_FOUND, "NOT_FOUND"),
            DomainError::SlugAlreadyExists => (StatusCode::CONFLICT, "SLUG_ALREADY_EXISTS"),
            DomainError::ReservedSlug => (StatusCode::BAD_REQUEST, "RESERVED_SLUG"),
            DomainError::InvalidSlug => (StatusCode::BAD_REQUEST, "RESERVED_SLUG"),
            DomainError::PayloadTooLarge => (StatusCode::PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE"),
            DomainError::ShareExpired => (StatusCode::GONE, "SHARE_EXPIRED"),
            DomainError::ShareRevoked => (StatusCode::GONE, "SHARE_REVOKED"),
            DomainError::QuotaExceeded => (StatusCode::GONE, "QUOTA_EXCEEDED"),
            DomainError::InvalidPassword => (StatusCode::UNAUTHORIZED, "INVALID_PASSWORD"),
            DomainError::ValidationError(_) => (StatusCode::BAD_REQUEST, "VALIDATION_ERROR"),
            DomainError::RateLimited => (StatusCode::TOO_MANY_REQUESTS, "RATE_LIMITED"),
            DomainError::Internal(_) => (StatusCode::INTERNAL_SERVER_ERROR, "INTERNAL_ERROR"),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, code) = self.status_and_code();
        let message = match &self.0 {
            DomainError::Internal(detail) => {
                tracing::error!(error = %detail, "Internal backend error");
                "Une erreur interne est survenue.".to_string()
            }
            other => other.to_string(),
        };
        (status, Json(ApiErrorBody { code, message })).into_response()
    }
}
