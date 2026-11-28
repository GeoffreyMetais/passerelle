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
mod tests {
    use super::*;

    #[test]
    fn slug_accepts_valid_characters_and_length() {
        let slug = Slug::parse("vacances-ete_2026").expect("valid slug");
        assert_eq!(slug.as_str(), "vacances-ete_2026");

        let min = Slug::parse("abc").expect("min length 3");
        assert_eq!(min.as_str(), "abc");

        let max_str = "a".repeat(64);
        assert!(Slug::parse(&max_str).is_ok());
    }

    #[test]
    fn slug_rejects_invalid_length_or_characters() {
        assert_eq!(Slug::parse("ab"), Err(DomainError::InvalidSlug));
        assert_eq!(Slug::parse(&"a".repeat(65)), Err(DomainError::InvalidSlug));
        assert_eq!(Slug::parse("bad slug"), Err(DomainError::InvalidSlug));
        assert_eq!(Slug::parse("bad/slug"), Err(DomainError::InvalidSlug));
        assert_eq!(Slug::parse("été-2026"), Err(DomainError::InvalidSlug));
    }

    #[test]
    fn slug_rejects_reserved_words_case_insensitively() {
        for reserved in [
            "api",
            "API",
            "d",
            "D",
            "health",
            "HEALTH",
            "assets",
            "robots.txt",
            "favicon.ico",
        ] {
            let res = Slug::parse(reserved);
            assert!(
                matches!(
                    res,
                    Err(DomainError::ReservedSlug) | Err(DomainError::InvalidSlug)
                ),
                "Expected rejection for reserved slug {reserved}, got {res:?}"
            );
            // Explicitly ensure valid-charset reserved slugs return ReservedSlug
            if !reserved.contains('.') && reserved.len() >= 3 {
                assert_eq!(res, Err(DomainError::ReservedSlug), "for {reserved}");
            }
        }
        assert_eq!(Slug::parse("d"), Err(DomainError::ReservedSlug));
        assert_eq!(Slug::parse("D"), Err(DomainError::ReservedSlug));
    }

    #[test]
    fn slug_generate_random_is_8_alnum_chars_and_valid() {
        for _ in 0..50 {
            let s = Slug::generate_random();
            assert_eq!(s.as_str().len(), 8);
            assert!(s.as_str().chars().all(|c| c.is_ascii_alphanumeric()));
            assert!(Slug::parse(s.as_str()).is_ok());
        }
    }

    #[test]
    fn relative_path_normalizes_and_accepts_valid_paths() {
        assert_eq!(RelativeFilePath::parse("").unwrap().as_str(), "");
        assert_eq!(RelativeFilePath::parse("/").unwrap().as_str(), "");
        assert_eq!(
            RelativeFilePath::parse("Photos/Vacances/")
                .unwrap()
                .as_str(),
            "Photos/Vacances"
        );
        assert_eq!(
            RelativeFilePath::parse("Photos\\Vacances\\plage.jpg")
                .unwrap()
                .as_str(),
            "Photos/Vacances/plage.jpg"
        );
        assert_eq!(
            RelativeFilePath::parse("Photos//Vacances")
                .unwrap()
                .as_str(),
            "Photos/Vacances"
        );
    }

    #[test]
    fn relative_path_rejects_traversal_and_null_bytes() {
        assert_eq!(
            RelativeFilePath::parse(".."),
            Err(DomainError::ForbiddenPath)
        );
        assert_eq!(
            RelativeFilePath::parse("../etc/passwd"),
            Err(DomainError::ForbiddenPath)
        );
        assert_eq!(
            RelativeFilePath::parse("Photos/../../secret"),
            Err(DomainError::ForbiddenPath)
        );
        assert_eq!(
            RelativeFilePath::parse("Photos/./secret"),
            Err(DomainError::ForbiddenPath)
        );
        assert_eq!(
            RelativeFilePath::parse("Photos\0/file.txt"),
            Err(DomainError::ForbiddenPath)
        );
    }

    #[test]
    fn relative_path_parent_and_file_name() {
        let root = RelativeFilePath::root();
        assert!(root.is_root());
        assert_eq!(root.parent(), None);
        assert_eq!(root.file_name(), None);

        let top = RelativeFilePath::parse("Photos").unwrap();
        assert_eq!(top.parent(), Some(RelativeFilePath::root()));
        assert_eq!(top.file_name(), Some("Photos"));

        let nested = RelativeFilePath::parse("Photos/Vacances/plage.jpg").unwrap();
        assert_eq!(
            nested.parent().map(|p| p.as_str().to_string()),
            Some("Photos/Vacances".to_string())
        );
        assert_eq!(nested.file_name(), Some("plage.jpg"));
    }
}
