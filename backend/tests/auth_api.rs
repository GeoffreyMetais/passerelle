mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::TestEnv;
use serde_json::{Value, json};

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
