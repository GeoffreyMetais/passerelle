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
    pub allowed_origins: Vec<String>,
    pub max_zip_size_bytes: u64,
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
            .trim_end_matches('/')
            .to_string();

        let allowed_origins = std::env::var("ALLOWED_ORIGINS")
            .unwrap_or_else(|_| "http://localhost:3000,http://localhost:8080".to_string())
            .split(',')
            .map(|s| s.trim().to_string())
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
            allowed_origins,
            max_zip_size_bytes,
        })
    }
}
