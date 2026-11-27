use super::{api_url, file_url};
use crate::cache::HttpCache;
use anyhow::{Context, Result};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;
use std::time::Duration;
use tokio::task::JoinSet;

const TITLES_PER_REQUEST: usize = 40;
const CONCURRENT_REQUESTS: usize = 4;

pub(super) async fn audio_titles(http: &HttpCache, ttl: Duration) -> Result<BTreeSet<String>> {
    let (wave, ogg) = tokio::try_join!(
        list_audio(http, "audio/wav|audio/x-wav|audio/wave|audio/vnd.wave", ttl),
        list_audio(http, "application/ogg|audio/ogg", ttl),
    )?;
    Ok(wave.into_iter().chain(ogg).collect())
}

async fn list_audio(http: &HttpCache, mime: &str, ttl: Duration) -> Result<BTreeSet<String>> {
    let mut titles = BTreeSet::new();
    let mut continuation = None::<(String, String)>;
    let mut seen = BTreeSet::new();
    loop {
        let mut url = api_url(&[
            ("action", "query"),
            ("list", "allimages"),
            ("aimime", mime),
            ("aiprop", "mime"),
            ("ailimit", "500"),
        ]);
        if let Some((token, start)) = &continuation {
            url.query_pairs_mut()
                .append_pair("continue", token)
                .append_pair("aicontinue", start);
        }
        let data = http.get_json(url.as_str(), ttl).await?;
        let items = data["query"]["allimages"]
            .as_array()
            .context("Wiki 音频索引缺少文件列表")?;
        for item in items {
            let title = item["title"].as_str().context("Wiki 音频索引缺少文件名")?;
            titles.insert(title.to_owned());
        }
        let Some(next) = data.get("continue") else {
            return Ok(titles);
        };
        let token = next["continue"]
            .as_str()
            .context("Wiki 音频索引缺少续页标记")?;
        let start = next["aicontinue"]
            .as_str()
            .context("Wiki 音频索引缺少下一页文件名")?;
        anyhow::ensure!(seen.insert(start.to_owned()), "Wiki 音频索引重复返回同一页");
        continuation = Some((token.to_owned(), start.to_owned()));
    }
}

pub(super) async fn find_files(
    http: &Arc<HttpCache>,
    titles: BTreeSet<String>,
    ttl: Duration,
) -> Result<BTreeMap<String, Value>> {
    let titles: Vec<_> = titles.into_iter().collect();
    let mut batches = titles.chunks(TITLES_PER_REQUEST);
    let mut tasks = JoinSet::new();
    let mut files = BTreeMap::new();
    loop {
        while tasks.len() < CONCURRENT_REQUESTS {
            let Some(batch) = batches.next() else { break };
            let http = Arc::clone(http);
            let batch = batch.to_vec();
            tasks.spawn(async move {
                let titles = batch.join("|");
                let url = api_url(&[
                    ("action", "query"),
                    ("prop", "imageinfo"),
                    ("iiprop", "url|mime|metadata"),
                    ("redirects", "1"),
                    ("titles", &titles),
                ]);
                let response = http.get_json(url.as_str(), ttl).await?;
                files_by_title(&response, &batch)
            });
        }
        let Some(result) = tasks.join_next().await else {
            break;
        };
        files.extend(result??);
    }
    Ok(files)
}

fn files_by_title(response: &Value, requested: &[String]) -> Result<BTreeMap<String, Value>> {
    let query = &response["query"];
    let pages = query["pages"]
        .as_array()
        .with_context(|| format!("Wiki 文件查询未返回页面: {}", response["error"]))?;
    let aliases: BTreeMap<_, _> = ["normalized", "redirects"]
        .into_iter()
        .flat_map(|key| query[key].as_array().into_iter().flatten())
        .filter_map(|alias| Some((alias["from"].as_str()?, alias["to"].as_str()?)))
        .collect();
    let index: BTreeMap<_, _> = pages
        .iter()
        .filter_map(|page| {
            Some((
                page["title"].as_str()?,
                page["imageinfo"].as_array()?.first()?,
            ))
        })
        .collect();
    Ok(requested
        .iter()
        .filter_map(|title| {
            let mut resolved = title.as_str();
            for _ in 0..aliases.len() {
                let Some(next) = aliases.get(resolved) else {
                    break;
                };
                resolved = next;
            }
            Some((title.clone(), (*index.get(resolved)?).clone()))
        })
        .collect())
}

pub(super) fn icon_url(info: &Value) -> Option<String> {
    let extension = match info["mime"].as_str()? {
        "image/png" => ".png",
        "image/gif" => ".gif",
        "image/webp" => ".webp",
        _ => return None,
    };
    file_url(info["url"].as_str()?, &[extension])
}

pub(super) fn sound_url(info: &Value) -> Option<String> {
    let extension = match info["mime"].as_str()? {
        "audio/wav" | "audio/x-wav" | "audio/wave" | "audio/vnd.wave" => ".wav",
        "audio/ogg" | "application/ogg" if has_vorbis(&info["metadata"]) => ".ogg",
        _ => return None,
    };
    file_url(info["url"].as_str()?, &[extension])
}

fn has_vorbis(metadata: &Value) -> bool {
    metadata.as_array().into_iter().flatten().any(|entry| {
        entry["name"] == "streams"
            && entry["value"]
                .as_array()
                .into_iter()
                .flatten()
                .any(|stream| {
                    stream["value"]
                        .as_array()
                        .into_iter()
                        .flatten()
                        .any(|field| field["name"] == "type" && field["value"] == "Vorbis")
                })
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn resolves_wiki_file_redirects_and_rejects_unsupported_audio_codecs() {
        let requested = ["File:BlockSprite_chain.png".to_owned()];
        let api = json!({"query": {
            "normalized": [{"from": "File:BlockSprite_chain.png", "to": "File:BlockSprite chain.png"}],
            "redirects": [{"from": "File:BlockSprite chain.png", "to": "File:BlockSprite iron-chain.png"}],
            "pages": [{"title": "File:BlockSprite iron-chain.png", "imageinfo": [{"mime": "image/png", "url": "https://zh.minecraft.wiki/images/BlockSprite_iron-chain.png?v"}]}]
        }});
        let files = files_by_title(&api, &requested).unwrap();
        assert_eq!(
            icon_url(&files[&requested[0]]).as_deref(),
            Some("https://zh.minecraft.wiki/images/BlockSprite_iron-chain.png")
        );
        let ogg = |codec| json!({"mime": "application/ogg", "url": "https://zh.minecraft.wiki/images/Known.ogg", "metadata": [{"name": "streams", "value": [{"value": [{"name": "type", "value": codec}]}]}]});
        assert!(sound_url(&ogg("Vorbis")).is_some());
        assert!(sound_url(&ogg("Opus")).is_none());
    }
}
