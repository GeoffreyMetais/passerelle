mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::TestEnv;
use serde_json::{Value, json};

#[tokio::test]
async fn shares_lifecycle_reserved_slugs_active_count_quota_expiration_and_revocation() {
    let env = TestEnv::new().await;
    let (access, _) = env.login().await;

    // Reserved slugs are rejected
    for reserved in ["api", "d", "health", "assets"] {
        let req = Request::builder()
            .method("POST")
            .uri("/api/v1/shares")
            .header(header::AUTHORIZATION, format!("Bearer {access}"))
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(
                json!({
                    "relativePath": "readme.txt",
                    "customSlug": reserved
                })
                .to_string(),
            ))
            .unwrap();
        let (status, _, bytes) = env.send(req).await;
        assert_eq!(status, StatusCode::BAD_REQUEST);
        let err: Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(err["code"], "RESERVED_SLUG");
    }

    // Creation with auto-generated slug
    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/shares")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "relativePath": "readme.txt",
                "maxDownloads": 1
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::CREATED);
    let share: Value = serde_json::from_slice(&bytes).unwrap();
    let share_id = share["id"].as_str().unwrap().to_string();
    let slug = share["slug"].as_str().unwrap().to_string();
    assert_eq!(slug.len(), 8);

    // Badge activeShareCount = 1 in the file explorer
    let req = Request::builder()
        .uri("/api/v1/files")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (_, _, bytes) = env.send(req).await;
    let root: Value = serde_json::from_slice(&bytes).unwrap();
    let readme_entry = root["entries"]
        .as_array()
        .unwrap()
        .iter()
        .find(|e| e["name"] == "readme.txt")
        .unwrap();
    assert_eq!(readme_entry["activeShareCount"], 1);

    // QR Code SVG
    let qr_req = Request::builder()
        .uri(format!("/api/v1/shares/{share_id}/qr"))
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (status, headers, bytes) = env.send(qr_req).await;
    assert_eq!(status, StatusCode::OK);
    assert!(
        headers
            .get(header::CONTENT_TYPE)
            .unwrap()
            .to_str()
            .unwrap()
            .contains("image/svg+xml")
    );
    let svg = String::from_utf8(bytes).unwrap();
    assert!(svg.contains("<svg"));

    // 1st download -> 200 OK
    let dl1 = Request::builder()
        .uri(format!("/d/{slug}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(dl1).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(bytes, b"0123456789ABCDEF");

    // 2nd download -> 410 Gone (quota reached)
    let dl2 = Request::builder()
        .uri(format!("/d/{slug}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(dl2).await;
    assert_eq!(status, StatusCode::GONE);

    let page_gone = Request::builder()
        .uri(format!("/{slug}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(page_gone).await;
    assert_eq!(status, StatusCode::GONE);

    // Quota increase then revocation via PATCH
    let patch_req = Request::builder()
        .method("PATCH")
        .uri(format!("/api/v1/shares/{share_id}"))
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({ "maxDownloads": 5, "isRevoked": true }).to_string(),
        ))
        .unwrap();
    let (status, _, bytes) = env.send(patch_req).await;
    assert_eq!(status, StatusCode::OK);
    let patched: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(patched["isRevoked"], true);
    assert_eq!(patched["isActive"], false);

    let revoked_page = Request::builder()
        .uri(format!("/{slug}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(revoked_page).await;
    assert_eq!(status, StatusCode::GONE);

    // Link expired via PATCH in the past -> 410 Gone
    let unrevoke_expired = Request::builder()
        .method("PATCH")
        .uri(format!("/api/v1/shares/{share_id}"))
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "isRevoked": false,
                "expiresAt": "2020-01-01T00:00:00Z"
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, _) = env.send(unrevoke_expired).await;
    assert_eq!(status, StatusCode::OK);

    let expired_page = Request::builder()
        .uri(format!("/{slug}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(expired_page).await;
    assert_eq!(status, StatusCode::GONE);
}
