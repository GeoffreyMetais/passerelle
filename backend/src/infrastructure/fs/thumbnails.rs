use super::PathGuard;
use crate::domain::errors::DomainError;
use crate::domain::ports::{ThumbnailPayload, ThumbnailPort};
use crate::domain::value_objects::RelativeFilePath;
use async_trait::async_trait;
use sha2::{Digest, Sha256};
use std::io::Cursor;
use std::path::{Path, PathBuf};

#[derive(Clone)]
pub struct WebpThumbnailer {
    guard: PathGuard,
    cache_dir: PathBuf,
}

impl WebpThumbnailer {
    pub async fn new(guard: PathGuard, state_dir: &Path) -> Result<Self, DomainError> {
        let cache_dir = state_dir.join("cache").join("thumbnails");
        tokio::fs::create_dir_all(&cache_dir).await.map_err(|e| {
            DomainError::Internal(format!("Impossible de cr\u{e9}er cache/thumbnails: {e}"))
        })?;
        Ok(Self { guard, cache_dir })
    }
}

#[async_trait]
impl ThumbnailPort for WebpThumbnailer {
    async fn get_or_generate_webp(
        &self,
        path: &RelativeFilePath,
    ) -> Result<ThumbnailPayload, DomainError> {
        let guard = self.guard.clone();
        let cache_dir = self.cache_dir.clone();
        let rel = path.clone();

        tokio::task::spawn_blocking(move || {
            let source = guard.resolve(&rel)?;
            let meta = std::fs::metadata(&source).map_err(|_| DomainError::NotFound)?;
            if !meta.is_file() {
                return Err(DomainError::NotFound);
            }
            let mtime_nanos = meta
                .modified()
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map(|d| d.as_nanos())
                .unwrap_or(0);

            let mut hasher = Sha256::new();
            hasher.update(rel.as_str().as_bytes());
            hasher.update(b":");
            hasher.update(mtime_nanos.to_le_bytes());
            let hash_hex = hex::encode(hasher.finalize());
            let etag = format!("\"{hash_hex}\"");
            let cache_path = cache_dir.join(format!("{hash_hex}.webp"));

            if cache_path.exists() {
                if let Ok(bytes) = std::fs::read(&cache_path) {
                    return Ok(ThumbnailPayload { bytes, etag });
                }
            }

            let img = image::open(&source)
                .map_err(|e| DomainError::Internal(format!("Image decode error: {e}")))?;
            let resized = img.thumbnail(256, 256);

            let mut buf = Vec::new();
            let mut cursor = Cursor::new(&mut buf);
            resized
                .write_to(&mut cursor, image::ImageFormat::WebP)
                .map_err(|e| DomainError::Internal(format!("WebP encode error: {e}")))?;

            let _ = std::fs::write(&cache_path, &buf);

            Ok(ThumbnailPayload { bytes: buf, etag })
        })
        .await
        .map_err(|e| DomainError::Internal(format!("spawn_blocking thumbnail error: {e}")))?
    }
}
