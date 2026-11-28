mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::TestEnv;
use serde_json::{Value, json};
use std::io::{Cursor, Read};

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
