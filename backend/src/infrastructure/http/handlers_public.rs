use crate::application::share_use_cases::PublicShareStatus;
use crate::domain::errors::DomainError;
use crate::infrastructure::fs::stream_directory_zip;
use crate::infrastructure::http::error::ApiError;
use crate::infrastructure::http::handlers_files::sanitize_filename;
use crate::infrastructure::http::state::{AppState, extract_client_ip};
use askama::Template;
use axum::Form;
use axum::body::Body;
use axum::extract::{Path, Query, Request, State};
use axum::http::{Extensions, HeaderMap, HeaderValue, StatusCode, header};
use axum::response::{Html, IntoResponse, Redirect, Response};
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use serde::Deserialize;
use tower::ServiceExt;
use tower_http::services::ServeFile;

const UNLOCK_COOKIE_PREFIX: &str = "passerelle_unlock_";

#[derive(Template)]
#[template(path = "share.html")]
struct SharePageTemplate {
    title: String,
    og_title: String,
    og_description: String,
    og_url: String,
    public_path_prefix: String,
    state: PublicViewState,
}

enum PublicViewState {
    Gone {
        heading: String,
        message: String,
    },
    Locked {
        slug: String,
        target_name: String,
        is_directory: bool,
        size_label: String,
        expires_label: Option<String>,
        has_error: bool,
    },
    Ready {
        slug: String,
        target_name: String,
        is_directory: bool,
        size_label: String,
        expires_label: Option<String>,
    },
}

#[derive(Debug, Deserialize)]
pub struct PublicPageQuery {
    pub error: Option<String>,
}

pub async fn get_public_share(
    State(state): State<AppState>,
    Path(slug): Path<String>,
    headers: HeaderMap,
    Query(query): Query<PublicPageQuery>,
) -> Response {
    let cookie_val = extract_unlock_cookie(&headers, &slug);
    let og_url = format!("{}/{}", state.public_base_url, slug);
    let prefix = state.public_path_prefix.clone();

    match state
        .shares
        .get_public_share_info(&slug, cookie_val.as_deref())
        .await
    {
        Ok(PublicShareStatus::Locked {
            share,
            total_size_bytes,
        }) => {
            let size_label = format_human_size(total_size_bytes);
            let expires_label = share
                .expires_at
                .map(|dt| dt.format("%d/%m/%Y à %H:%M UTC").to_string());
            let tpl = SharePageTemplate {
                title: format!("{} — Passerelle", share.target_name),
                og_title: share.target_name.clone(),
                og_description: format!("Partage protégé ({size_label})"),
                og_url,
                public_path_prefix: prefix,
                state: PublicViewState::Locked {
                    slug: share.slug.as_str().to_string(),
                    target_name: share.target_name,
                    is_directory: share.is_directory,
                    size_label,
                    expires_label,
                    has_error: query.error.is_some(),
                },
            };
            render_html(StatusCode::OK, tpl)
        }
        Ok(PublicShareStatus::Ready {
            share,
            total_size_bytes,
        }) => {
            let size_label = format_human_size(total_size_bytes);
            let expires_label = share
                .expires_at
                .map(|dt| dt.format("%d/%m/%Y à %H:%M UTC").to_string());
            let kind = if share.is_directory {
                "Archive ZIP"
            } else {
                "Fichier"
            };
            let tpl = SharePageTemplate {
                title: format!("{} — Passerelle", share.target_name),
                og_title: share.target_name.clone(),
                og_description: format!("{kind} · {size_label}"),
                og_url,
                public_path_prefix: prefix,
                state: PublicViewState::Ready {
                    slug: share.slug.as_str().to_string(),
                    target_name: share.target_name,
                    is_directory: share.is_directory,
                    size_label,
                    expires_label,
                },
            };
            render_html(StatusCode::OK, tpl)
        }
        Err(DomainError::ShareExpired) => render_gone(
            "Lien expiré",
            "Ce lien de partage a dépassé sa date de validité.",
            og_url,
            prefix,
        ),
        Err(DomainError::ShareRevoked) => render_gone(
            "Lien révoqué",
            "Ce lien de partage a été désactivé par son propriétaire.",
            og_url,
            prefix,
        ),
        Err(DomainError::QuotaExceeded) => render_gone(
            "Quota atteint",
            "Le nombre maximal de téléchargements autorisés pour ce lien a été atteint.",
            og_url,
            prefix,
        ),
        Err(DomainError::NotFound)
        | Err(DomainError::InvalidSlug)
        | Err(DomainError::ReservedSlug) => {
            let tpl = SharePageTemplate {
                title: "Partage introuvable — Passerelle".to_string(),
                og_title: "Partage introuvable".to_string(),
                og_description: "Ce lien de partage n'existe pas.".to_string(),
                og_url,
                public_path_prefix: prefix,
                state: PublicViewState::Gone {
                    heading: "Lien introuvable".to_string(),
                    message: "Aucun fichier ou dossier ne correspond à cette adresse.".to_string(),
                },
            };
            render_html(StatusCode::NOT_FOUND, tpl)
        }
        Err(err) => ApiError(err).into_response(),
    }
}

#[derive(Debug, Deserialize)]
pub struct UnlockForm {
    pub password: String,
}

pub async fn unlock_public_share(
    State(state): State<AppState>,
    Path(slug): Path<String>,
    headers: HeaderMap,
    extensions: Extensions,
    Form(form): Form<UnlockForm>,
) -> Response {
    let ip = extract_client_ip(&headers, &extensions);
    if !state.unlock_limiter.check_and_record(ip) {
        return ApiError(DomainError::RateLimited).into_response();
    }

    let prefix = &state.public_path_prefix;
    match state.shares.unlock_share(&slug, &form.password).await {
        Ok(signed_cookie) => {
            let cookie_name = format!("{UNLOCK_COOKIE_PREFIX}{slug}");
            let cookie_path = if prefix.is_empty() {
                "/".to_string()
            } else {
                format!("{prefix}/")
            };
            let set_cookie = format!(
                "{cookie_name}={signed_cookie}; Path={cookie_path}; Max-Age=3600; HttpOnly; SameSite=Lax"
            );
            let mut resp = Redirect::to(&format!("{prefix}/{slug}")).into_response();
            if let Ok(val) = HeaderValue::from_str(&set_cookie) {
                resp.headers_mut().insert(header::SET_COOKIE, val);
            }
            resp
        }
        Err(DomainError::InvalidPassword) => {
            Redirect::to(&format!("{prefix}/{slug}?error=1")).into_response()
        }
        Err(DomainError::ShareExpired) => render_gone(
            "Lien expiré",
            "Ce lien de partage a dépassé sa date de validité.",
            format!("{}/{}", state.public_base_url, slug),
            prefix.clone(),
        ),
        Err(DomainError::ShareRevoked) => render_gone(
            "Lien révoqué",
            "Ce lien de partage a été désactivé par son propriétaire.",
            format!("{}/{}", state.public_base_url, slug),
            prefix.clone(),
        ),
        Err(DomainError::QuotaExceeded) => render_gone(
            "Quota atteint",
            "Le nombre maximal de téléchargements autorisés pour ce lien a été atteint.",
            format!("{}/{}", state.public_base_url, slug),
            prefix.clone(),
        ),
        Err(err) => ApiError(err).into_response(),
    }
}

#[derive(Debug, Deserialize)]
pub struct DownloadQuery {
    pub password: Option<String>,
}

pub async fn download_public_share(
    State(state): State<AppState>,
    Path(slug): Path<String>,
    Query(query): Query<DownloadQuery>,
    req: Request,
) -> Result<Response, ApiError> {
    let cookie_val = extract_unlock_cookie(req.headers(), &slug);
    let provided_password = query
        .password
        .clone()
        .or_else(|| extract_basic_auth_password(req.headers()));

    let is_range_continuation = is_nonzero_range_header(req.headers());

    let share = state
        .shares
        .prepare_public_download(
            &slug,
            cookie_val.as_deref(),
            provided_password.as_deref(),
            is_range_continuation,
        )
        .await?;

    let abs_path = state.path_guard.resolve(&share.relative_path)?;

    if share.is_directory {
        let stream = stream_directory_zip(
            state.path_guard.clone(),
            abs_path,
            share.target_name.clone(),
        );
        let zip_name = format!("{}.zip", share.target_name);
        let disp = format!(
            "attachment; filename=\"{}\"; filename*=UTF-8''{}",
            sanitize_filename(&zip_name),
            urlencoding::encode(&zip_name)
        );
        let mut resp = Response::new(Body::from_stream(stream));
        *resp.status_mut() = StatusCode::OK;
        resp.headers_mut().insert(
            header::CONTENT_TYPE,
            HeaderValue::from_static("application/zip"),
        );
        if let Ok(val) = HeaderValue::from_str(&disp) {
            resp.headers_mut().insert(header::CONTENT_DISPOSITION, val);
        }
        resp.headers_mut()
            .insert(header::CACHE_CONTROL, HeaderValue::from_static("no-store"));
        Ok(resp)
    } else {
        let mut response = ServeFile::new(abs_path)
            .oneshot(req)
            .await
            .map_err(|e| ApiError(DomainError::Internal(format!("ServeFile error: {e}"))))?;

        let disp = format!(
            "attachment; filename=\"{}\"; filename*=UTF-8''{}",
            sanitize_filename(&share.target_name),
            urlencoding::encode(&share.target_name)
        );
        if let Ok(val) = HeaderValue::from_str(&disp) {
            response
                .headers_mut()
                .insert(header::CONTENT_DISPOSITION, val);
        }
        Ok(response.map(Body::new))
    }
}

fn render_gone(
    heading: &str,
    message: &str,
    og_url: String,
    public_path_prefix: String,
) -> Response {
    let tpl = SharePageTemplate {
        title: format!("{heading} — Passerelle"),
        og_title: heading.to_string(),
        og_description: message.to_string(),
        og_url,
        public_path_prefix,
        state: PublicViewState::Gone {
            heading: heading.to_string(),
            message: message.to_string(),
        },
    };
    render_html(StatusCode::GONE, tpl)
}

fn render_html(status: StatusCode, tpl: SharePageTemplate) -> Response {
    match tpl.render() {
        Ok(html) => (status, Html(html)).into_response(),
        Err(e) => {
            ApiError(DomainError::Internal(format!("Template render error: {e}"))).into_response()
        }
    }
}

fn extract_unlock_cookie(headers: &HeaderMap, slug: &str) -> Option<String> {
    let target_name = format!("{UNLOCK_COOKIE_PREFIX}{slug}");
    let raw_cookies = headers.get(header::COOKIE)?.to_str().ok()?;
    for pair in raw_cookies.split(';') {
        let trimmed = pair.trim();
        if let Some((name, value)) = trimmed.split_once('=') {
            if name.trim() == target_name {
                return Some(value.trim().to_string());
            }
        }
    }
    None
}

fn extract_basic_auth_password(headers: &HeaderMap) -> Option<String> {
    let auth = headers.get(header::AUTHORIZATION)?.to_str().ok()?;
    let encoded = auth.strip_prefix("Basic ")?.trim();
    let decoded_bytes = BASE64.decode(encoded).ok()?;
    let decoded = String::from_utf8(decoded_bytes).ok()?;
    let (_, pass) = decoded.split_once(':')?;
    Some(pass.to_string())
}

fn is_nonzero_range_header(headers: &HeaderMap) -> bool {
    let Some(range) = headers.get(header::RANGE).and_then(|v| v.to_str().ok()) else {
        return false;
    };
    let Some(spec) = range.trim().strip_prefix("bytes=") else {
        return false;
    };
    let first_range = spec.split(',').next().unwrap_or("").trim();
    let Some((start_str, _)) = first_range.split_once('-') else {
        return false;
    };
    if start_str.is_empty() {
        // Suffix range `bytes=-500`
        return true;
    }
    matches!(start_str.parse::<u64>(), Ok(start) if start > 0)
}

fn format_human_size(bytes: u64) -> String {
    const KIB: f64 = 1024.0;
    const MIB: f64 = KIB * 1024.0;
    const GIB: f64 = MIB * 1024.0;
    let b = bytes as f64;
    if b >= GIB {
        format!("{:.2} Go", b / GIB)
    } else if b >= MIB {
        format!("{:.1} Mo", b / MIB)
    } else if b >= KIB {
        format!("{:.1} Ko", b / KIB)
    } else {
        format!("{bytes} o")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::http::HeaderValue;

    #[test]
    fn nonzero_range_detection_handles_resume_vs_initial() {
        let mut h = HeaderMap::new();
        assert!(!is_nonzero_range_header(&h));

        h.insert(header::RANGE, HeaderValue::from_static("bytes=0-1023"));
        assert!(!is_nonzero_range_header(&h));

        h.insert(header::RANGE, HeaderValue::from_static("bytes=1024-"));
        assert!(is_nonzero_range_header(&h));
    }

    #[test]
    fn format_human_size_formats_units() {
        assert_eq!(format_human_size(512), "512 o");
        assert_eq!(format_human_size(2048), "2.0 Ko");
        assert_eq!(format_human_size(5 * 1024 * 1024), "5.0 Mo");
    }
}
