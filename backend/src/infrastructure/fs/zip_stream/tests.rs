use super::*;
use std::io::{Cursor, Read};
use tempfile::TempDir;

#[tokio::test]
async fn stream_directory_zip_produces_valid_archive_excluding_hidden_files() {
    let data_dir = TempDir::new().unwrap();
    let target = data_dir.path().join("Vacances");
    let sub = target.join("Japon");
    std::fs::create_dir_all(&sub).unwrap();
    std::fs::write(target.join("notes.txt"), b"Hello Japan").unwrap();
    std::fs::write(sub.join("kyoto.txt"), b"Kyoto shrine").unwrap();
    std::fs::write(target.join(".DS_Store"), b"ignored").unwrap();
    std::fs::create_dir_all(target.join("@eaDir")).unwrap();
    std::fs::write(target.join("@eaDir").join("thumb"), b"ignored").unwrap();

    let guard = PathGuard::new(data_dir.path()).unwrap();
    let mut stream = stream_directory_zip(guard, target, "Vacances".to_string());

    use futures::StreamExt;
    let mut zip_bytes = Vec::new();
    while let Some(chunk) = stream.next().await {
        zip_bytes.extend_from_slice(&chunk.unwrap());
    }

    let reader = Cursor::new(zip_bytes);
    let mut archive = zip::ZipArchive::new(reader).unwrap();
    assert_eq!(archive.len(), 2);

    let mut names = Vec::new();
    for i in 0..archive.len() {
        let mut file = archive.by_index(i).unwrap();
        let mut content = String::new();
        file.read_to_string(&mut content).unwrap();
        names.push((file.name().to_string(), content));
    }
    names.sort_by(|a, b| a.0.cmp(&b.0));

    assert_eq!(
        names,
        vec![
            (
                "Vacances/Japon/kyoto.txt".to_string(),
                "Kyoto shrine".to_string()
            ),
            ("Vacances/notes.txt".to_string(), "Hello Japan".to_string()),
        ]
    );
}
