use crate::domain::errors::DomainError;
use std::net::SocketAddr;
use std::path::PathBuf;

pub const DEFAULT_MAX_ZIP_SIZE_BYTES: u64 = 10_737_418_240; // 10 GiB

#[derive(Debug, Clone)]
pub struct Config {
    pub bind_addr: SocketAddr,
    pub data_dir: PathBuf,
    pub state_dir: PathBuf,
    pub admin_username: String,
    pub admin_password: String,
    pub jwt_secret: String,
    pub public_base_url: String,
    pub base_path: String,
    pub allowed_origins: Vec<String>,
    pub max_zip_size_bytes: u64,
}

pub fn normalize_base_path(raw: &str) -> String {
    let trimmed = raw.trim().trim_matches('/');
    if trimmed.is_empty() {
        String::new()
    } else {
        format!("/{trimmed}")
    }
}

pub fn extract_url_path_prefix(public_base_url: &str) -> String {
    let trimmed = public_base_url.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    let after_scheme = match trimmed.split_once("://") {
        Some((_, rest)) => rest,
        None => trimmed,
    };
    let raw_path = match after_scheme.find('/') {
        Some(idx) => &after_scheme[idx..],
        None => return String::new(),
    };
    let without_query = raw_path.split(['?', '#']).next().unwrap_or("");
    normalize_base_path(without_query)
}

pub fn normalize_origin(raw: &str) -> String {
    let trimmed = raw.trim();
    if let Some((scheme, rest)) = trimmed.split_once("://") {
        let authority = rest.split(['/', '?', '#']).next().unwrap_or("");
        if authority.is_empty() {
            String::new()
        } else {
            format!("{scheme}://{authority}")
        }
    } else {
        trimmed.trim_end_matches('/').to_string()
    }
}

impl Config {
    pub fn from_env() -> Result<Self, DomainError> {
        let bind_raw = std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_string());
        let bind_addr: SocketAddr = bind_raw.parse().map_err(|e| {
            DomainError::ValidationError(format!("BIND_ADDR invalide '{bind_raw}': {e}"))
        })?;

        let data_dir =
            PathBuf::from(std::env::var("DATA_DIR").unwrap_or_else(|_| "/data".to_string()));
        let state_dir = PathBuf::from(
            std::env::var("STATE_DIR").unwrap_or_else(|_| "/var/lib/passerelle".to_string()),
        );
        let admin_username =
            std::env::var("ADMIN_USERNAME").unwrap_or_else(|_| "admin".to_string());

        let admin_password = std::env::var("ADMIN_PASSWORD")
            .ok()
            .filter(|s| !s.is_empty())
            .ok_or_else(|| {
                DomainError::ValidationError(
                    "La variable d'environnement ADMIN_PASSWORD est obligatoire.".to_string(),
                )
            })?;

        let jwt_secret = std::env::var("JWT_SECRET")
            .ok()
            .filter(|s| s.len() >= 16)
            .ok_or_else(|| {
                DomainError::ValidationError(
                    "La variable d'environnement JWT_SECRET est obligatoire (min 16 caractères)."
                        .to_string(),
                )
            })?;

        let public_base_url = std::env::var("PUBLIC_BASE_URL")
            .unwrap_or_else(|_| "http://localhost:8080".to_string())
            .trim()
            .trim_end_matches('/')
            .to_string();

        let raw_base_path = std::env::var("API_BASE_PATH")
            .or_else(|_| std::env::var("BASE_PATH"))
            .unwrap_or_default();
        let base_path = normalize_base_path(&raw_base_path);

        let allowed_origins = std::env::var("ALLOWED_ORIGINS")
            .unwrap_or_else(|_| "http://localhost:3000,http://localhost:8080".to_string())
            .split(',')
            .map(normalize_origin)
            .filter(|s| !s.is_empty())
            .collect();

        let max_zip_size_bytes = match std::env::var("MAX_ZIP_SIZE_BYTES") {
            Ok(v) => v.parse::<u64>().map_err(|e| {
                DomainError::ValidationError(format!("MAX_ZIP_SIZE_BYTES invalide '{v}': {e}"))
            })?,
            Err(_) => DEFAULT_MAX_ZIP_SIZE_BYTES,
        };

        Ok(Self {
            bind_addr,
            data_dir,
            state_dir,
            admin_username,
            admin_password,
            jwt_secret,
            public_base_url,
            base_path,
            allowed_origins,
            max_zip_size_bytes,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_base_path_handles_root_slashes_and_whitespace() {
        assert_eq!(normalize_base_path(""), "");
        assert_eq!(normalize_base_path("/"), "");
        assert_eq!(normalize_base_path("///"), "");
        assert_eq!(normalize_base_path("   "), "");
        assert_eq!(normalize_base_path(" / "), "");
        assert_eq!(normalize_base_path("passerelle-api"), "/passerelle-api");
        assert_eq!(normalize_base_path("/passerelle-api"), "/passerelle-api");
        assert_eq!(normalize_base_path("/passerelle-api/"), "/passerelle-api");
        assert_eq!(normalize_base_path("  /nested/api/// "), "/nested/api");
    }

    #[test]
    fn extract_url_path_prefix_extracts_path_without_trailing_slash() {
        assert_eq!(
            extract_url_path_prefix("https://partage.exemple.fr/passerelle-api"),
            "/passerelle-api"
        );
        assert_eq!(
            extract_url_path_prefix("https://partage.exemple.fr/passerelle-api/"),
            "/passerelle-api"
        );
        assert_eq!(
            extract_url_path_prefix("https://partage.exemple.fr/a/b/"),
            "/a/b"
        );
        assert_eq!(extract_url_path_prefix("https://s.domaine.fr"), "");
        assert_eq!(extract_url_path_prefix("http://localhost:8080/"), "");
        assert_eq!(extract_url_path_prefix(""), "");
    }

    #[test]
    fn normalize_origin_strips_path_and_trailing_slashes() {
        assert_eq!(
            normalize_origin("https://partage.exemple.fr/passerelle/"),
            "https://partage.exemple.fr"
        );
        assert_eq!(
            normalize_origin("https://partage.exemple.fr/"),
            "https://partage.exemple.fr"
        );
        assert_eq!(
            normalize_origin("http://localhost:3000"),
            "http://localhost:3000"
        );
    }
}
