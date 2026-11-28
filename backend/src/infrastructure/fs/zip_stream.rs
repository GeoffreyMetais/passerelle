use super::{PathGuard, is_hidden_name};
use async_zip::tokio::write::ZipFileWriter;
use async_zip::{Compression, ZipEntryBuilder};
use futures::io::AsyncWriteExt as FuturesAsyncWriteExt;
use std::path::PathBuf;
use tokio::io::AsyncReadExt;
use tokio_util::io::ReaderStream;
use walkdir::WalkDir;

/// Generates a ZIP64 stream at constant memory (`Stored` mode, no temporary disk storage).
pub fn stream_directory_zip(
    guard: PathGuard,
    dir_path: PathBuf,
    root_folder_name: String,
) -> ReaderStream<tokio::io::DuplexStream> {
    let (writer_half, reader_half) = tokio::io::duplex(65536);

    tokio::spawn(async move {
        let mut zip_writer = ZipFileWriter::with_tokio(writer_half);

        let files: Vec<(PathBuf, String)> = tokio::task::spawn_blocking({
            let guard = guard.clone();
            let dir = dir_path.clone();
            move || collect_zip_entries(&guard, &dir, &root_folder_name)
        })
        .await
        .unwrap_or_default();

        let mut buffer = vec![0u8; 65536];
        for (abs_path, archive_rel_path) in files {
            let Ok(mut file) = tokio::fs::File::open(&abs_path).await else {
                continue;
            };
            let entry_builder = ZipEntryBuilder::new(archive_rel_path.into(), Compression::Stored);
            let Ok(mut entry_writer) = zip_writer.write_entry_stream(entry_builder).await else {
                break;
            };

            while let Ok(n) = file.read(&mut buffer).await {
                if n == 0 {
                    break;
                }
                if FuturesAsyncWriteExt::write_all(&mut entry_writer, &buffer[..n])
                    .await
                    .is_err()
                {
                    return;
                }
            }

            if entry_writer.close().await.is_err() {
                return;
            }
        }

        let _ = zip_writer.close().await;
    });

    ReaderStream::new(reader_half)
}

fn collect_zip_entries(
    guard: &PathGuard,
    dir: &std::path::Path,
    root_folder_name: &str,
) -> Vec<(PathBuf, String)> {
    let mut files = Vec::new();
    for entry in WalkDir::new(dir)
        .follow_links(false)
        .sort_by_file_name()
        .into_iter()
        .filter_entry(|e| {
            if e.depth() == 0 {
                return true;
            }
            let name = e.file_name().to_string_lossy();
            !is_hidden_name(&name) && guard.is_within_root(e.path())
        })
        .flatten()
    {
        if entry.file_type().is_file() {
            if let Ok(rel) = entry.path().strip_prefix(dir) {
                let rel_str = rel.to_string_lossy().replace('\\', "/");
                let archive_path = format!("{root_folder_name}/{rel_str}");
                files.push((entry.path().to_path_buf(), archive_path));
            }
        }
    }
    files
}

#[cfg(test)]
mod tests {
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
}
