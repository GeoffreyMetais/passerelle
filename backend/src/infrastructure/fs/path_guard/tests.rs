use super::*;
use tempfile::TempDir;

#[test]
fn path_guard_blocks_traversal_and_escaping_symlinks() {
    let data_dir = TempDir::new().unwrap();
    let outside_dir = TempDir::new().unwrap();
    let secret_file = outside_dir.path().join("secret.txt");
    std::fs::write(&secret_file, "top-secret").unwrap();

    let inside_file = data_dir.path().join("ok.txt");
    std::fs::write(&inside_file, "hello").unwrap();

    #[cfg(unix)]
    std::os::unix::fs::symlink(&secret_file, data_dir.path().join("escape_link")).unwrap();

    let guard = PathGuard::new(data_dir.path()).unwrap();
    assert!(
        guard
            .resolve(&RelativeFilePath::parse("ok.txt").unwrap())
            .is_ok()
    );

    #[cfg(unix)]
    assert_eq!(
        guard.resolve(&RelativeFilePath::parse("escape_link").unwrap()),
        Err(DomainError::ForbiddenPath)
    );
}
