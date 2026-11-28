use super::*;
use axum::http::HeaderValue;

#[test]
fn nonzero_range_detection_handles_resume_vs_initial() {
    let mut h = HeaderMap::new();
    assert!(!is_nonzero_range_header(&h));

    h.insert(header::RANGE, HeaderValue::from_static("bytes=0-1023"));
    assert!(!is_nonzero_range_header(&h));

    h.insert(header::RANGE, HeaderValue::from_static("bytes=1024-"));
    assert!(is_nonzero_range_header(&h));
}

#[test]
fn format_human_size_formats_units() {
    assert_eq!(format_human_size(512), "512 o");
    assert_eq!(format_human_size(2048), "2.0 Ko");
    assert_eq!(format_human_size(5 * 1024 * 1024), "5.0 Mo");
}
