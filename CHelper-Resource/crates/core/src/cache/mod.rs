//! 缓存层：HTTP 下载缓存与 caidlist 仓库数据快照缓存。

pub mod http;
pub mod tarball;

pub use http::HttpCache;
pub use tarball::RepoSource;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[derive(Debug, Serialize, Deserialize)]
pub struct CacheMeta {
    /// 获取时间（Unix 秒）。
    pub fetched_at: u64,
    /// 可选的附加信息（例如 HTTP ETag / 引用指向的 commit）。
    #[serde(default)]
    pub extra: serde_json::Value,
}

pub fn now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

pub fn meta_is_fresh(meta: &CacheMeta, ttl: Duration) -> bool {
    let age = now_unix().saturating_sub(meta.fetched_at);
    Duration::from_secs(age) < ttl
}

pub fn read_meta(path: &Path) -> Option<CacheMeta> {
    let text = std::fs::read_to_string(path).ok()?;
    serde_json::from_str(&text).ok()
}

pub fn write_meta(path: &Path, meta: &CacheMeta) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(path, serde_json::to_string_pretty(meta)?)?;
    Ok(())
}

pub fn sha256_hex(data: &[u8]) -> String {
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();
    hasher.update(data);
    hex::encode(hasher.finalize())
}

pub fn ensure_dir(path: &PathBuf) -> Result<()> {
    std::fs::create_dir_all(path)?;
    Ok(())
}
