// Shared helpers are compiled into every integration-test crate; unused ones are expected.
#![allow(dead_code)]

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use image::{ImageBuffer, Rgb};
use passerelle_backend::build_app_with_limits;
use passerelle_backend::infrastructure::config::{Config, DEFAULT_MAX_ZIP_SIZE_BYTES};
use serde_json::{Value, json};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::time::Duration;
use tempfile::TempDir;
use tower::ServiceExt;

pub struct TestEnv {
    _data_dir: TempDir,
    _state_dir: TempDir,
    _outside_dir: TempDir,
    app: Router,
}

impl TestEnv {
    pub async fn new() -> Self {
        Self::with_options(50, 50, "https://s.domaine.fr", "").await
    }

    pub async fn with_limits(login_max: usize, unlock_max: usize) -> Self {
        Self::with_options(login_max, unlock_max, "https://s.domaine.fr", "").await
    }

    pub async fn with_subpath(public_base_url: &str, base_path: &str) -> Self {
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

    pub async fn send(&self, req: Request<Body>) -> (StatusCode, axum::http::HeaderMap, Vec<u8>) {
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

    pub async fn login(&self) -> (String, String) {
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
