use super::*;

#[test]
fn argon2_hash_and_verify() {
    let hasher = Argon2Hasher;
    let h1 = hasher.hash_password("hunter2").unwrap();
    let h2 = hasher.hash_password("hunter2").unwrap();
    assert_ne!(h1, h2);
    assert!(hasher.verify_password("hunter2", &h1).unwrap());
    assert!(!hasher.verify_password("wrong", &h1).unwrap());
}

#[test]
fn jwt_and_unlock_cookie_roundtrip() {
    let svc = JwtTokenService::new(b"super-secret-hmac-key-32-bytes!!".to_vec(), 900);
    let sid = Uuid::now_v7();
    let token = svc.issue_access_token(sid, "admin").unwrap();
    let (parsed_sid, user) = svc.verify_access_token(&token).unwrap();
    assert_eq!(parsed_sid, sid);
    assert_eq!(user, "admin");

    let cookie = svc.sign_unlock_cookie("my-slug", 3600).unwrap();
    assert!(svc.verify_unlock_cookie(&cookie, "my-slug"));
    assert!(!svc.verify_unlock_cookie(&cookie, "other-slug"));
}
