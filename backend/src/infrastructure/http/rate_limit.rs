use std::collections::HashMap;
use std::net::{IpAddr, Ipv4Addr};
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// In-memory rate limiter (sliding window) per IP (or global fallback when there is no socket IP).
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
mod tests;
