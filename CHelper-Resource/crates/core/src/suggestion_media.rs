//! Write the Wiki preview links applicable to each resource pack.

use crate::cache::HttpCache;
use crate::config::Config;
use crate::jfmt::to_string_pretty4;
use crate::sources::wiki_media::{self, resource_id, MediaCatalog, MediaIds, MediaSources};
use crate::write_crlf;
use anyhow::{bail, Context, Result};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

const FILENAME: &str = "suggestion_media.json";
const BRANCHES: [&str; 2] = ["vanilla", "experiment"];
const CATEGORIES: [&str; 4] = ["block", "item", "effect", "sound"];

pub(crate) async fn fetch(
    config: &Config,
    http: Arc<HttpCache>,
    refresh: bool,
) -> Result<MediaSources> {
    let hours = if refresh { 0 } else { config.http_ttl_hours };
    wiki_media::fetch(http, Duration::from_secs(hours.saturating_mul(3600))).await
}

pub(crate) async fn write_for_edition(
    config: &Config,
    root: &Path,
    edition: &str,
    sources: &MediaSources,
) -> Result<()> {
    for file in prepare_for_edition(config, root, edition, sources).await? {
        write_crlf(&file.path, &file.text)?;
    }
    Ok(())
}

pub async fn refresh_bundled(
    config: &Config,
    editions: &[String],
    refresh: bool,
) -> Result<BTreeMap<String, usize>> {
    if let Some(edition) = editions
        .iter()
        .find(|name| !matches!(name.as_str(), "release" | "beta" | "netease"))
    {
        bail!("未知资源包版本: {edition}");
    }
    let sources = fetch(
        config,
        Arc::new(HttpCache::new(&config.cache_dir)?),
        refresh,
    )
    .await?;
    let mut files = Vec::new();
    for edition in editions {
        let root = config.project_root.join("resources").join(edition);
        files.extend(prepare_for_edition(config, &root, edition, &sources).await?);
    }
    let mut report = BTreeMap::new();
    for file in files {
        write_crlf(&file.path, &file.text)?;
        report.insert(file.key, file.count);
    }
    Ok(report)
}

struct PreparedMedia {
    key: String,
    path: PathBuf,
    text: String,
    count: usize,
}

async fn prepare_for_edition(
    config: &Config,
    root: &Path,
    edition: &str,
    sources: &MediaSources,
) -> Result<Vec<PreparedMedia>> {
    let manifest = root.join("vanilla/manifest.json");
    let doc: Value = serde_json::from_str(&std::fs::read_to_string(&manifest)?)
        .with_context(|| format!("解析 {} 失败", manifest.display()))?;
    let version = doc["version"].as_str().context("资源包缺少 version")?;
    let mut all_ids = MediaIds::new();
    let mut branches = Vec::new();
    for branch in BRANCHES {
        let branch_root = root.join(branch);
        let ids = read_pack_ids(&branch_root)
            .with_context(|| format!("读取 [{edition}/{branch}] 补全媒体 ID 失败"))?;
        for (category, names) in &ids {
            all_ids
                .entry(category.clone())
                .or_default()
                .extend(names.iter().cloned());
        }
        branches.push((branch, branch_root, ids));
    }
    let catalog = sources
        .for_edition(edition, version, &all_ids, &config.bedrock_lang)
        .await?;
    branches
        .into_iter()
        .map(|(branch, branch_root, ids)| {
            let (text, count) = render_for_pack(&ids, &catalog)?;
            Ok(PreparedMedia {
                key: format!("{edition}/{branch}"),
                path: branch_root.join(FILENAME),
                text,
                count,
            })
        })
        .collect()
}

fn read_pack_ids(root: &Path) -> Result<MediaIds> {
    let mut pack_ids = MediaIds::new();
    for category in CATEGORIES {
        let mut names = BTreeSet::new();
        let id_files: &[&str] = if category == "sound" {
            &["sound", "music"]
        } else {
            &[category]
        };
        for file in id_files {
            let path = root.join("id").join(format!("{file}.json"));
            let text = match std::fs::read_to_string(&path) {
                Ok(text) => text,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound && *file == "music" => {
                    continue
                }
                Err(error) => {
                    return Err(error).with_context(|| format!("读取 {} 失败", path.display()))
                }
            };
            let doc: Value = serde_json::from_str(&text)
                .with_context(|| format!("解析 {} 失败", path.display()))?;
            let content = if *file == "block" {
                &doc["content"]["blockStateValues"]
            } else {
                &doc["content"]
            };
            let ids = content
                .as_array()
                .with_context(|| format!("{} 缺少 ID 列表", path.display()))?;
            for entry in ids {
                if let Some(id) = entry["name"].as_str().and_then(resource_id) {
                    let namespace = entry["idNamespace"].as_str();
                    let key = match namespace {
                        Some(ns) if ns != "minecraft" && !id.contains(':') => format!("{ns}:{id}"),
                        _ => id.to_owned(),
                    };
                    names.insert(key);
                }
            }
        }
        pack_ids.insert(category.to_owned(), names);
    }
    Ok(pack_ids)
}

fn render_for_pack(ids: &MediaIds, catalog: &MediaCatalog) -> Result<(String, usize)> {
    let mut media = BTreeMap::new();
    let mut count = 0;
    for (category, urls) in catalog {
        let links: BTreeMap<_, _> = ids[category]
            .iter()
            .filter_map(|key| urls.get(key).map(|url| (key, url)))
            .collect();
        count += links.len();
        media.insert(category, links);
    }
    Ok((to_string_pretty4(&serde_json::to_value(media)?), count))
}
