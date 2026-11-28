//! Read-only filesystem adapters: path guard, directory access, thumbnails, ZIP streaming.

mod local_fs;
mod path_guard;
mod thumbnails;
mod zip_stream;

pub use local_fs::LocalFileSystem;
pub use path_guard::PathGuard;
pub use thumbnails::WebpThumbnailer;
pub use zip_stream::stream_directory_zip;

use std::path::Path;

const HIDDEN_SYSTEM_NAMES: &[&str] = &["@eaDir", "Thumbs.db", ".DS_Store", "#recycle"];
const THUMBNAIL_EXTS: &[&str] = &["jpg", "jpeg", "png", "webp", "gif"];

pub(crate) fn is_hidden_name(name: &str) -> bool {
    name.starts_with('.') || HIDDEN_SYSTEM_NAMES.contains(&name)
}

pub(crate) fn has_thumbnail_support(name: &str) -> bool {
    Path::new(name)
        .extension()
        .and_then(|e| e.to_str())
        .is_some_and(|ext| THUMBNAIL_EXTS.contains(&ext.to_ascii_lowercase().as_str()))
}
