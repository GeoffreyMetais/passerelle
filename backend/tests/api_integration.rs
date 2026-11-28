use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use image::{ImageBuffer, Rgb};
use passerelle_backend::build_app_with_limits;
use passerelle_backend::infrastructure::config::{Config, DEFAULT_MAX_ZIP_SIZE_BYTES};
use serde_json::{Value, json};
use std::io::{Cursor, Read};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::time::Duration;
use tempfile::TempDir;
use tower::ServiceExt;

struct TestEnv {
    _data_dir: TempDir,
    _state_dir: TempDir,
    _outside_dir: TempDir,
    app: Router,
}

impl TestEnv {
    async fn new() -> Self {
        Self::with_options(50, 50, "https://s.domaine.fr", "").await
    }

    async fn with_limits(login_max: usize, unlock_max: usize) -> Self {
        Self::with_options(login_max, unlock_max, "https://s.domaine.fr", "").await
    }

    async fn with_subpath(public_base_url: &str, base_path: &str) -> Self {
        Self::with_options(50, 50, public_base_url, base_path).await
    }

    async fn with_options(
        login_max: usize,
        unlock_max: usize,
        public_base_url: &str,
        base_path: &str,
    ) -> Self {
        let data_dir = TempDir::new().unwrap();
        let state_dir = TempDir::new().unwrap();
        let outside_dir = TempDir::new().unwrap();

        // Create a realistic directory tree in DATA_DIR
        let photos_dir = data_dir.path().join("Photos").join("Vacances");
        let japan_dir = photos_dir.join("Japon");
        std::fs::create_dir_all(&japan_dir).unwrap();

        std::fs::write(data_dir.path().join("readme.txt"), b"0123456789ABCDEF").unwrap();
        std::fs::write(photos_dir.join("notes.txt"), b"Vacances notes").unwrap();
        std::fs::write(japan_dir.join("kyoto.txt"), b"Kyoto temple").unwrap();

        // Hidden / NAS files to be ignored by default
        std::fs::write(data_dir.path().join(".secret"), b"hidden").unwrap();
        std::fs::write(photos_dir.join(".DS_Store"), b"mac").unwrap();
        std::fs::create_dir_all(photos_dir.join("@eaDir")).unwrap();
        std::fs::write(photos_dir.join("@eaDir").join("meta"), b"synology").unwrap();

        // Valid PNG image to test WebP thumbnail generation
        let img = ImageBuffer::from_fn(320, 240, |x, y| {
            Rgb([(x % 255) as u8, (y % 255) as u8, 128])
        });
        img.save(photos_dir.join("plage.png")).unwrap();

        // File outside DATA_DIR + outgoing symlink for anti-escape test
        let outside_secret = outside_dir.path().join("etc_passwd_mock");
        std::fs::write(&outside_secret, b"root:x:0:0").unwrap();
        #[cfg(unix)]
        std::os::unix::fs::symlink(&outside_secret, data_dir.path().join("escape_symlink"))
            .unwrap();

        let config = Config {
            bind_addr: SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 0),
            data_dir: data_dir.path().to_path_buf(),
            state_dir: state_dir.path().to_path_buf(),
            admin_username: "admin".to_string(),
            admin_password: "super-admin-password".to_string(),
            jwt_secret: "test-hmac-secret-at-least-32-bytes-long!".to_string(),
            public_base_url: public_base_url.to_string(),
            base_path: base_path.to_string(),
            allowed_origins: vec!["http://localhost:3000".to_string()],
            max_zip_size_bytes: DEFAULT_MAX_ZIP_SIZE_BYTES,
        };

        let app = build_app_with_limits(&config, login_max, unlock_max, Duration::from_secs(60))
            .await
            .unwrap();

        Self {
            _data_dir: data_dir,
            _state_dir: state_dir,
            _outside_dir: outside_dir,
            app,
        }
    }

    async fn send(&self, req: Request<Body>) -> (StatusCode, axum::http::HeaderMap, Vec<u8>) {
        let resp = self.app.clone().oneshot(req).await.unwrap();
        let status = resp.status();
        let headers = resp.headers().clone();
        let bytes = resp
            .into_body()
            .collect()
            .await
            .unwrap()
            .to_bytes()
            .to_vec();
        (status, headers, bytes)
    }

    async fn login(&self) -> (String, String) {
        let body = json!({
            "username": "admin",
            "password": "super-admin-password",
            "deviceName": "IntegrationTest"
        })
        .to_string();
        let req = Request::builder()
            .method("POST")
            .uri("/api/v1/auth/login")
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(body))
            .unwrap();
        let (status, _, bytes) = self.send(req).await;
        assert_eq!(status, StatusCode::OK);
        let v: Value = serde_json::from_slice(&bytes).unwrap();
        (
            v["accessToken"].as_str().unwrap().to_string(),
            v["refreshToken"].as_str().unwrap().to_string(),
        )
    }
}

#[tokio::test]
async fn health_and_auth_login_refresh_logout_and_unauthorized_rejection() {
    let env = TestEnv::new().await;

    // Health check
    let req = Request::builder()
        .uri("/health")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::OK);
    let v: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(v["status"], "ok");

    // Unauthorized access to protected endpoint
    let req = Request::builder()
        .uri("/api/v1/files")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(req).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let err: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(err["code"], "UNAUTHORIZED");

    // Login -> Refresh -> Logout
    let (access1, refresh1) = env.login().await;
    assert!(refresh1.starts_with("rt_"));

    let refresh_req = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/refresh")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(json!({ "refreshToken": refresh1 }).to_string()))
        .unwrap();
    let (status, _, bytes) = env.send(refresh_req).await;
    assert_eq!(status, StatusCode::OK);
    let v: Value = serde_json::from_slice(&bytes).unwrap();
    let access2 = v["accessToken"].as_str().unwrap().to_string();
    let refresh2 = v["refreshToken"].as_str().unwrap().to_string();
    assert_ne!(refresh1, refresh2);

    // Replay old refresh token fails
    let replay_req = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/refresh")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(json!({ "refreshToken": refresh1 }).to_string()))
        .unwrap();
    let (status, _, _) = env.send(replay_req).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    // Logout
    let logout_req = Request::builder()
        .method("DELETE")
        .uri("/api/v1/auth/logout")
        .header(header::AUTHORIZATION, format!("Bearer {access2}"))
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(logout_req).await;
    assert_eq!(status, StatusCode::NO_CONTENT);

    // Refresh after logout fails
    let post_logout = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/refresh")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(json!({ "refreshToken": refresh2 }).to_string()))
        .unwrap();
    let (status, _, _) = env.send(post_logout).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let _ = access1;
}

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

#[tokio::test]
async fn password_protected_share_unlock_cookie_query_password_and_range_no_double_count() {
    let env = TestEnv::new().await;
    let (access, _) = env.login().await;

    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/shares")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "relativePath": "readme.txt",
                "customSlug": "secure-readme",
                "password": "my-secret-password",
                "maxDownloads": 2
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, _) = env.send(req).await;
    assert_eq!(status, StatusCode::CREATED);

    // Public page shows the locked form
    let page_req = Request::builder()
        .uri("/secure-readme")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(page_req).await;
    assert_eq!(status, StatusCode::OK);
    let html = String::from_utf8(bytes).unwrap();
    assert!(html.contains("/secure-readme/unlock"));
    assert!(html.len() < 20 * 1024);

    // Download without password is rejected
    let dl_no_auth = Request::builder()
        .uri("/d/secure-readme")
        .body(Body::empty())
        .unwrap();
    let (status, _, _) = env.send(dl_no_auth).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    // Unlock via form -> 303 + Set-Cookie HttpOnly
    let unlock_req = Request::builder()
        .method("POST")
        .uri("/secure-readme/unlock")
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from("password=my-secret-password"))
        .unwrap();
    let (status, headers, _) = env.send(unlock_req).await;
    assert_eq!(status, StatusCode::SEE_OTHER);
    let set_cookie = headers
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(set_cookie.contains("HttpOnly"));
    let cookie_kv = set_cookie.split(';').next().unwrap().to_string();

    // Unlocked page shows the /d/secure-readme button
    let unlocked_page = Request::builder()
        .uri("/secure-readme")
        .header(header::COOKIE, &cookie_kv)
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(unlocked_page).await;
    assert_eq!(status, StatusCode::OK);
    let html = String::from_utf8(bytes).unwrap();
    assert!(html.contains("/d/secure-readme"));

    // Range download 0-3 (consumes 1 quota -> downloadCount = 1)
    let dl_part1 = Request::builder()
        .uri("/d/secure-readme")
        .header(header::COOKIE, &cookie_kv)
        .header(header::RANGE, "bytes=0-3")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(dl_part1).await;
    assert_eq!(status, StatusCode::PARTIAL_CONTENT);
    assert_eq!(bytes, b"0123");

    // Range resume 4-7 (offset > 0: MUST NOT increment downloadCount)
    let dl_part2 = Request::builder()
        .uri("/d/secure-readme")
        .header(header::COOKIE, &cookie_kv)
        .header(header::RANGE, "bytes=4-7")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(dl_part2).await;
    assert_eq!(status, StatusCode::PARTIAL_CONTENT);
    assert_eq!(bytes, b"4567");

    // Check that downloadCount is still 1
    let list_req = Request::builder()
        .uri("/api/v1/shares?path=readme.txt")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .body(Body::empty())
        .unwrap();
    let (_, _, bytes) = env.send(list_req).await;
    let list_json: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(list_json["shares"][0]["downloadCount"], 1);

    // Direct curl/wget download via ?password=... (consumes the 2nd and last quota)
    let dl_query = Request::builder()
        .uri("/d/secure-readme?password=my-secret-password")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(dl_query).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(bytes, b"0123456789ABCDEF");
}

#[tokio::test]
async fn folder_download_streams_valid_zip_with_nested_structure() {
    let env = TestEnv::new().await;
    let (access, _) = env.login().await;

    let req = Request::builder()
        .method("POST")
        .uri("/api/v1/shares")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "relativePath": "Photos/Vacances",
                "customSlug": "vacances-zip"
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, _) = env.send(req).await;
    assert_eq!(status, StatusCode::CREATED);

    let dl_req = Request::builder()
        .uri("/d/vacances-zip")
        .body(Body::empty())
        .unwrap();
    let (status, headers, bytes) = env.send(dl_req).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        headers.get(header::CONTENT_TYPE).unwrap(),
        "application/zip"
    );
    assert!(
        headers
            .get(header::CONTENT_DISPOSITION)
            .unwrap()
            .to_str()
            .unwrap()
            .contains("Vacances.zip")
    );

    let mut archive = zip::ZipArchive::new(Cursor::new(bytes)).unwrap();
    let mut file_names = Vec::new();
    for i in 0..archive.len() {
        let f = archive.by_index(i).unwrap();
        file_names.push(f.name().to_string());
    }
    file_names.sort();
    assert_eq!(
        file_names,
        vec![
            "Vacances/Japon/kyoto.txt",
            "Vacances/notes.txt",
            "Vacances/plage.png",
        ]
    );

    let mut kyoto = archive.by_name("Vacances/Japon/kyoto.txt").unwrap();
    let mut content = String::new();
    kyoto.read_to_string(&mut content).unwrap();
    assert_eq!(content, "Kyoto temple");
}

#[tokio::test]
async fn rate_limiting_returns_429_on_login_and_unlock() {
    let env = TestEnv::with_limits(2, 2).await;

    for _ in 0..2 {
        let req = Request::builder()
            .method("POST")
            .uri("/api/v1/auth/login")
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(
                json!({ "username": "admin", "password": "wrong" }).to_string(),
            ))
            .unwrap();
        let (status, _, _) = env.send(req).await;
        assert_eq!(status, StatusCode::UNAUTHORIZED);
    }

    let limited = Request::builder()
        .method("POST")
        .uri("/api/v1/auth/login")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({ "username": "admin", "password": "wrong" }).to_string(),
        ))
        .unwrap();
    let (status, _, bytes) = env.send(limited).await;
    assert_eq!(status, StatusCode::TOO_MANY_REQUESTS);
    let err: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(err["code"], "RATE_LIMITED");
}

#[tokio::test]
async fn subpath_hosting_prefixes_routes_urls_redirects_and_cookies() {
    let env = TestEnv::with_subpath(
        "https://partage.exemple.fr/passerelle-api",
        "/passerelle-api",
    )
    .await;

    // 1. Root /health (for Docker healthcheck) AND /passerelle-api/health both return 200
    for health_uri in ["/health", "/passerelle-api/health"] {
        let req = Request::builder()
            .uri(health_uri)
            .body(Body::empty())
            .unwrap();
        let (status, _, bytes) = env.send(req).await;
        assert_eq!(status, StatusCode::OK, "Expected 200 on {health_uri}");
        let v: Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(v["status"], "ok");
    }

    // 2. POST /passerelle-api/api/v1/auth/login -> 200
    let login_req = Request::builder()
        .method("POST")
        .uri("/passerelle-api/api/v1/auth/login")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "username": "admin",
                "password": "super-admin-password",
                "deviceName": "SubpathTest"
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, bytes) = env.send(login_req).await;
    assert_eq!(status, StatusCode::OK);
    let auth_json: Value = serde_json::from_slice(&bytes).unwrap();
    let access = auth_json["accessToken"].as_str().unwrap().to_string();
    assert_eq!(
        auth_json["publicBaseUrl"],
        "https://partage.exemple.fr/passerelle-api"
    );

    // 3. POST /passerelle-api/api/v1/shares -> publicUrl & directDownloadUrl include /passerelle-api
    let create_req = Request::builder()
        .method("POST")
        .uri("/passerelle-api/api/v1/shares")
        .header(header::AUTHORIZATION, format!("Bearer {access}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({
                "relativePath": "readme.txt",
                "customSlug": "subpath-secret",
                "password": "subpath-password"
            })
            .to_string(),
        ))
        .unwrap();
    let (status, _, bytes) = env.send(create_req).await;
    assert_eq!(status, StatusCode::CREATED);
    let share_json: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(
        share_json["publicUrl"],
        "https://partage.exemple.fr/passerelle-api/subpath-secret"
    );
    assert_eq!(
        share_json["directDownloadUrl"],
        "https://partage.exemple.fr/passerelle-api/d/subpath-secret"
    );

    // 4. GET /passerelle-api/<slug> -> HTML containing action="/passerelle-api/<slug>/unlock"
    let locked_req = Request::builder()
        .uri("/passerelle-api/subpath-secret")
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(locked_req).await;
    assert_eq!(status, StatusCode::OK);
    let html = String::from_utf8(bytes).unwrap();
    assert!(
        html.contains(r#"action="/passerelle-api/subpath-secret/unlock""#),
        "Unexpected form action in HTML: {html}"
    );

    // 4b. Wrong password redirects to /passerelle-api/<slug>?error=1
    let bad_unlock = Request::builder()
        .method("POST")
        .uri("/passerelle-api/subpath-secret/unlock")
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from("password=wrong"))
        .unwrap();
    let (status, headers, _) = env.send(bad_unlock).await;
    assert_eq!(status, StatusCode::SEE_OTHER);
    assert_eq!(
        headers.get(header::LOCATION).unwrap().to_str().unwrap(),
        "/passerelle-api/subpath-secret?error=1"
    );

    // 5. POST /passerelle-api/<slug>/unlock -> 303 See Other with Location: /passerelle-api/<slug> and Set-Cookie Path=/passerelle-api
    let unlock_req = Request::builder()
        .method("POST")
        .uri("/passerelle-api/subpath-secret/unlock")
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from("password=subpath-password"))
        .unwrap();
    let (status, headers, _) = env.send(unlock_req).await;
    assert_eq!(status, StatusCode::SEE_OTHER);
    assert_eq!(
        headers.get(header::LOCATION).unwrap().to_str().unwrap(),
        "/passerelle-api/subpath-secret"
    );
    let set_cookie = headers
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(
        set_cookie.contains("Path=/passerelle-api"),
        "Expected Path=/passerelle-api in Set-Cookie, got: {set_cookie}"
    );
    let cookie_kv = set_cookie.split(';').next().unwrap().to_string();

    // 6. GET /passerelle-api/<slug> with cookie -> HTML containing href="/passerelle-api/d/<slug>"
    let unlocked_req = Request::builder()
        .uri("/passerelle-api/subpath-secret")
        .header(header::COOKIE, &cookie_kv)
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(unlocked_req).await;
    assert_eq!(status, StatusCode::OK);
    let unlocked_html = String::from_utf8(bytes).unwrap();
    assert!(
        unlocked_html.contains(r#"href="/passerelle-api/d/subpath-secret""#),
        "Unexpected download link in HTML: {unlocked_html}"
    );

    // 7. GET /passerelle-api/d/<slug> with cookie -> 200 OK + file contents
    let dl_req = Request::builder()
        .uri("/passerelle-api/d/subpath-secret")
        .header(header::COOKIE, &cookie_kv)
        .body(Body::empty())
        .unwrap();
    let (status, _, bytes) = env.send(dl_req).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(bytes, b"0123456789ABCDEF");
}
