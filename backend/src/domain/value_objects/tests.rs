use super::*;

#[test]
fn slug_accepts_valid_characters_and_length() {
    let slug = Slug::parse("vacances-ete_2026").expect("valid slug");
    assert_eq!(slug.as_str(), "vacances-ete_2026");

    let min = Slug::parse("abc").expect("min length 3");
    assert_eq!(min.as_str(), "abc");

    let max_str = "a".repeat(64);
    assert!(Slug::parse(&max_str).is_ok());
}

#[test]
fn slug_rejects_invalid_length_or_characters() {
    assert_eq!(Slug::parse("ab"), Err(DomainError::InvalidSlug));
    assert_eq!(Slug::parse(&"a".repeat(65)), Err(DomainError::InvalidSlug));
    assert_eq!(Slug::parse("bad slug"), Err(DomainError::InvalidSlug));
    assert_eq!(Slug::parse("bad/slug"), Err(DomainError::InvalidSlug));
    assert_eq!(Slug::parse("été-2026"), Err(DomainError::InvalidSlug));
}

#[test]
fn slug_rejects_reserved_words_case_insensitively() {
    for reserved in [
        "api",
        "API",
        "d",
        "D",
        "health",
        "HEALTH",
        "assets",
        "robots.txt",
        "favicon.ico",
    ] {
        let res = Slug::parse(reserved);
        assert!(
            matches!(
                res,
                Err(DomainError::ReservedSlug) | Err(DomainError::InvalidSlug)
            ),
            "Expected rejection for reserved slug {reserved}, got {res:?}"
        );
        // Explicitly ensure valid-charset reserved slugs return ReservedSlug
        if !reserved.contains('.') && reserved.len() >= 3 {
            assert_eq!(res, Err(DomainError::ReservedSlug), "for {reserved}");
        }
    }
    assert_eq!(Slug::parse("d"), Err(DomainError::ReservedSlug));
    assert_eq!(Slug::parse("D"), Err(DomainError::ReservedSlug));
}

#[test]
fn slug_generate_random_is_8_alnum_chars_and_valid() {
    for _ in 0..50 {
        let s = Slug::generate_random();
        assert_eq!(s.as_str().len(), 8);
        assert!(s.as_str().chars().all(|c| c.is_ascii_alphanumeric()));
        assert!(Slug::parse(s.as_str()).is_ok());
    }
}

#[test]
fn relative_path_normalizes_and_accepts_valid_paths() {
    assert_eq!(RelativeFilePath::parse("").unwrap().as_str(), "");
    assert_eq!(RelativeFilePath::parse("/").unwrap().as_str(), "");
    assert_eq!(
        RelativeFilePath::parse("Photos/Vacances/")
            .unwrap()
            .as_str(),
        "Photos/Vacances"
    );
    assert_eq!(
        RelativeFilePath::parse("Photos\\Vacances\\plage.jpg")
            .unwrap()
            .as_str(),
        "Photos/Vacances/plage.jpg"
    );
    assert_eq!(
        RelativeFilePath::parse("Photos//Vacances")
            .unwrap()
            .as_str(),
        "Photos/Vacances"
    );
}

#[test]
fn relative_path_rejects_traversal_and_null_bytes() {
    assert_eq!(
        RelativeFilePath::parse(".."),
        Err(DomainError::ForbiddenPath)
    );
    assert_eq!(
        RelativeFilePath::parse("../etc/passwd"),
        Err(DomainError::ForbiddenPath)
    );
    assert_eq!(
        RelativeFilePath::parse("Photos/../../secret"),
        Err(DomainError::ForbiddenPath)
    );
    assert_eq!(
        RelativeFilePath::parse("Photos/./secret"),
        Err(DomainError::ForbiddenPath)
    );
    assert_eq!(
        RelativeFilePath::parse("Photos\0/file.txt"),
        Err(DomainError::ForbiddenPath)
    );
}

#[test]
fn relative_path_parent_and_file_name() {
    let root = RelativeFilePath::root();
    assert!(root.is_root());
    assert_eq!(root.parent(), None);
    assert_eq!(root.file_name(), None);

    let top = RelativeFilePath::parse("Photos").unwrap();
    assert_eq!(top.parent(), Some(RelativeFilePath::root()));
    assert_eq!(top.file_name(), Some("Photos"));

    let nested = RelativeFilePath::parse("Photos/Vacances/plage.jpg").unwrap();
    assert_eq!(
        nested.parent().map(|p| p.as_str().to_string()),
        Some("Photos/Vacances".to_string())
    );
    assert_eq!(nested.file_name(), Some("plage.jpg"));
}
