use super::*;

#[test]
fn normalize_base_path_handles_root_slashes_and_whitespace() {
    assert_eq!(normalize_base_path(""), "");
    assert_eq!(normalize_base_path("/"), "");
    assert_eq!(normalize_base_path("///"), "");
    assert_eq!(normalize_base_path("   "), "");
    assert_eq!(normalize_base_path(" / "), "");
    assert_eq!(normalize_base_path("passerelle-api"), "/passerelle-api");
    assert_eq!(normalize_base_path("/passerelle-api"), "/passerelle-api");
    assert_eq!(normalize_base_path("/passerelle-api/"), "/passerelle-api");
    assert_eq!(normalize_base_path("  /nested/api/// "), "/nested/api");
}

#[test]
fn extract_url_path_prefix_extracts_path_without_trailing_slash() {
    assert_eq!(
        extract_url_path_prefix("https://partage.exemple.fr/passerelle-api"),
        "/passerelle-api"
    );
    assert_eq!(
        extract_url_path_prefix("https://partage.exemple.fr/passerelle-api/"),
        "/passerelle-api"
    );
    assert_eq!(
        extract_url_path_prefix("https://partage.exemple.fr/a/b/"),
        "/a/b"
    );
    assert_eq!(extract_url_path_prefix("https://s.domaine.fr"), "");
    assert_eq!(extract_url_path_prefix("http://localhost:8080/"), "");
    assert_eq!(extract_url_path_prefix(""), "");
}

#[test]
fn normalize_origin_strips_path_and_trailing_slashes() {
    assert_eq!(
        normalize_origin("https://partage.exemple.fr/passerelle/"),
        "https://partage.exemple.fr"
    );
    assert_eq!(
        normalize_origin("https://partage.exemple.fr/"),
        "https://partage.exemple.fr"
    );
    assert_eq!(
        normalize_origin("http://localhost:3000"),
        "http://localhost:3000"
    );
}
