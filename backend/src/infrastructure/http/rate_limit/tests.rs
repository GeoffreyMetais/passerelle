use super::*;

#[test]
fn rate_limiter_blocks_after_max_requests_within_window() {
    let limiter = RateLimiter::new(3, Duration::from_secs(60));
    let ip = Some(IpAddr::V4(Ipv4Addr::new(10, 0, 0, 1)));
    assert!(limiter.check_and_record(ip));
    assert!(limiter.check_and_record(ip));
    assert!(limiter.check_and_record(ip));
    assert!(!limiter.check_and_record(ip));

    // Another IP is independent
    let ip2 = Some(IpAddr::V4(Ipv4Addr::new(10, 0, 0, 2)));
    assert!(limiter.check_and_record(ip2));
}
