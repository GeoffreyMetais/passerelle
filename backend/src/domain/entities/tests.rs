use super::*;
use chrono::Duration;

fn sample_share() -> ShareLink {
    let now = Utc::now();
    ShareLink {
        id: Uuid::now_v7(),
        slug: Slug::from_trusted("test-slug"),
        relative_path: RelativeFilePath::root(),
        target_name: "Vacances".to_string(),
        is_directory: true,
        password_hash: None,
        expires_at: None,
        max_downloads: None,
        download_count: 0,
        is_revoked: false,
        created_at: now,
        last_accessed_at: None,
    }
}

#[test]
fn share_link_active_when_not_revoked_not_expired_under_quota() {
    let now = Utc::now();
    let mut share = sample_share();
    share.expires_at = Some(now + Duration::hours(1));
    share.max_downloads = Some(5);
    share.download_count = 4;

    assert!(!share.is_expired(now));
    assert!(!share.is_quota_reached());
    assert!(share.is_active(now));
    assert_eq!(share.verify_access(now), Ok(()));
}

#[test]
fn share_link_revoked_returns_share_revoked() {
    let now = Utc::now();
    let mut share = sample_share();
    share.is_revoked = true;

    assert!(!share.is_active(now));
    assert_eq!(share.verify_access(now), Err(DomainError::ShareRevoked));
}

#[test]
fn share_link_expired_returns_share_expired() {
    let now = Utc::now();
    let mut share = sample_share();
    share.expires_at = Some(now - Duration::seconds(1));

    assert!(share.is_expired(now));
    assert!(!share.is_active(now));
    assert_eq!(share.verify_access(now), Err(DomainError::ShareExpired));

    // Exact boundary timestamp is also expired
    share.expires_at = Some(now);
    assert!(share.is_expired(now));
    assert_eq!(share.verify_access(now), Err(DomainError::ShareExpired));
}

#[test]
fn share_link_quota_reached_returns_quota_exceeded() {
    let now = Utc::now();
    let mut share = sample_share();
    share.max_downloads = Some(2);
    share.download_count = 2;

    assert!(share.is_quota_reached());
    assert!(!share.is_active(now));
    assert_eq!(share.verify_access(now), Err(DomainError::QuotaExceeded));
}
