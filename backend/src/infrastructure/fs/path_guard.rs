use crate::domain::errors::DomainError;
use crate::domain::value_objects::RelativeFilePath;
use std::path::{Path, PathBuf};

#[derive(Clone)]
pub struct PathGuard {
    canonical_root: PathBuf,
}

impl PathGuard {
    pub fn new(data_dir: &Path) -> Result<Self, DomainError> {
        let canonical_root = std::fs::canonicalize(data_dir).map_err(|e| {
            DomainError::Internal(format!(
                "DATA_DIR inaccessible ({}): {e}",
                data_dir.display()
            ))
        })?;
        if !canonical_root.is_dir() {
            return Err(DomainError::Internal(format!(
                "DATA_DIR n'est pas un dossier: {}",
                canonical_root.display()
            )));
        }
        Ok(Self { canonical_root })
    }

    pub fn root(&self) -> &Path {
        &self.canonical_root
    }

    pub fn resolve(&self, rel: &RelativeFilePath) -> Result<PathBuf, DomainError> {
        let joined = if rel.is_root() {
            self.canonical_root.clone()
        } else {
            self.canonical_root.join(rel.as_str())
        };

        let canonical = match std::fs::canonicalize(&joined) {
            Ok(p) => p,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                return Err(DomainError::NotFound);
            }
            Err(e) if e.kind() == std::io::ErrorKind::PermissionDenied => {
                return Err(DomainError::ForbiddenPath);
            }
            Err(e) => {
                return Err(DomainError::Internal(format!(
                    "Path canonicalize error: {e}"
                )));
            }
        };

        if !canonical.starts_with(&self.canonical_root) {
            return Err(DomainError::ForbiddenPath);
        }
        Ok(canonical)
    }

    pub fn is_within_root(&self, candidate: &Path) -> bool {
        std::fs::canonicalize(candidate)
            .map(|c| c.starts_with(&self.canonical_root))
            .unwrap_or(false)
    }
}

#[cfg(test)]
mod tests;
