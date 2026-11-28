mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::TestEnv;
use serde_json::{Value, json};

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
