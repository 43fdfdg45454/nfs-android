//! One disk cache for the whole app, whatever the servers: its size is a setting, and the app
//! shows how much it holds and can empty it.

use nfs_engine::DiskCache;
use std::sync::{Arc, Mutex};

static CACHE: Mutex<Option<(String, u64, Arc<DiskCache>)>> = Mutex::new(None);

/// The cache in `dir` with `bytes` at most (0: none). A new size applies to mounts made after.
pub(crate) fn get(dir: &str, bytes: u64) -> Option<Arc<DiskCache>> {
    if bytes == 0 {
        return None;
    }
    let mut cache = CACHE.lock().expect("not poisoned");
    match cache.as_ref() {
        Some((d, b, c)) if d == dir && *b == bytes => Some(c.clone()),
        _ => {
            let new = DiskCache::new(dir, bytes).ok()?;
            *cache = Some((dir.to_owned(), bytes, new.clone()));
            Some(new)
        }
    }
}

/// Bytes the cache in `dir` holds on disk.
#[uniffi::export]
pub fn cache_used(dir: String) -> u64 {
    DiskCache::new(dir, u64::MAX).map_or(0, |c| c.used())
}

/// Empties the cache in `dir`. Files being read keep what they have in memory.
#[uniffi::export]
pub fn cache_clear(dir: String) {
    if let Ok(cache) = DiskCache::new(dir, u64::MAX) {
        cache.clear();
    }
}
