use super::*;
use crate::application::fakes::{FakePasswordHasher, FakeSessionRepository, FakeTokenService};

fn make_auth() -> (AuthUseCases, Arc<FakeSessionRepository>) {
    let repo = Arc::new(FakeSessionRepository::default());
    let hasher = Arc::new(FakePasswordHasher);
    let token_svc = Arc::new(FakeTokenService::default());
    let hash = hasher.hash_password("secret-pass").unwrap();
    let uc = AuthUseCases::new(
        "admin".to_string(),
        hash,
        "https://s.domaine.fr".to_string(),
        repo.clone(),
        hasher,
        token_svc,
    );
    (uc, repo)
}

#[tokio::test]
async fn login_succeeds_and_creates_session() {
    let (uc, repo) = make_auth();
    let res = uc
        .login(LoginCommand {
            username: "admin".to_string(),
            password: "secret-pass".to_string(),
            device_name: Some("PWA Web".to_string()),
        })
        .await
        .expect("login succeeds");

    assert_eq!(res.expires_in_seconds, 900);
    assert_eq!(res.public_base_url, "https://s.domaine.fr");
    assert!(res.refresh_token.starts_with("rt_"));
    assert_eq!(repo.sessions.lock().unwrap().len(), 1);
}

#[tokio::test]
async fn login_rejects_invalid_credentials() {
    let (uc, _repo) = make_auth();
    let err = uc
        .login(LoginCommand {
            username: "admin".to_string(),
            password: "wrong".to_string(),
            device_name: None,
        })
        .await
        .unwrap_err();
    assert_eq!(err, DomainError::InvalidCredentials);

    let err2 = uc
        .login(LoginCommand {
            username: "other".to_string(),
            password: "secret-pass".to_string(),
            device_name: None,
        })
        .await
        .unwrap_err();
    assert_eq!(err2, DomainError::InvalidCredentials);
}

#[tokio::test]
async fn refresh_rotates_token_and_invalidates_old_one() {
    let (uc, _repo) = make_auth();
    let first = uc
        .login(LoginCommand {
            username: "admin".to_string(),
            password: "secret-pass".to_string(),
            device_name: None,
        })
        .await
        .unwrap();

    let second = uc.refresh(&first.refresh_token).await.expect("refresh ok");
    assert_ne!(second.refresh_token, first.refresh_token);

    // Old refresh token can no longer be reused
    let replay = uc.refresh(&first.refresh_token).await;
    assert_eq!(replay, Err(DomainError::Unauthorized));
}

#[tokio::test]
async fn logout_deletes_session() {
    let (uc, repo) = make_auth();
    let tokens = uc
        .login(LoginCommand {
            username: "admin".to_string(),
            password: "secret-pass".to_string(),
            device_name: None,
        })
        .await
        .unwrap();

    let (session_id, _) = uc.verify_access_token(&tokens.access_token).unwrap();
    uc.logout(session_id).await.unwrap();
    assert!(repo.sessions.lock().unwrap().is_empty());
    assert_eq!(
        uc.refresh(&tokens.refresh_token).await,
        Err(DomainError::Unauthorized)
    );
}
