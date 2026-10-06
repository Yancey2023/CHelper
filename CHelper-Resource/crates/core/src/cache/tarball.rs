//! caidlist 仓库数据快照：按引用拉取 GitHub tarball（或本地 `git archive`），
//! 只解压需要的子目录（`version/`、`translation/`），以 commit sha 为缓存键。

use super::{read_meta, write_meta, CacheMeta, now_unix};
use crate::config::Source;
use anyhow::{bail, Context, Result};
use flate2::read::GzDecoder;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::process::Command;
use tracing::{debug, info};

/// 需要从仓库快照中提取的子目录。
pub const NEEDED_PREFIXES: &[&str] = &["version/", "translation/"];

pub struct RepoSource {
    pub name: String,
    pub source: Source,
    http: std::sync::Arc<HttpAlias>,
    cache_dir: PathBuf,
    ref_ttl: Duration,
    refresh: bool,
}

// 避免循环依赖的简单别名（真实类型是 HttpCache）。
type HttpAlias = crate::cache::HttpCache;

fn sanitize_component(s: &str) -> String {
    s.chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.' { c } else { '_' })
        .collect()
}

impl RepoSource {
    pub fn new(
        name: &str,
        source: &Source,
        http: std::sync::Arc<crate::cache::HttpCache>,
        cache_dir: &Path,
        ref_ttl_minutes: u64,
        refresh: bool,
    ) -> Self {
        Self {
            name: name.to_string(),
            source: source.clone(),
            http,
            cache_dir: cache_dir.to_path_buf(),
            ref_ttl: Duration::from_secs(ref_ttl_minutes * 60),
            refresh,
        }
    }

    async fn git(&self, args: &[&str], cwd: Option<&Path>) -> Result<String> {
        let mut cmd = Command::new("git");
        cmd.args(args);
        if let Some(cwd) = cwd {
            cmd.current_dir(cwd);
        }
        cmd.env("GIT_TERMINAL_PROMPT", "0");
        let out = cmd
            .output()
            .await
            .with_context(|| format!("无法执行 git {args:?}"))?;
        if !out.status.success() {
            bail!(
                "git {args:?} 失败: {}",
                String::from_utf8_lossy(&out.stderr).trim()
            );
        }
        Ok(String::from_utf8_lossy(&out.stdout).trim().to_string())
    }

    /// 解析数据锚点对应的 commit sha。
    /// 指定了 `commit`（或 ref 本身是 sha）时直接返回，不做引用解析。
    pub async fn resolve_sha(&self) -> Result<String> {
        if self.source.anchor_is_commit() {
            let sha = self.source.anchor().to_string();
            debug!(repo = %self.source.repo, %sha, "使用固定 commit 锚点");
            return Ok(sha);
        }
        let meta_dir = self.cache_dir.join("refs").join(sanitize_component(&self.source.repo));
        let meta_path = meta_dir.join(format!("{}.json", sanitize_component(&self.source.ref_)));
        if !self.refresh {
            if let Some(meta) = read_meta(&meta_path) {
                if super::meta_is_fresh(&meta, self.ref_ttl) {
                    if let Some(sha) = meta.extra.get("sha").and_then(|v| v.as_str()) {
                        debug!(repo = %self.source.repo, ref = %self.source.ref_, sha, "引用解析缓存命中");
                        return Ok(sha.to_string());
                    }
                }
            }
        }
        let url = format!("https://github.com/{}.git", self.source.repo);
        let out = self
            .git(&["ls-remote", &url, self.source.anchor()], None)
            .await
            .with_context(|| format!("远程解析引用失败: {url}"))?;
        if out.is_empty() {
            bail!("引用不存在: {}/{}", self.source.repo, self.source.anchor());
        }
        let sha = out.split_whitespace().next().unwrap().to_string();
        write_meta(
            &meta_path,
            &CacheMeta {
                fetched_at: now_unix(),
                extra: serde_json::json!({ "sha": sha }),
            },
        )?;
        Ok(sha)
    }

    /// 获取仓库快照目录（含 `version/`、`translation/` 子树），返回解压根路径。
    pub async fn checkout(&self) -> Result<PathBuf> {
        let sha = self.resolve_sha().await?;
        let repo_key = sanitize_component(&self.source.repo);
        let extract_dir = self.cache_dir.join("extract").join(&repo_key).join(&sha);
        let marker = extract_dir.join(".complete");
        if marker.exists() {
            debug!(source = %self.name, sha = %sha, "仓库快照缓存命中");
            return Ok(extract_dir);
        }
        let tarball_path = self.fetch_tarball(&sha).await?;
        info!(source = %self.name, repo = %self.source.repo, ref = %self.source.ref_, sha = %sha, "解压仓库快照");
        extract_needed(&tarball_path, &extract_dir)
            .with_context(|| format!("解压失败: {}", tarball_path.display()))?;
        std::fs::write(&marker, b"ok")?;
        Ok(extract_dir)
    }

    async fn fetch_tarball(&self, sha: &str) -> Result<PathBuf> {
        let repo_key = sanitize_component(&self.source.repo);
        let dir = self.cache_dir.join("tarballs").join(&repo_key);
        std::fs::create_dir_all(&dir)?;
        let path = dir.join(format!("{sha}.tar.gz"));
        if path.exists() && !self.refresh {
            return Ok(path);
        }
        let url = format!("https://codeload.github.com/{}/tar.gz/{}", self.source.repo, sha);
        let bytes = self
            .http
            .get(&url, Duration::from_secs(3600 * 24 * 365))
            .await
            .with_context(|| format!("下载 tarball 失败（{} @ {sha}）", self.source.repo))?;
        std::fs::write(&path, &bytes)?;
        Ok(path)
    }
}

/// 从 tar/tar.gz 中提取 `NEEDED_PREFIXES` 下的文件到 `dest`。
/// GitHub tarball 顶层目录为 `{repo}-{sha}/`，两者都兼容。
fn extract_needed(tarball: &Path, dest: &Path) -> Result<()> {
    let file = std::fs::File::open(tarball)?;
    let name = tarball.file_name().and_then(|n| n.to_str()).unwrap_or("");
    let inner: Box<dyn Read> = if name.ends_with(".tar.gz") {
        Box::new(GzDecoder::new(file))
    } else {
        Box::new(file)
    };
    let mut archive = tar::Archive::new(inner);
    archive.set_preserve_permissions(false);
    archive.set_unpack_xattrs(false);
    std::fs::create_dir_all(dest)?;
    for entry in archive.entries()? {
        let mut entry = entry?;
        let path = entry.path()?.to_path_buf();
        let Some(relative) = strip_top_and_match(&path) else {
            continue;
        };
        let target = dest.join(&relative);
        if entry.header().entry_type().is_dir() {
            std::fs::create_dir_all(&target)?;
            continue;
        }
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent)?;
        }
        entry.unpack(&target)?;
    }
    Ok(())
}

/// 判断条目是否属于需要的子树并返回仓库相对路径。
/// GitHub tarball 顶层有 `{repo}-{sha}/` 目录，先按原样匹配，不中则去掉顶层目录再匹配。
fn strip_top_and_match(path: &Path) -> Option<PathBuf> {
    let norm = |p: &Path| p.to_string_lossy().replace('\\', "/");
    let path_str = norm(path);
    for prefix in NEEDED_PREFIXES {
        if path_str.starts_with(prefix) {
            return Some(path.to_path_buf());
        }
    }
    let mut components = path.components();
    components.next()?;
    let rest: PathBuf = components.collect();
    let rest_str = norm(&rest);
    for prefix in NEEDED_PREFIXES {
        if rest_str.starts_with(prefix) {
            return Some(rest);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strip_top_matches() {
        assert_eq!(
            strip_top_and_match(Path::new("caidlist-f719874f/version/release/package/info.json")),
            Some(PathBuf::from("version/release/package/info.json"))
        );
        assert_eq!(
            strip_top_and_match(Path::new("version/release/package/info.json")),
            Some(PathBuf::from("version/release/package/info.json"))
        );
        assert_eq!(
            strip_top_and_match(Path::new("translation/block.json")),
            Some(PathBuf::from("translation/block.json"))
        );
        assert_eq!(strip_top_and_match(Path::new("caidlist-f719/src/index.js")), None);
        assert_eq!(strip_top_and_match(Path::new("README.md")), None);
    }
}
