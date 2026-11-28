use crate::domain::errors::DomainError;
use serde::{Deserialize, Serialize};
use std::fmt;

const RESERVED_SLUGS: &[&str] = &["api", "d", "health", "assets", "robots.txt", "favicon.ico"];

/// Value Object representing a validated URL slug `[a-zA-Z0-9_-]{3,64}` excluding reserved words.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(transparent)]
pub struct Slug(String);

impl Slug {
    /// Validates and builds a `Slug`.
    pub fn parse(raw: &str) -> Result<Self, DomainError> {
        let lower = raw.to_ascii_lowercase();
        if RESERVED_SLUGS.contains(&lower.as_str()) {
            return Err(DomainError::ReservedSlug);
        }
        if raw.len() < 3 || raw.len() > 64 {
            return Err(DomainError::InvalidSlug);
        }
        if !raw
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
        {
            return Err(DomainError::InvalidSlug);
        }
        Ok(Self(raw.to_string()))
    }

    /// Builds a `Slug` from an already validated source (e.g. SQLite database).
    pub fn from_trusted(raw: impl Into<String>) -> Self {
        Self(raw.into())
    }

    /// Generates a random 8-character alphanumeric slug.
    pub fn generate_random() -> Self {
        use rand::Rng;
        const ALPHANUM: &[u8] = b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        let mut rng = rand::thread_rng();
        let slug: String = (0..8)
            .map(|_| {
                let idx = rng.gen_range(0..ALPHANUM.len());
                ALPHANUM[idx] as char
            })
            .collect();
        Self(slug)
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for Slug {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

/// Value Object representing a secure relative path under `/data` (no `..`, no leading `/`, no null byte).
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(transparent)]
pub struct RelativeFilePath(String);

impl RelativeFilePath {
    pub fn root() -> Self {
        Self(String::new())
    }

    pub fn parse(raw: &str) -> Result<Self, DomainError> {
        if raw.contains('\0') {
            return Err(DomainError::ForbiddenPath);
        }

        let normalized = raw.replace('\\', "/");
        let trimmed = normalized.trim_matches('/');
        if trimmed.is_empty() {
            return Ok(Self::root());
        }

        let mut clean_segments = Vec::new();
        for segment in trimmed.split('/') {
            if segment.is_empty() {
                continue;
            }
            if segment == "." || segment == ".." {
                return Err(DomainError::ForbiddenPath);
            }
            clean_segments.push(segment);
        }

        Ok(Self(clean_segments.join("/")))
    }

    /// Builds a `RelativeFilePath` from an already validated source (e.g. SQLite database).
    pub fn from_trusted(raw: impl Into<String>) -> Self {
        Self(raw.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn is_root(&self) -> bool {
        self.0.is_empty()
    }

    pub fn parent(&self) -> Option<Self> {
        if self.0.is_empty() {
            return None;
        }
        match self.0.rsplit_once('/') {
            Some((parent, _)) => Some(Self(parent.to_string())),
            None => Some(Self::root()),
        }
    }

    pub fn file_name(&self) -> Option<&str> {
        if self.0.is_empty() {
            return None;
        }
        Some(match self.0.rsplit_once('/') {
            Some((_, name)) => name,
            None => &self.0,
        })
    }
}

impl fmt::Display for RelativeFilePath {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

#[cfg(test)]
mod tests;
