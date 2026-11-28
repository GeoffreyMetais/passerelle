use super::*;
use crate::application::fakes::{FakeFileSystem, FakeShareRepository, FakeThumbnailer};
use crate::domain::entities::ShareLink;
use crate::domain::value_objects::Slug;
use chrono::Duration;
use uuid::Uuid;

fn setup_fs() -> (FileUseCases, Arc<FakeShareRepository>) {
    let now = Utc::now();
    let fs = Arc::new(FakeFileSystem::default());
    let shares = Arc::new(FakeShareRepository::default());
    let thumb = Arc::new(FakeThumbnailer);

    let entries = vec![
        FileEntry {
            name: "zeta.txt".to_string(),
            path: "Photos/Vacances/zeta.txt".to_string(),
            is_directory: false,
            size_bytes: Some(100),
            modified_at: now - Duration::hours(5),
            mime_type: Some("text/plain".to_string()),
            has_thumbnail: false,
            active_share_count: 0,
        },
        FileEntry {
            name: "Japon".to_string(),
            path: "Photos/Vacances/Japon".to_string(),
            is_directory: true,
            size_bytes: None,
            modified_at: now - Duration::hours(1),
            mime_type: None,
            has_thumbnail: false,
            active_share_count: 0,
        },
        FileEntry {
            name: "alpha.jpg".to_string(),
            path: "Photos/Vacances/alpha.jpg".to_string(),
            is_directory: false,
            size_bytes: Some(5000),
            modified_at: now,
            mime_type: Some("image/jpeg".to_string()),
            has_thumbnail: true,
            active_share_count: 0,
        },
    ];

    {
        let mut lock = fs.entries.lock().unwrap();
        lock.push((
            "Photos/Vacances".to_string(),
            FileMetadata {
                name: "Vacances".to_string(),
                relative_path: RelativeFilePath::from_trusted("Photos/Vacances"),
                is_directory: true,
                size_bytes: None,
                total_size_bytes: 5100,
                modified_at: now,
                mime_type: None,
                has_thumbnail: false,
            },
            Some(entries),
        ));
        lock.push((
            "Photos/Vacances/alpha.jpg".to_string(),
            FileMetadata {
                name: "alpha.jpg".to_string(),
                relative_path: RelativeFilePath::from_trusted("Photos/Vacances/alpha.jpg"),
                is_directory: false,
                size_bytes: Some(5000),
                total_size_bytes: 5000,
                modified_at: now,
                mime_type: Some("image/jpeg".to_string()),
                has_thumbnail: true,
            },
            None,
        ));
    }

    (FileUseCases::new(fs, shares.clone(), thumb), shares)
}

#[tokio::test]
async fn list_directory_groups_folders_first_builds_breadcrumbs_and_enriches_shares() {
    let (uc, shares) = setup_fs();
    shares
        .create(&ShareLink {
            id: Uuid::now_v7(),
            slug: Slug::from_trusted("japon-2026"),
            relative_path: RelativeFilePath::from_trusted("Photos/Vacances/Japon"),
            target_name: "Japon".to_string(),
            is_directory: true,
            password_hash: None,
            expires_at: None,
            max_downloads: None,
            download_count: 0,
            is_revoked: false,
            created_at: Utc::now(),
            last_accessed_at: None,
        })
        .await
        .unwrap();

    let listing = uc
        .list_directory("Photos/Vacances", SortBy::Name, SortOrder::Desc, false)
        .await
        .unwrap();

    assert_eq!(listing.current_path, "Photos/Vacances");
    assert_eq!(listing.parent_path.as_deref(), Some("Photos"));
    assert_eq!(listing.breadcrumbs.len(), 3);
    assert_eq!(listing.breadcrumbs[0].name, "Racine");
    assert_eq!(listing.breadcrumbs[1].path, "Photos");
    assert_eq!(listing.breadcrumbs[2].path, "Photos/Vacances");

    // Folder "Japon" stays first even in Desc order!
    assert_eq!(listing.entries[0].name, "Japon");
    assert_eq!(listing.entries[0].active_share_count, 1);
    assert_eq!(listing.entries[1].name, "zeta.txt");
    assert_eq!(listing.entries[2].name, "alpha.jpg");
}

#[tokio::test]
async fn search_files_rejects_empty_query_and_returns_matches() {
    let (uc, _) = setup_fs();
    assert_eq!(
        uc.search_files("   ", "", false).await,
        Err(DomainError::ValidationError(
            "Le paramètre de recherche 'q' est obligatoire.".to_string()
        ))
    );

    let results = uc.search_files("ALPHA", "", false).await.unwrap();
    assert_eq!(results.len(), 1);
    assert_eq!(results[0].name, "alpha.jpg");
}

#[tokio::test]
async fn thumbnail_only_allowed_for_supported_files() {
    let (uc, _) = setup_fs();
    assert_eq!(
        uc.get_thumbnail("Photos/Vacances").await.unwrap_err(),
        DomainError::NotFound
    );
    let payload = uc.get_thumbnail("Photos/Vacances/alpha.jpg").await.unwrap();
    assert!(!payload.bytes.is_empty());
}
