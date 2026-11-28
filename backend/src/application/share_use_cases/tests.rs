use super::*;
use crate::application::fakes::{
    FakeFileSystem, FakePasswordHasher, FakeShareRepository, FakeTokenService,
};
use crate::domain::errors::DomainError;
use crate::domain::ports::FileMetadata;
use crate::domain::value_objects::RelativeFilePath;
use chrono::Utc;
use std::sync::Arc;

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
