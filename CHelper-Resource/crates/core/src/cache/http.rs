//! HTTP 下载缓存：URL 哈希为键，带 TTL，下载失败时回退旧缓存。

use super::{read_meta, write_meta, CacheMeta, now_unix};
use anyhow::{anyhow, Context, Result};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tracing::{debug, warn};

pub struct HttpCache {
    client: reqwest::Client,
    data_dir: PathBuf,
    meta_dir: PathBuf,
}

fn url_key(url: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(url.as_bytes());
    hex::encode(hasher.finalize())
}

impl HttpCache {
    pub fn new(cache_dir: &Path) -> Result<Self> {
        let data_dir = cache_dir.join("http/data");
        let meta_dir = cache_dir.join("http/meta");
        std::fs::create_dir_all(&data_dir)?;
        std::fs::create_dir_all(&meta_dir)?;
        let mut headers = reqwest::header::HeaderMap::new();
        headers.insert(
            reqwest::header::USER_AGENT,
            "chelper-resource/0.1 (CHelper resource pack generator)".parse().unwrap(),
        );
        let client = reqwest::Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(300))
            .build()?;
        Ok(Self {
            client,
            data_dir,
            meta_dir,
        })
    }

    fn paths(&self, url: &str) -> (PathBuf, PathBuf) {
        let key = url_key(url);
        (self.data_dir.join(&key), self.meta_dir.join(format!("{key}.json")))
    }

    /// 获取 URL 内容（字节）。缓存未过期直接返回；否则重新下载；下载失败时回退旧缓存。
    pub async fn get(&self, url: &str, ttl: Duration) -> Result<Vec<u8>> {
        let (data_path, meta_path) = self.paths(url);
        let stale = data_path.exists().then(|| std::fs::read(&data_path).ok()).flatten();
        if let Some(data) = &stale {
            if let Some(meta) = read_meta(&meta_path) {
                if super::meta_is_fresh(&meta, ttl) {
                    debug!(url, size = data.len(), "http cache hit");
                    return Ok(data.clone());
                }
            }
        }
        match self.download(url).await {
            Ok(bytes) => {
                std::fs::write(&data_path, &bytes)
                    .with_context(|| format!("写入缓存失败: {}", data_path.display()))?;
                write_meta(&meta_path, &CacheMeta { fetched_at: now_unix(), extra: serde_json::Value::Null })?;
                Ok(bytes)
            }
            Err(err) => {
                if let Some(data) = stale {
                    warn!(url, %err, "下载失败，回退旧缓存");
                    return Ok(data);
                }
                Err(err)
            }
        }
    }

    pub async fn get_text(&self, url: &str, ttl: Duration) -> Result<String> {
        let bytes = self.get(url, ttl).await?;
        String::from_utf8(bytes).with_context(|| format!("响应不是 UTF-8: {url}"))
    }

    /// 带内容校验（sha1）的获取：缓存内容与期望 sha1 不符时重新下载。
    pub async fn get_checked(&self, url: &str, sha1: &str, ttl: Duration) -> Result<Vec<u8>> {
        if !sha1.is_empty() {
            let (data_path, _) = self.paths(url);
            if data_path.exists() {
                if let Ok(data) = std::fs::read(&data_path) {
                    use sha1::{Digest as _, Sha1};
                    let mut hasher = Sha1::new();
                    hasher.update(&data);
                    if hex::encode(hasher.finalize()) == sha1 {
                        return Ok(data);
                    }
                    tracing::warn!(url, "缓存内容 sha1 不匹配，重新下载");
                    let _ = std::fs::remove_file(&data_path);
                }
            }
        }
        self.get(url, ttl).await
    }

    pub async fn get_json(&self, url: &str, ttl: Duration) -> Result<serde_json::Value> {
        let text = self.get_text(url, ttl).await?;
        serde_json::from_str(&text).with_context(|| format!("解析 JSON 失败: {url}"))
    }

    /// POST 表单（不走缓存，用于 Scribunto 控制台等交互式接口）。
    pub async fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<String> {
        debug!(url, "posting form");
        let resp = self
            .client
            .post(url)
            .form(form)
            .send()
            .await
            .with_context(|| format!("请求失败: {url}"))?;
        let status = resp.status();
        if !status.is_success() {
            return Err(anyhow!("HTTP {status}: {url}"));
        }
        resp.text().await.with_context(|| format!("读取响应失败: {url}"))
    }

    async fn download(&self, url: &str) -> Result<Vec<u8>> {
        debug!(url, "downloading");
        let resp = self
            .client
            .get(url)
            .send()
            .await
            .with_context(|| format!("请求失败: {url}"))?;
        let status = resp.status();
        if !status.is_success() {
            return Err(anyhow!("HTTP {status}: {url}"));
        }
        let bytes = resp
            .bytes()
            .await
            .with_context(|| format!("读取响应失败: {url}"))?;
        Ok(bytes.to_vec())
    }
}
