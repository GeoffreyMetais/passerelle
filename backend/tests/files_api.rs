mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::TestEnv;
use serde_json::Value;

#[tokio::test]
async fn files_listing_breadcrumbs_hidden_search_thumbnails_and_path_traversal_blocking() {
    let env = TestEnv::new().await;
    let (access, _) = env.login().await;

    // Root listing: directories before files, hidden files & outgoing symlinks hidden
    let req = Request::builder()
        .uri("/api/v1/files")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::OK);
    let root: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(root["currentPath"], "");
    assert!(root["parentPath"].is_null());
    assert_eq!(root["breadcrumbs"].as_array().unwrap().len(), 1);
    let entries = root["entries"].as_array().unwrap();
    assert_eq!(entries[0]["name"], "Photos");
    assert_eq!(entries[0]["isDirectory"], true);
    assert!(entries.iter().all(|e| e["name"] != ".secret"));
    assert!(entries.iter().all(|e| e["name"] != "escape_symlink"));

    // Subdirectory listing with breadcrumbs and sorting
    let req = Request::builder()
        .uri("/api/v1/files?path=Photos/Vacances&sortBy=name&sortOrder=desc")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::OK);
    let sub: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(sub["currentPath"], "Photos/Vacances");
    assert_eq!(sub["parentPath"], "Photos");
    assert_eq!(sub["breadcrumbs"].as_array().unwrap().len(), 3);
    let sub_entries = sub["entries"].as_array().unwrap();
    assert_eq!(sub_entries[0]["name"], "Japon"); // directory always first even in desc order
    assert!(
        sub_entries
            .iter()
            .all(|e| e["name"] != ".DS_Store" && e["name"] != "@eaDir")
    );

    // Recursive search
    let req = Request::builder()
        .uri("/api/v1/files/search?q=KYOTO")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::OK);
    let search: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(search["results"].as_array().unwrap().len(), 1);
    assert_eq!(
        search["results"][0]["path"],
        "Photos/Vacances/Japon/kyoto.txt"
    );

    // WebP thumbnail + ETag 304 Not Modified
    let req = Request::builder()
        .uri("/api/v1/files/thumbnail?path=Photos/Vacances/plage.png")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (status, headers, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(headers.get(header::CONTENT_TYPE).unwrap(), "image/webp");
    assert!(bytes.starts_with(b"RIFF"));
    let etag = headers
        .get(header::ETAG)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();

    let req304 = Request::builder()
        .uri("/api/v1/files/thumbnail?path=Photos/Vacances/plage.png")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::IF_NONE_MATCH, etag)
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(req304).await;
    assert_eq!(status, StatusCode::NOT_MODIFIED);

    // Strict Path Traversal blocking
    for malicious in [
        "../etc/passwd",
        "%2e%2e/etc/passwd",
        "Photos/../../secret",
        "escape_symlink",
    ] {
        let req = Request::builder()
            .uri(format!("/api/v1/files/download?path={malicious}"))
            .header(header::AUTHORIZATION, format!("Bearer {access}"))
            .body(Body::empty())
            .unwrap();
        let (status, _, bytes) = env.send(req).await;
        assert_eq!(status, StatusCode::FORBIDDEN, "Failed to block {malicious}");
        let err: Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(err["code"], "FORBIDDEN_PATH");
    }
}
