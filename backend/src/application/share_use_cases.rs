use crate::domain::entities::ShareLink;
use crate::domain::errors::DomainError;
use crate::domain::ports::{
    FileSystemPort, PasswordHasher, SharePatch, ShareRepository, TokenService,
};
use crate::domain::value_objects::{RelativeFilePath, Slug};
use chrono::{DateTime, Duration, Utc};
use qrcode::QrCode;
use qrcode::render::svg;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

pub const UNLOCK_COOKIE_TTL_SECONDS: i64 = 3600;

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateShareCommand {
    pub relative_path: String,
    pub custom_slug: Option<String>,
    pub password: Option<String>,
    pub expires_in_seconds: Option<i64>,
    pub max_downloads: Option<i64>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateShareCommand {
    pub is_revoked: Option<bool>,
    pub expires_at: Option<DateTime<Utc>>,
    #[serde(default)]
    pub clear_expiration: bool,
    pub max_downloads: Option<i64>,
    #[serde(default)]
    pub clear_max_downloads: bool,
    pub new_password: Option<String>,
    #[serde(default)]
    pub clear_password: bool,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ShareDto {
    pub id: Uuid,
    pub slug: String,
    pub public_url: String,
    pub direct_download_url: String,
    pub target_name: String,
    pub relative_path: String,
    pub is_directory: bool,
    pub has_password: bool,
    pub expires_at: Option<DateTime<Utc>>,
    pub max_downloads: Option<u64>,
    pub download_count: u64,
    pub is_revoked: bool,
    pub is_active: bool,
    pub created_at: DateTime<Utc>,
    pub last_accessed_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PublicShareStatus {
    Locked {
        share: ShareLink,
        total_size_bytes: u64,
    },
    Ready {
        share: ShareLink,
        total_size_bytes: u64,
    },
}

#[derive(Clone)]
pub struct ShareUseCases {
    shares: Arc<dyn ShareRepository>,
    fs: Arc<dyn FileSystemPort>,
    hasher: Arc<dyn PasswordHasher>,
    token_service: Arc<dyn TokenService>,
    public_base_url: String,
    max_zip_size_bytes: u64,
}

impl ShareUseCases {
    pub fn new(
        shares: Arc<dyn ShareRepository>,
        fs: Arc<dyn FileSystemPort>,
        hasher: Arc<dyn PasswordHasher>,
        token_service: Arc<dyn TokenService>,
        public_base_url: String,
        max_zip_size_bytes: u64,
    ) -> Self {
        Self {
            shares,
            fs,
            hasher,
            token_service,
            public_base_url: public_base_url.trim_end_matches('/').to_string(),
            max_zip_size_bytes,
        }
    }

    pub fn to_dto(&self, share: &ShareLink, now: DateTime<Utc>) -> ShareDto {
        ShareDto {
            id: share.id,
            slug: share.slug.as_str().to_string(),
            public_url: format!("{}/{}", self.public_base_url, share.slug.as_str()),
            direct_download_url: format!("{}/d/{}", self.public_base_url, share.slug.as_str()),
            target_name: share.target_name.clone(),
            relative_path: share.relative_path.as_str().to_string(),
            is_directory: share.is_directory,
            has_password: share.has_password(),
            expires_at: share.expires_at,
            max_downloads: share.max_downloads,
            download_count: share.download_count,
            is_revoked: share.is_revoked,
            is_active: share.is_active(now),
            created_at: share.created_at,
            last_accessed_at: share.last_accessed_at,
        }
    }

    pub async fn create_share(&self, cmd: CreateShareCommand) -> Result<ShareDto, DomainError> {
        let rel_path = RelativeFilePath::parse(&cmd.relative_path)?;
        if rel_path.is_root() {
            return Err(DomainError::ForbiddenPath);
        }

        let meta = self.fs.get_metadata(&rel_path).await?;
        if meta.is_directory && meta.total_size_bytes > self.max_zip_size_bytes {
            return Err(DomainError::PayloadTooLarge);
        }

        let now = Utc::now();
        let expires_at = match cmd.expires_in_seconds {
            Some(secs) if secs <= 0 => {
                return Err(DomainError::ValidationError(
                    "expiresInSeconds doit être strictement positif.".to_string(),
                ));
            }
            Some(secs) => Some(now + Duration::seconds(secs)),
            None => None,
        };

        let max_downloads = match cmd.max_downloads {
            Some(max) if max <= 0 => {
                return Err(DomainError::ValidationError(
                    "maxDownloads doit être strictement positif.".to_string(),
                ));
            }
            Some(max) => Some(max as u64),
            None => None,
        };

        let password_hash = match cmd.password {
            Some(ref p) if p.is_empty() => {
                return Err(DomainError::ValidationError(
                    "Le mot de passe ne peut pas être vide.".to_string(),
                ));
            }
            Some(ref p) => Some(self.hasher.hash_password(p)?),
            None => None,
        };

        let slug = match cmd.custom_slug {
            Some(ref raw) => {
                let s = Slug::parse(raw)?;
                if self.shares.find_by_slug(&s).await?.is_some() {
                    return Err(DomainError::SlugAlreadyExists);
                }
                s
            }
            None => self.generate_unique_slug().await?,
        };

        let share = ShareLink {
            id: Uuid::now_v7(),
            slug,
            relative_path: rel_path,
            target_name: meta.name,
            is_directory: meta.is_directory,
            password_hash,
            expires_at,
            max_downloads,
            download_count: 0,
            is_revoked: false,
            created_at: now,
            last_accessed_at: None,
        };

        self.shares.create(&share).await?;
        Ok(self.to_dto(&share, now))
    }

    async fn generate_unique_slug(&self) -> Result<Slug, DomainError> {
        for _ in 0..10 {
            let candidate = Slug::generate_random();
            if self.shares.find_by_slug(&candidate).await?.is_none() {
                return Ok(candidate);
            }
        }
        Err(DomainError::Internal(
            "Impossible de générer un slug unique.".to_string(),
        ))
    }

    pub async fn list_shares(
        &self,
        path_filter: Option<&str>,
        active_only: bool,
    ) -> Result<Vec<ShareDto>, DomainError> {
        let parsed_filter = match path_filter {
            Some(raw) => Some(RelativeFilePath::parse(raw)?),
            None => None,
        };
        let now = Utc::now();
        let shares = self
            .shares
            .list(parsed_filter.as_ref(), active_only, now)
            .await?;
        Ok(shares.iter().map(|s| self.to_dto(s, now)).collect())
    }

    pub async fn update_share(
        &self,
        id: Uuid,
        cmd: UpdateShareCommand,
    ) -> Result<ShareDto, DomainError> {
        let max_downloads = match cmd.max_downloads {
            Some(max) if max <= 0 => {
                return Err(DomainError::ValidationError(
                    "maxDownloads doit être strictement positif.".to_string(),
                ));
            }
            Some(max) => Some(max as u64),
            None => None,
        };

        let new_password_hash = match cmd.new_password {
            Some(ref p) if p.is_empty() => {
                return Err(DomainError::ValidationError(
                    "Le mot de passe ne peut pas être vide.".to_string(),
                ));
            }
            Some(ref p) => Some(self.hasher.hash_password(p)?),
            None => None,
        };

        let patch = SharePatch {
            is_revoked: cmd.is_revoked,
            expires_at: cmd.expires_at,
            clear_expiration: cmd.clear_expiration,
            max_downloads,
            clear_max_downloads: cmd.clear_max_downloads,
            new_password_hash,
            clear_password: cmd.clear_password,
        };

        let updated = self.shares.update(id, patch).await?;
        Ok(self.to_dto(&updated, Utc::now()))
    }

    pub async fn delete_share(&self, id: Uuid) -> Result<(), DomainError> {
        self.shares.delete(id).await
    }

    pub async fn generate_qr_svg(&self, id: Uuid) -> Result<String, DomainError> {
        let share = self
            .shares
            .find_by_id(id)
            .await?
            .ok_or(DomainError::NotFound)?;
        let url = format!("{}/{}", self.public_base_url, share.slug.as_str());
        let code = QrCode::new(url.as_bytes())
            .map_err(|e| DomainError::Internal(format!("QR generation error: {e}")))?;
        let svg_xml = code
            .render::<svg::Color<'_>>()
            .min_dimensions(256, 256)
            .dark_color(svg::Color("#111111"))
            .light_color(svg::Color("#ffffff"))
            .build();
        Ok(svg_xml)
    }

    pub async fn get_public_share_info(
        &self,
        raw_slug: &str,
        unlock_cookie: Option<&str>,
    ) -> Result<PublicShareStatus, DomainError> {
        let slug = Slug::parse(raw_slug).map_err(|_| DomainError::NotFound)?;
        let share = self
            .shares
            .find_by_slug(&slug)
            .await?
            .ok_or(DomainError::NotFound)?;

        let now = Utc::now();
        share.verify_access(now)?;

        let meta = self.fs.get_metadata(&share.relative_path).await?;
        let unlocked = !share.has_password()
            || unlock_cookie.is_some_and(|c| {
                self.token_service
                    .verify_unlock_cookie(c, share.slug.as_str())
            });

        if unlocked {
            Ok(PublicShareStatus::Ready {
                share,
                total_size_bytes: meta.total_size_bytes,
            })
        } else {
            Ok(PublicShareStatus::Locked {
                share,
                total_size_bytes: meta.total_size_bytes,
            })
        }
    }

    pub async fn unlock_share(
        &self,
        raw_slug: &str,
        password: &str,
    ) -> Result<String, DomainError> {
        let slug = Slug::parse(raw_slug).map_err(|_| DomainError::NotFound)?;
        let share = self
            .shares
            .find_by_slug(&slug)
            .await?
            .ok_or(DomainError::NotFound)?;

        share.verify_access(Utc::now())?;

        if let Some(ref hash) = share.password_hash {
            if !self.hasher.verify_password(password, hash)? {
                return Err(DomainError::InvalidPassword);
            }
        }

        self.token_service
            .sign_unlock_cookie(share.slug.as_str(), UNLOCK_COOKIE_TTL_SECONDS)
    }

    pub async fn prepare_public_download(
        &self,
        raw_slug: &str,
        unlock_cookie: Option<&str>,
        provided_password: Option<&str>,
        is_range_continuation: bool,
    ) -> Result<ShareLink, DomainError> {
        let slug = Slug::parse(raw_slug).map_err(|_| DomainError::NotFound)?;
        let share = self
            .shares
            .find_by_slug(&slug)
            .await?
            .ok_or(DomainError::NotFound)?;

        let now = Utc::now();
        share.verify_access(now)?;

        if let Some(ref hash) = share.password_hash {
            let cookie_ok = unlock_cookie.is_some_and(|c| {
                self.token_service
                    .verify_unlock_cookie(c, share.slug.as_str())
            });
            let password_ok = match provided_password {
                Some(p) => self.hasher.verify_password(p, hash)?,
                None => false,
            };
            if !cookie_ok && !password_ok {
                return Err(DomainError::InvalidPassword);
            }
        }

        // Check that the target still exists before incrementing the counter
        let _meta = self.fs.get_metadata(&share.relative_path).await?;

        // If this is a Range request continuation (offset > 0), do not increment the quota again
        if !is_range_continuation {
            let incremented = self.shares.increment_download_atomic(share.id, now).await?;
            if !incremented {
                // Re-read to return the exact error (quota vs expired vs revoked)
                if let Some(fresh) = self.shares.find_by_id(share.id).await? {
                    fresh.verify_access(now)?;
                }
                return Err(DomainError::QuotaExceeded);
            }
        }

        Ok(share)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::application::fakes::{
        FakeFileSystem, FakePasswordHasher, FakeShareRepository, FakeTokenService,
    };
    use crate::domain::ports::FileMetadata;

    fn setup(max_zip: u64) -> (ShareUseCases, Arc<FakeShareRepository>, Arc<FakeFileSystem>) {
        let shares = Arc::new(FakeShareRepository::default());
        let fs = Arc::new(FakeFileSystem::default());
        let hasher = Arc::new(FakePasswordHasher);
        let tokens = Arc::new(FakeTokenService::default());

        {
            let mut lock = fs.entries.lock().unwrap();
            lock.push((
                "Photos/Vacances".to_string(),
                FileMetadata {
                    name: "Vacances".to_string(),
                    relative_path: RelativeFilePath::from_trusted("Photos/Vacances"),
                    is_directory: true,
                    size_bytes: None,
                    total_size_bytes: 5000,
                    modified_at: Utc::now(),
                    mime_type: None,
                    has_thumbnail: false,
                },
                Some(vec![]),
            ));
            lock.push((
                "rapport.pdf".to_string(),
                FileMetadata {
                    name: "rapport.pdf".to_string(),
                    relative_path: RelativeFilePath::from_trusted("rapport.pdf"),
                    is_directory: false,
                    size_bytes: Some(1234),
                    total_size_bytes: 1234,
                    modified_at: Utc::now(),
                    mime_type: Some("application/pdf".to_string()),
                    has_thumbnail: false,
                },
                None,
            ));
        }

        let uc = ShareUseCases::new(
            shares.clone(),
            fs.clone(),
            hasher,
            tokens,
            "https://s.domaine.fr".to_string(),
            max_zip,
        );
        (uc, shares, fs)
    }

    #[tokio::test]
    async fn create_share_with_auto_slug_and_custom_slug() {
        let (uc, _, _) = setup(10_000_000);

        let auto_dto = uc
            .create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: None,
                password: None,
                expires_in_seconds: Some(3600),
                max_downloads: Some(5),
            })
            .await
            .unwrap();
        assert_eq!(auto_dto.slug.len(), 8);
        assert!(auto_dto.is_active);
        assert_eq!(auto_dto.max_downloads, Some(5));

        let custom_dto = uc
            .create_share(CreateShareCommand {
                relative_path: "Photos/Vacances".to_string(),
                custom_slug: Some("vacances-2026".to_string()),
                password: Some("topsecret".to_string()),
                expires_in_seconds: None,
                max_downloads: None,
            })
            .await
            .unwrap();
        assert_eq!(custom_dto.slug, "vacances-2026");
        assert!(custom_dto.has_password);
        assert_eq!(custom_dto.public_url, "https://s.domaine.fr/vacances-2026");
        assert_eq!(
            custom_dto.direct_download_url,
            "https://s.domaine.fr/d/vacances-2026"
        );
    }

    #[tokio::test]
    async fn create_share_rejects_oversized_directory_reserved_slug_and_duplicate_slug() {
        let (uc, _, _) = setup(1000); // 1000 bytes max, Vacances is 5000 bytes

        let err = uc
            .create_share(CreateShareCommand {
                relative_path: "Photos/Vacances".to_string(),
                custom_slug: None,
                password: None,
                expires_in_seconds: None,
                max_downloads: None,
            })
            .await
            .unwrap_err();
        assert_eq!(err, DomainError::PayloadTooLarge);

        let (uc2, _, _) = setup(10_000_000);
        assert_eq!(
            uc2.create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: Some("api".to_string()),
                password: None,
                expires_in_seconds: None,
                max_downloads: None,
            })
            .await
            .unwrap_err(),
            DomainError::ReservedSlug
        );

        uc2.create_share(CreateShareCommand {
            relative_path: "rapport.pdf".to_string(),
            custom_slug: Some("mon-doc".to_string()),
            password: None,
            expires_in_seconds: None,
            max_downloads: None,
        })
        .await
        .unwrap();

        assert_eq!(
            uc2.create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: Some("mon-doc".to_string()),
                password: None,
                expires_in_seconds: None,
                max_downloads: None,
            })
            .await
            .unwrap_err(),
            DomainError::SlugAlreadyExists
        );
    }

    #[tokio::test]
    async fn unlock_and_quota_and_range_continuation() {
        let (uc, _, _) = setup(10_000_000);
        let dto = uc
            .create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: Some("secret-doc".to_string()),
                password: Some("pass123".to_string()),
                expires_in_seconds: None,
                max_downloads: Some(1),
            })
            .await
            .unwrap();

        // Locked initially
        assert!(matches!(
            uc.get_public_share_info("secret-doc", None).await.unwrap(),
            PublicShareStatus::Locked { .. }
        ));

        // Wrong password fails
        assert_eq!(
            uc.unlock_share("secret-doc", "wrong").await.unwrap_err(),
            DomainError::InvalidPassword
        );

        // Correct password yields valid cookie
        let cookie = uc.unlock_share("secret-doc", "pass123").await.unwrap();
        assert!(matches!(
            uc.get_public_share_info("secret-doc", Some(&cookie))
                .await
                .unwrap(),
            PublicShareStatus::Ready { .. }
        ));

        // First download succeeds (increments count to 1)
        uc.prepare_public_download("secret-doc", Some(&cookie), None, false)
            .await
            .unwrap();

        // Range continuation would fail verify_access only if quota already reached,
        // so test range continuation BEFORE reaching max_downloads on another link:
        let _ = dto;
        let _ = uc
            .create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: Some("range-doc".to_string()),
                password: None,
                expires_in_seconds: None,
                max_downloads: Some(1),
            })
            .await
            .unwrap();
        // Continuation chunk does not consume quota
        uc.prepare_public_download("range-doc", None, None, true)
            .await
            .unwrap();
        // Initial download still succeeds (consumes the 1 allowed download)
        uc.prepare_public_download("range-doc", None, None, false)
            .await
            .unwrap();
        // Next initial download fails with QuotaExceeded
        assert_eq!(
            uc.prepare_public_download("range-doc", None, None, false)
                .await
                .unwrap_err(),
            DomainError::QuotaExceeded
        );
    }

    #[tokio::test]
    async fn update_share_clears_fields_and_generates_qr_svg() {
        let (uc, _, _) = setup(10_000_000);
        let dto = uc
            .create_share(CreateShareCommand {
                relative_path: "rapport.pdf".to_string(),
                custom_slug: Some("qr-test".to_string()),
                password: Some("pwd".to_string()),
                expires_in_seconds: Some(600),
                max_downloads: Some(10),
            })
            .await
            .unwrap();

        let patched = uc
            .update_share(
                dto.id,
                UpdateShareCommand {
                    is_revoked: Some(true),
                    expires_at: None,
                    clear_expiration: true,
                    max_downloads: None,
                    clear_max_downloads: true,
                    new_password: None,
                    clear_password: true,
                },
            )
            .await
            .unwrap();
        assert!(patched.is_revoked);
        assert!(!patched.is_active);
        assert!(patched.expires_at.is_none());
        assert!(patched.max_downloads.is_none());
        assert!(!patched.has_password);

        let svg = uc.generate_qr_svg(dto.id).await.unwrap();
        assert!(svg.contains("<svg"));
        assert!(svg.contains("</svg>"));
    }
}
