//! 基岩版语言文件：从 Mojang 官方 bedrock-samples 仓库获取 `zh_CN.lang`（自行抓取）。

use crate::cache::HttpCache;
use anyhow::{Context, Result};
use indexmap::IndexMap;
use std::time::Duration;

const TTL: Duration = Duration::from_secs(3600 * 24 * 7);

const REPO: &str = "Mojang/bedrock-samples";
/// bedrock-samples 仓库中 vanilla 资源包的文本目录（目录名是单数 resource_pack）。
const LANG_PATH: &str = "resource_pack/texts/zh_CN.lang";

/// 解析 .lang 文件（对齐 caidlist `parseMinecraftLang`：
/// trimStart、在 `\t` 或 `##` 处截断、首个 `=` 分隔）。
pub fn parse_lang(text: &str) -> IndexMap<String, String> {
    let mut map = IndexMap::new();
    for line in text.split(|c| c == '\n' || c == '\r') {
        let mut l = line.trim_start();
        if let Some(pos) = l.find('\t') {
            l = &l[..pos];
        }
        if let Some(pos) = l.find("##") {
            l = &l[..pos];
        }
        if let Some(eq) = l.find('=') {
            if eq > 0 && eq < l.len() - 1 {
                map.insert(l[..eq].to_string(), l[eq + 1..].to_string());
            }
        }
    }
    map
}

/// 获取基岩版 zh_CN 语言文件。
///
/// `preferred_ref` 为主引用（release→main、beta→preview）；`version` 非空时
/// 会先尝试按版本号匹配的标签（如 `v1.21.50.7`），失败则回退主引用。
pub async fn fetch_bedrock_lang(
    http: &HttpCache,
    preferred_ref: &str,
    version: &str,
    try_version_tag: bool,
) -> Result<IndexMap<String, String>> {
    let mut candidates: Vec<String> = Vec::new();
    if try_version_tag && !version.is_empty() {
        let normalized = version
            .split('.')
            .map(|part| part.trim_start_matches('0').to_string())
            .collect::<Vec<_>>()
            .join(".");
        for v in [version.to_string(), normalized] {
            if !v.is_empty() {
                candidates.push(format!("v{v}"));
            }
        }
    }
    candidates.push(preferred_ref.to_string());
    let mut last_err = None;
    for candidate in &candidates {
        let url = format!("https://raw.githubusercontent.com/{REPO}/{candidate}/{LANG_PATH}");
        match http.get_text(&url, TTL).await {
            Ok(text) => return Ok(parse_lang(&text)),
            Err(err) => {
                tracing::info!("bedrock-samples 引用 {candidate} 不可用: {err}");
                last_err = Some(err);
            }
        }
    }
    Err(last_err.unwrap_or_else(|| anyhow::anyhow!("无法获取 bedrock-samples 语言文件")))
        .with_context(|| format!("获取 bedrock-samples zh_CN.lang 失败（尝试引用: {candidates:?}）"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_lang() {
        let text = "# comment\nkey=Value\n\tanother.tabcut=1\nwithcomment=keep ## trailing\n";
        let m = parse_lang(text);
        assert_eq!(m.get("key").unwrap(), "Value");
        assert_eq!(m.get("another.tabcut").unwrap(), "1");
        assert_eq!(m.get("withcomment").unwrap(), "keep ");
    }
}
