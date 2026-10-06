//! Java 版语言文件（zh_cn / en_us）：Mojang piston 元数据 → client.jar + 资源索引。
//! 移植自 caidlist `src/sources/javaEdition.js`（含愚人节版本跳过表）。

use crate::cache::HttpCache;
use anyhow::{Context, Result};
use serde_json::Value;
use std::time::Duration;

const META_TTL: Duration = Duration::from_secs(60 * 60);
const IMMUTABLE_TTL: Duration = Duration::from_secs(3600 * 24 * 365);

const SKIP_VERSIONS: &[&str] = &[
    "15w14a",
    "1.RV-Pre1",
    "3D Shareware v1.34",
    "20w14infinite",
    "22w13oneblockatatime",
    "23w13a_or_b",
    "24w14potato",
    "25w14craftmine",
];

/// 语言文件集合：`zh_cn` / `en_us` 两个键值映射。
pub struct JavaLang {
    pub zh_cn: Value,
    pub en_us: Value,
}

pub async fn fetch_java_lang(http: &HttpCache, manifest_url: &str) -> Result<JavaLang> {
    let manifest = http.get_json(manifest_url, META_TTL).await?;
    let latest_snapshot = manifest
        .pointer("/latest/snapshot")
        .and_then(|v| v.as_str())
        .context("版本清单缺少 latest.snapshot")?
        .to_string();
    let version_id = if SKIP_VERSIONS.contains(&latest_snapshot.as_str()) {
        // 跳过愚人节版本，取发布时间最新的版本
        let versions = manifest
            .get("versions")
            .and_then(|v| v.as_array())
            .context("版本清单缺少 versions")?;
        let mut best: Option<(&Value, String)> = None;
        for v in versions {
            let id = v.get("id").and_then(|x| x.as_str()).unwrap_or("");
            if SKIP_VERSIONS.contains(&id) {
                continue;
            }
            let release_time = v.get("releaseTime").and_then(|x| x.as_str()).unwrap_or("");
            let better = match &best {
                None => true,
                Some((_, best_time)) => release_time.as_bytes() > best_time.as_bytes(),
            };
            if better {
                best = Some((v, release_time.to_string()));
            }
        }
        best.map(|(v, _)| v.get("id").and_then(|x| x.as_str()).unwrap_or("").to_string())
            .filter(|s| !s.is_empty())
            .context("版本清单为空")?
    } else {
        latest_snapshot
    };

    let version_entry = manifest
        .get("versions")
        .and_then(|v| v.as_array())
        .and_then(|arr| {
            arr.iter()
                .find(|v| v.get("id").and_then(|x| x.as_str()) == Some(version_id.as_str()))
                .cloned()
        })
        .context("版本清单中找不到目标版本")?;
    let version_meta_url = version_entry
        .get("url")
        .and_then(|v| v.as_str())
        .context("版本条目缺少 url")?;
    let meta = http.get_json(version_meta_url, META_TTL).await?;

    let client_url = meta
        .pointer("/downloads/client/url")
        .and_then(|v| v.as_str())
        .context("版本元数据缺少 client 下载地址")?;
    let client_sha1 = meta
        .pointer("/downloads/client/sha1")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    let asset_index_url = meta
        .pointer("/assetIndex/url")
        .and_then(|v| v.as_str())
        .context("版本元数据缺少 assetIndex 地址")?;

    let (en_us_raw, zh_cn_raw) = tokio::try_join!(
        fetch_en_us(http, client_url, client_sha1),
        fetch_zh_cn(http, asset_index_url)
    )?;
    let en_us: Value = serde_json::from_str(&en_us_raw).context("解析 en_us.json 失败")?;
    let zh_cn: Value = serde_json::from_str(&zh_cn_raw).context("解析 zh_cn.json 失败")?;
    Ok(JavaLang { zh_cn, en_us })
}

/// 下载 client.jar（按 sha1 缓存）并抽取 `assets/minecraft/lang/en_us.json`。
async fn fetch_en_us(http: &HttpCache, jar_url: &str, sha1: &str) -> Result<String> {
    let jar_bytes = http.get_checked(jar_url, sha1, IMMUTABLE_TTL).await?;
    let cursor = std::io::Cursor::new(jar_bytes);
    let mut archive = zip::ZipArchive::new(cursor).context("打开 client.jar 失败")?;
    let mut entry = archive
        .by_name("assets/minecraft/lang/en_us.json")
        .context("client.jar 中找不到 en_us.json")?;
    let mut buf = String::new();
    std::io::Read::read_to_string(&mut entry, &mut buf)?;
    Ok(buf)
}

/// 通过资源索引下载 `minecraft/lang/zh_cn.json`。
async fn fetch_zh_cn(http: &HttpCache, asset_index_url: &str) -> Result<String> {
    let asset_index = http.get_json(asset_index_url, META_TTL).await?;
    // JSON Pointer 中 `/` 需转义为 `~1`
    let mut hash = asset_index
        .pointer("/objects/minecraft~1lang~1zh_cn.json/hash")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string());
    if hash.is_none() {
        // 兜底：遍历寻找 lang/zh_cn.json
        hash = asset_index
            .get("objects")
            .and_then(|v| v.as_object())
            .and_then(|objects| {
                objects
                    .iter()
                    .find(|(k, _)| k.ends_with("lang/zh_cn.json"))
                    .and_then(|(_, v)| v.pointer("/hash").and_then(|h| h.as_str()))
                    .map(|s| s.to_string())
            });
    }
    let hash = hash.context("资源索引缺少 zh_cn.json")?;
    let asset_url = format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash);
    http.get_text(&asset_url, IMMUTABLE_TTL).await
}
