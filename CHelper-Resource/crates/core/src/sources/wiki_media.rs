//! Extract previews from Chinese Minecraft Wiki tables keyed by Bedrock IDs.

mod file_index;
mod sound_fallback;

use super::wiki::WIKI_API;
use crate::cache::HttpCache;
use crate::config::BedrockLangConfig;
use anyhow::{bail, Context, Result};
use reqwest::Url;
use scraper::{ElementRef, Html, Selector};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::OnceCell;
use tokio::task::JoinSet;

pub(crate) type MediaCatalog = BTreeMap<String, BTreeMap<String, String>>;
pub(crate) type MediaIds = BTreeMap<String, BTreeSet<String>>;
type MediaLinks = BTreeMap<String, String>;

const ORIGIN: &str = "https://zh.minecraft.wiki";
const SOUND_ARTICLES_PER_REQUEST: usize = 12;
const SOUND_CONCURRENT_REQUESTS: usize = 4;

pub(crate) struct MediaSources {
    http: Arc<HttpCache>,
    ttl: Duration,
    base: MediaCatalog,
    sounds: SoundLinks,
    definitions: sound_fallback::SoundPools,
    audio_titles: OnceCell<BTreeSet<String>>,
}

pub(crate) async fn fetch(http: Arc<HttpCache>, ttl: Duration) -> Result<MediaSources> {
    let (block_page, item_page, effect_page, sounds, definitions) = tokio::try_join!(
        parse(&http, &[("page", "基岩版数据值/方块ID")], ttl),
        parse(&http, &[("page", "基岩版数据值/物品ID/1.16.100后")], ttl),
        parse(&http, &[("page", "基岩版数据值")], ttl),
        fetch_sound_links(Arc::clone(&http), ttl),
        sound_fallback::fetch_definitions(&http, "main", ttl),
    )?;
    let blocks = icon_links(&block_page, "图标");
    let mut items = blocks.clone();
    let item_icons = icon_links(&item_page, "图标");
    let effects = icon_links(&effect_page, "效果");
    if blocks.is_empty() || item_icons.is_empty() || effects.is_empty() || sounds.events.is_empty()
    {
        bail!("中文 Wiki 的基岩版媒体表缺少预期的图标或音效条目");
    }
    items.extend(item_icons);
    let mut events = sounds.events.clone();
    let added = sound_fallback::fill_missing(&mut events, &sounds.files, &definitions);
    tracing::info!(added, "按基岩版音源补齐 Wiki 试听映射");
    let base = BTreeMap::from([
        ("block".into(), blocks),
        ("item".into(), items),
        ("effect".into(), effects),
        ("sound".into(), events),
    ]);
    Ok(MediaSources {
        http,
        ttl,
        base,
        sounds,
        definitions,
        audio_titles: OnceCell::new(),
    })
}

impl MediaSources {
    pub(crate) async fn for_edition(
        &self,
        edition: &str,
        version: &str,
        ids: &MediaIds,
        config: &BedrockLangConfig,
    ) -> Result<MediaCatalog> {
        let mut catalog = self.base.clone();
        let definitions = self.versioned_definitions(edition, version, config).await;
        let sound_map = catalog.get_mut("sound").expect("sound catalog");
        if let Some(definitions) = &definitions {
            let mut sounds = self.sounds.events.clone();
            sound_fallback::fill_missing(&mut sounds, &self.sounds.files, definitions);
            for (id, url) in sounds {
                sound_map.entry(id).or_insert(url);
            }
        }
        let definitions = definitions.as_ref().unwrap_or(&self.definitions);
        let published = sound_fallback::find_published_sounds(
            &self.http,
            &ids["sound"],
            sound_map,
            definitions,
            self.ttl,
        )
        .await?;
        tracing::info!(
            edition,
            version,
            added = published.len(),
            "从 Wiki 文件索引补齐声音"
        );
        sound_map.extend(published);
        let titles = self
            .audio_titles
            .get_or_try_init(|| file_index::audio_titles(&self.http, self.ttl))
            .await?;
        let additional = sound_fallback::find_indexed_sounds(
            &self.http,
            &ids["sound"],
            sound_map,
            definitions,
            titles,
            self.ttl,
        )
        .await?;
        tracing::info!(
            edition,
            version,
            added = additional.len(),
            "按 Wiki 音频索引中的实际文件名补齐声音"
        );
        sound_map.extend(additional);
        self.fill_missing_sprites(&mut catalog, ids).await?;
        Ok(catalog)
    }

    async fn versioned_definitions(
        &self,
        edition: &str,
        version: &str,
        config: &BedrockLangConfig,
    ) -> Option<sound_fallback::SoundPools> {
        let preferred = match edition {
            "beta" => &config.beta_ref,
            "netease" if !config.netease_ref.is_empty() => &config.netease_ref,
            _ => &config.release_ref,
        };
        let tag = if config.try_version_tag {
            sound_fallback::version_tag(edition, version)
        } else {
            None
        };
        for reference in tag.as_deref().into_iter().chain([preferred.as_str()]) {
            if reference == "main" {
                break;
            }
            match sound_fallback::fetch_definitions(&self.http, reference, self.ttl).await {
                Ok(definitions) => return Some(definitions),
                Err(error) => {
                    tracing::info!(reference, %error, "基岩版声音定义引用不可用，尝试备用引用")
                }
            }
        }
        None
    }

    async fn fill_missing_sprites(&self, catalog: &mut MediaCatalog, ids: &MediaIds) -> Result<()> {
        let mut titles = BTreeSet::new();
        let mut candidates = Vec::new();
        for (category, prefixes) in [
            ("block", &["BlockSprite"][..]),
            ("item", &["ItemSprite", "BlockSprite"][..]),
        ] {
            for id in &ids[category] {
                if id == "air"
                    || catalog[category].contains_key(id)
                    || id.contains(':')
                    || id.contains('/')
                {
                    continue;
                }
                let names: Vec<_> = prefixes
                    .iter()
                    .map(|prefix| format!("File:{prefix}_{}.png", id.replace('_', "-")))
                    .collect();
                titles.extend(names.iter().cloned());
                candidates.push((category, id, names));
            }
        }
        let files = file_index::find_files(&self.http, titles, self.ttl).await?;
        for (category, id, names) in candidates {
            if let Some(url) = names
                .iter()
                .filter_map(|name| files.get(name))
                .find_map(file_index::icon_url)
            {
                catalog
                    .get_mut(category)
                    .expect("icon category")
                    .insert(id.clone(), url);
            }
        }
        Ok(())
    }
}

fn api_url(parameters: &[(&str, &str)]) -> Url {
    let mut url = Url::parse(WIKI_API).expect("valid Wiki API URL");
    url.query_pairs_mut()
        .append_pair("format", "json")
        .append_pair("formatversion", "2")
        .extend_pairs(parameters.iter().copied());
    url
}

async fn parse(http: &HttpCache, parameters: &[(&str, &str)], ttl: Duration) -> Result<String> {
    let mut url = api_url(parameters);
    url.query_pairs_mut()
        .append_pair("action", "parse")
        .append_pair("prop", "text")
        .append_pair("variant", "zh-cn")
        .append_pair("disableeditsection", "1");
    let response = http.get_json(url.as_str(), ttl).await?;
    response
        .pointer("/parse/text")
        .and_then(Value::as_str)
        .map(str::to_owned)
        .with_context(|| format!("中文 Wiki 未返回解析后的页面: {}", response["error"]))
}

async fn sound_articles(http: &HttpCache, ttl: Duration) -> Result<Vec<String>> {
    let mut titles = Vec::new();
    let mut continuation = None::<(String, String)>;
    let mut seen_pages = BTreeSet::new();
    loop {
        let mut url = api_url(&[
            ("action", "query"),
            ("list", "embeddedin"),
            ("eititle", "Template:Sound table"),
            ("einamespace", "0"),
            ("eifilterredir", "nonredirects"),
            ("eilimit", "500"),
        ]);
        if let Some((token, page)) = &continuation {
            url.query_pairs_mut()
                .append_pair("continue", token)
                .append_pair("eicontinue", page);
        }
        let response = http.get_json(url.as_str(), ttl).await?;
        let entries = response
            .pointer("/query/embeddedin")
            .and_then(Value::as_array)
            .context("中文 Wiki 未返回音效表的引用条目")?;
        titles.extend(
            entries
                .iter()
                .filter_map(|row| row["title"].as_str().map(str::to_owned)),
        );
        let Some(next) = response.get("continue") else {
            break;
        };
        let token = next["continue"]
            .as_str()
            .context("音效表索引缺少 continue")?;
        let page = next["eicontinue"]
            .as_str()
            .context("音效表索引缺少 eicontinue")?;
        if !seen_pages.insert(page.to_owned()) {
            bail!("中文 Wiki 的音效表索引重复返回同一续页");
        }
        continuation = Some((token.to_owned(), page.to_owned()));
    }
    Ok(titles)
}

#[derive(Default)]
struct SoundLinks {
    events: MediaLinks,
    files: BTreeSet<String>,
}

async fn fetch_sound_links(http: Arc<HttpCache>, ttl: Duration) -> Result<SoundLinks> {
    let titles = sound_articles(&http, ttl).await?;
    tracing::info!(articles = titles.len(), "抓取中文 Wiki 基岩版音效表");
    let mut batches = titles.chunks(SOUND_ARTICLES_PER_REQUEST).enumerate();
    let mut pending = JoinSet::new();
    let mut pages = BTreeMap::new();
    loop {
        while pending.len() < SOUND_CONCURRENT_REQUESTS {
            let Some((index, titles)) = batches.next() else {
                break;
            };
            let text = titles
                .iter()
                .map(|title| format!("{{{{#lsth:{title}|音效}}}}\n"))
                .collect::<String>();
            let http = Arc::clone(&http);
            pending.spawn(async move {
                let html = parse(&http, &[("title", "基岩版声音事件"), ("text", &text)], ttl)
                    .await
                    .with_context(|| format!("读取第 {} 批 Wiki 音效表失败", index + 1))?;
                Ok::<_, anyhow::Error>((index, sound_links(&html)))
            });
        }
        let Some(result) = pending.join_next().await else {
            break;
        };
        let (index, links) = result??;
        pages.insert(index, links);
    }
    let mut sounds = SoundLinks::default();
    for page in pages.into_values() {
        for (id, url) in page.events {
            sounds.events.entry(id).or_insert(url);
        }
        sounds.files.extend(page.files);
    }
    Ok(sounds)
}

fn cell_text(cell: ElementRef<'_>) -> String {
    cell.text().collect::<String>().trim().to_owned()
}

pub(crate) fn resource_id(id: &str) -> Option<&str> {
    let id = id.trim().strip_prefix("minecraft:").unwrap_or(id.trim());
    let is_id_char = |ch: u8| ch.is_ascii_alphanumeric() || b"_.-/".contains(&ch);
    let valid_part = |part: &str| !part.is_empty() && part.bytes().all(is_id_char);
    let valid = match id.split_once(':') {
        Some((namespace, name)) => valid_part(namespace) && valid_part(name),
        None => valid_part(id),
    };
    valid.then_some(id)
}

fn file_url(src: &str, extensions: &[&str]) -> Option<String> {
    let path = src
        .strip_prefix(ORIGIN)
        .unwrap_or(src)
        .split(['?', '#'])
        .next()?;
    let file = path.strip_prefix("/images/")?;
    let file = if let Some(thumbnail) = file.strip_prefix("thumb/") {
        thumbnail.split_once('/')?.0
    } else {
        file
    };
    if file.contains('/') || !extensions.iter().any(|extension| file.ends_with(extension)) {
        return None;
    }
    // Keep existing percent escapes and encode any filename characters outside the clients' URL alphabet.
    let mut encoded = String::new();
    for ch in file.bytes() {
        if ch.is_ascii_alphanumeric() || b"_%.()'!+,~-".contains(&ch) {
            encoded.push(char::from(ch));
        } else {
            use std::fmt::Write;
            write!(&mut encoded, "%{ch:02X}").unwrap();
        }
    }
    Some(format!("{ORIGIN}/images/{encoded}"))
}

fn table_links(
    doc: &Html,
    media_column: &str,
    bedrock_sound_only: bool,
    media_url: impl Fn(ElementRef<'_>) -> Option<String>,
) -> MediaLinks {
    let tables = Selector::parse("table").unwrap();
    let rows = Selector::parse(":scope > tr, :scope > :is(thead, tbody) > tr").unwrap();
    let code = Selector::parse("code").unwrap();
    let mut links = BTreeMap::new();
    for table in doc.select(&tables) {
        let mut rows = table.select(&rows);
        let Some(header) = rows.next() else { continue };
        let headers: Vec<_> = header.child_elements().map(cell_text).collect();
        if bedrock_sound_only && !headers.iter().any(|title| title == "存档声音事件") {
            continue;
        }
        let Some(media_col) = headers.iter().position(|title| title == media_column) else {
            continue;
        };
        let Some(id_col) = headers.iter().position(|title| title == "命名空间ID") else {
            continue;
        };
        for row in rows {
            let cells: Vec<_> = row.child_elements().collect();
            if cells.len() != headers.len() {
                continue;
            }
            if let (Some(id), Some(media)) = (cells.get(id_col), cells.get(media_col)) {
                let mut ids: Vec<_> = id.select(&code).map(cell_text).collect();
                if ids.is_empty() {
                    ids.push(cell_text(*id));
                }
                for id in ids.iter().filter_map(|id| resource_id(id)) {
                    if let Some(url) = media_url(*media) {
                        links.entry(id.to_owned()).or_insert(url);
                    }
                }
            }
        }
    }
    links
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn indexes_wiki_icons_by_id_and_uses_original_file_urls() {
        let html = r#"
<table>
  <thead><tr><th>命名空间ID</th><th>图标</th></tr></thead>
  <tbody>
    <tr><td><code>minecraft:stone</code></td><td><img src="/images/thumb/Stone_JE_BE.png/25px-Stone_JE_BE.png?abcd" /></td></tr>
    <tr><td><code>minecraft:command_block</code><sup>注释</sup></td><td><img src="/images/thumb/Impulse_Command_Block.gif/25px-Impulse_Command_Block.gif?abcd" /></td></tr>
    <tr><td>warped_hyphae</td><td><img src="/images/Warped_Hyphae.webp" /></td></tr>
    <tr><td>sample:custom</td><td><img src="/images/自定义.png?abcdef" /></td></tr>
    <tr><td><code>missing</code></td><td><img src="https://example.com/images/Fake.png" /></td></tr>
    <tr><td><code>not an id</code></td><td><img src="/images/Other.png" /></td></tr>
  </tbody>
</table>
"#;
        assert_eq!(
            icon_links(html, "图标"),
            BTreeMap::from([
                (
                    "command_block".into(),
                    "https://zh.minecraft.wiki/images/Impulse_Command_Block.gif".into()
                ),
                (
                    "stone".into(),
                    "https://zh.minecraft.wiki/images/Stone_JE_BE.png".into()
                ),
                (
                    "sample:custom".into(),
                    "https://zh.minecraft.wiki/images/%E8%87%AA%E5%AE%9A%E4%B9%89.png".into()
                ),
                (
                    "warped_hyphae".into(),
                    "https://zh.minecraft.wiki/images/Warped_Hyphae.webp".into()
                ),
            ]),
        );
    }

    #[test]
    fn uses_bedrock_ids_and_direct_vorbis_or_wave_audio() {
        let html = r#"
<table>
  <tr><th>音效</th><th>命名空间ID</th><th>本地化键名</th></tr>
  <tr><td><audio><source src="/images/Java.ogg" type="audio/ogg; codecs=&quot;vorbis&quot;" /></audio></td><td>random.levelup</td><td>subtitle</td></tr>
</table>
<table>
  <tr><th>音效</th><th>命名空间ID</th><th>存档声音事件</th></tr>
  <tr><td><audio><source src="/images/transcoded/Level.ogg/Level.ogg.mp3" type="audio/mpeg" /><source src="/images/Level.ogg?key" type="audio/ogg; codecs=&quot;vorbis&quot;" /></audio></td><td><code>random<wbr />.levelup</code></td><td>levelup</td></tr>
  <tr><td><audio><source src="/images/transcoded/Step.wav/Step.wav.ogg" type="audio/ogg; codecs=&quot;opus&quot;" /><source src="/images/Step.wav" type="audio/x-wav" /></audio></td><td><code>step.stone</code><sup>基岩版注释</sup></td><td>step</td></tr>
</table>
"#;
        assert_eq!(
            sound_links(html).events,
            BTreeMap::from([
                (
                    "random.levelup".into(),
                    "https://zh.minecraft.wiki/images/Level.ogg".into()
                ),
                (
                    "step.stone".into(),
                    "https://zh.minecraft.wiki/images/Step.wav".into()
                ),
            ]),
        );
    }
}

fn icon_links(html: &str, media_column: &str) -> MediaLinks {
    let images = Selector::parse("img[src]").unwrap();
    table_links(&Html::parse_fragment(html), media_column, false, |cell| {
        cell.select(&images)
            .filter_map(|image| image.attr("src"))
            .find_map(|src| file_url(src, &[".png", ".gif", ".webp"]))
    })
}

fn sound_file(source: ElementRef<'_>) -> Option<String> {
    let src = source.attr("src")?;
    let mime = source.attr("type")?;
    if mime.starts_with("audio/ogg") && mime.contains("vorbis") {
        file_url(src, &[".ogg"])
    } else if matches!(mime, "audio/wav" | "audio/x-wav" | "audio/wave") {
        file_url(src, &[".wav"])
    } else {
        None
    }
}

fn sound_links(html: &str) -> SoundLinks {
    let doc = Html::parse_fragment(html);
    let sources = Selector::parse("audio source[src][type]").unwrap();
    SoundLinks {
        events: table_links(&doc, "音效", true, |cell| {
            cell.select(&sources).find_map(sound_file)
        }),
        files: doc.select(&sources).filter_map(sound_file).collect(),
    }
}
