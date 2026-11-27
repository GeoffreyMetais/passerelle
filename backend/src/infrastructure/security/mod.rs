use crate::domain::errors::DomainError;
use crate::domain::ports::{PasswordHasher, TokenService};
use argon2::Argon2;
use argon2::password_hash::rand_core::OsRng;
use argon2::password_hash::{PasswordHash, PasswordHasher as _, PasswordVerifier, SaltString};
use chrono::Utc;
use hmac::{Hmac, Mac};
use jsonwebtoken::{
    DecodingKey, EncodingKey, Header, Validation, decode, encode, errors::ErrorKind,
};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use uuid::Uuid;

type HmacSha256 = Hmac<Sha256>;

pub struct Argon2Hasher;

impl PasswordHasher for Argon2Hasher {
    fn hash_password(&self, password: &str) -> Result<String, DomainError> {
        let salt = SaltString::generate(&mut OsRng);
        let argon2 = Argon2::default();
        argon2
            .hash_password(password.as_bytes(), &salt)
            .map(|h| h.to_string())
            .map_err(|e| DomainError::Internal(format!("Argon2 hash error: {e}")))
    }

    fn verify_password(&self, password: &str, hash: &str) -> Result<bool, DomainError> {
        let parsed = PasswordHash::new(hash)
            .map_err(|e| DomainError::Internal(format!("Invalid password hash: {e}")))?;
        Ok(Argon2::default()
            .verify_password(password.as_bytes(), &parsed)
            .is_ok())
    }
}

#[derive(Debug, Serialize, Deserialize)]
struct AccessClaims {
    sub: String,
    sid: String,
    exp: usize,
    iat: usize,
}

pub struct JwtTokenService {
    secret: Vec<u8>,
    access_ttl_seconds: i64,
}

impl JwtTokenService {
    pub fn new(secret: impl Into<Vec<u8>>, access_ttl_seconds: i64) -> Self {
        Self {
            secret: secret.into(),
            access_ttl_seconds,
        }
    }
}

impl TokenService for JwtTokenService {
    fn issue_access_token(&self, session_id: Uuid, username: &str) -> Result<String, DomainError> {
        let now = Utc::now().timestamp();
        let exp = now + self.access_ttl_seconds;
        let claims = AccessClaims {
            sub: username.to_string(),
            sid: session_id.to_string(),
            exp: usize::try_from(exp).unwrap_or(0),
            iat: usize::try_from(now).unwrap_or(0),
        };
        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(&self.secret),
        )
        .map_err(|e| DomainError::Internal(format!("JWT encode error: {e}")))
    }

    fn verify_access_token(&self, token: &str) -> Result<(Uuid, String), DomainError> {
        let mut validation = Validation::default();
        validation.leeway = 5;
        let data =
            decode::<AccessClaims>(token, &DecodingKey::from_secret(&self.secret), &validation)
                .map_err(|e| match e.kind() {
                    ErrorKind::ExpiredSignature => DomainError::TokenExpired,
                    _ => DomainError::Unauthorized,
                })?;

        let session_id =
            Uuid::parse_str(&data.claims.sid).map_err(|_| DomainError::Unauthorized)?;
        Ok((session_id, data.claims.sub))
    }

    fn generate_refresh_token(&self) -> String {
        let mut bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut bytes);
        format!("rt_{}", hex::encode(bytes))
    }

    fn hash_refresh_token(&self, token: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(token.as_bytes());
        hex::encode(hasher.finalize())
    }

    fn sign_unlock_cookie(&self, slug: &str, ttl_seconds: i64) -> Result<String, DomainError> {
        let exp = Utc::now().timestamp() + ttl_seconds;
        let payload = format!("{slug}:{exp}");
        let mut mac = HmacSha256::new_from_slice(&self.secret)
            .map_err(|e| DomainError::Internal(format!("HMAC init error: {e}")))?;
        mac.update(payload.as_bytes());
        let sig = hex::encode(mac.finalize().into_bytes());
        Ok(format!("{exp}.{sig}"))
    }

    fn verify_unlock_cookie(&self, cookie_value: &str, expected_slug: &str) -> bool {
        let Some((exp_str, sig_hex)) = cookie_value.split_once('.') else {
            return false;
        };
        let Ok(exp) = exp_str.parse::<i64>() else {
            return false;
        };
        if Utc::now().timestamp() >= exp {
            return false;
        }
        let Ok(sig_bytes) = hex::decode(sig_hex) else {
            return false;
        };
        let payload = format!("{expected_slug}:{exp}");
        let Ok(mut mac) = HmacSha256::new_from_slice(&self.secret) else {
            return false;
        };
        mac.update(payload.as_bytes());
        mac.verify_slice(&sig_bytes).is_ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn argon2_hash_and_verify() {
        let hasher = Argon2Hasher;
        let h1 = hasher.hash_password("hunter2").unwrap();
        let h2 = hasher.hash_password("hunter2").unwrap();
        assert_ne!(h1, h2);
        assert!(hasher.verify_password("hunter2", &h1).unwrap());
        assert!(!hasher.verify_password("wrong", &h1).unwrap());
    }

    #[test]
    fn jwt_and_unlock_cookie_roundtrip() {
        let svc = JwtTokenService::new(b"super-secret-hmac-key-32-bytes!!".to_vec(), 900);
        let sid = Uuid::now_v7();
        let token = svc.issue_access_token(sid, "admin").unwrap();
        let (parsed_sid, user) = svc.verify_access_token(&token).unwrap();
        assert_eq!(parsed_sid, sid);
        assert_eq!(user, "admin");

        let cookie = svc.sign_unlock_cookie("my-slug", 3600).unwrap();
        assert!(svc.verify_unlock_cookie(&cookie, "my-slug"));
        assert!(!svc.verify_unlock_cookie(&cookie, "other-slug"));
    }
}
