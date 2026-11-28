use super::ShareUseCases;
use super::commands::{PublicShareStatus, UNLOCK_COOKIE_TTL_SECONDS};
use crate::domain::entities::ShareLink;
use crate::domain::errors::DomainError;
use crate::domain::value_objects::Slug;
use chrono::Utc;

impl ShareUseCases {
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
