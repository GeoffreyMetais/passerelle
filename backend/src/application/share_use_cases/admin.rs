use super::ShareUseCases;
use super::commands::{CreateShareCommand, ShareDto, UpdateShareCommand};
use crate::domain::entities::ShareLink;
use crate::domain::errors::DomainError;
use crate::domain::ports::SharePatch;
use crate::domain::value_objects::{RelativeFilePath, Slug};
use chrono::{Duration, Utc};
use qrcode::QrCode;
use qrcode::render::svg;
use uuid::Uuid;

impl ShareUseCases {
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
                    "expiresInSeconds doit \u{ea}tre strictement positif.".to_string(),
                ));
            }
            Some(secs) => Some(now + Duration::seconds(secs)),
            None => None,
        };

        let max_downloads = match cmd.max_downloads {
            Some(max) if max <= 0 => {
                return Err(DomainError::ValidationError(
                    "maxDownloads doit \u{ea}tre strictement positif.".to_string(),
                ));
            }
            Some(max) => Some(max as u64),
            None => None,
        };

        let password_hash = match cmd.password {
            Some(ref p) if p.is_empty() => {
                return Err(DomainError::ValidationError(
                    "Le mot de passe ne peut pas \u{ea}tre vide.".to_string(),
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
            "Impossible de g\u{e9}n\u{e9}rer un slug unique.".to_string(),
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
                    "maxDownloads doit \u{ea}tre strictement positif.".to_string(),
                ));
            }
            Some(max) => Some(max as u64),
            None => None,
        };

        let new_password_hash = match cmd.new_password {
            Some(ref p) if p.is_empty() => {
                return Err(DomainError::ValidationError(
                    "Le mot de passe ne peut pas \u{ea}tre vide.".to_string(),
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
}
