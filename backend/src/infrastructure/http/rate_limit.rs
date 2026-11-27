use std::collections::HashMap;
use std::net::{IpAddr, Ipv4Addr};
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// Limiteur de requêtes en mémoire (fenêtre glissante) par IP (ou fallback global si pas d'IP socket).
pub struct RateLimiter {
    max_requests: usize,
    window: Duration,
    buckets: Mutex<HashMap<IpAddr, Vec<Instant>>>,
}

impl RateLimiter {
    pub fn new(max_requests: usize, window: Duration) -> Self {
        Self {
            max_requests,
            window,
            buckets: Mutex::new(HashMap::new()),
        }
    }

    pub fn check_and_record(&self, key: Option<IpAddr>) -> bool {
        let addr = key.unwrap_or(IpAddr::V4(Ipv4Addr::UNSPECIFIED));
        let now = Instant::now();
        let mut buckets = self.buckets.lock().unwrap_or_else(|e| e.into_inner());
        let timestamps = buckets.entry(addr).or_default();
        timestamps.retain(|t| now.duration_since(*t) < self.window);

        if timestamps.len() >= self.max_requests {
            return false;
        }
        timestamps.push(now);
        true
    }
}

#[cfg(test)]
mod tests {
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
}
