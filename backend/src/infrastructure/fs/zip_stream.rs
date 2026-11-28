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
mod tests;
