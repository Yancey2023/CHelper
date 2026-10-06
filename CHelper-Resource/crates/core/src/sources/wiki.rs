//! 中文 Minecraft Wiki 数据抓取（全部自行实现，不复用 caidlist 已抓取的缓存文件）。
//!
//! - 标准译名表（ST）：`Module:Autolink/*`，通过 Scribunto 控制台执行 `mw.text.jsonEncode(p)`
//!   获取模块数据，并调用 `Module:ZhConversion` 完成简繁转换（移植 `src/sources/wiki.js`）；
//! - 游戏规则类型表：`Module:Gamerule_type_values_BE`（LSON）；
//! - 方块属性描述：`Module:Block_property_descriptions_BE`（LSON）；
//! - 游戏规则 / 基岩版粒子描述：解析 `?action=raw` 的 wikitext（移植 `update_translation.py`）。

use crate::cache::HttpCache;
use crate::sources::lson::parse_lson;
use anyhow::{bail, Context, Result};
use indexmap::IndexMap;
use regex::Regex;
use serde_json::Value;
use std::sync::OnceLock;
use std::time::Duration;

const WIKI_API: &str = "https://zh.minecraft.wiki/api.php";
const WIKI_PAGE: &str = "https://zh.minecraft.wiki/w/";
/// 缓存 TTL：1 小时（与 caidlist wiki.js 一致）。
const TTL: Duration = Duration::from_secs(60 * 60);

fn urlencode(s: &str) -> String {
    let mut out = String::new();
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(b as char),
            other => out.push_str(&format!("%{other:02X}")),
        }
    }
    out
}

fn raw_url(title: &str) -> String {
    format!("{WIKI_PAGE}{}?action=raw", urlencode(title))
}

/// Scribunto 控制台客户端。
struct Scribunto<'a> {
    http: &'a HttpCache,
}

impl<'a> Scribunto<'a> {
    fn new(http: &'a HttpCache) -> Self {
        Self { http }
    }

    async fn raw(&self, title: &str) -> Result<String> {
        self.http.get_text(&raw_url(title), TTL).await
    }

    async fn csrf_token(&self) -> Result<String> {
        let url = format!("{WIKI_API}?action=query&meta=tokens&format=json");
        let json = self.http.get_json(&url, Duration::from_secs(3600)).await?;
        json.pointer("/query/tokens/csrftoken")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .ok_or_else(|| anyhow::anyhow!("获取 CSRF token 失败"))
    }

    async fn exec_console(&self, title: &str, content: &str, question: &str, token: &str) -> Result<String> {
        let form = [
            ("action", "scribunto-console"),
            ("title", title),
            ("content", content),
            ("question", question),
            ("clear", "true"),
            ("token", token),
            ("format", "json"),
        ];
        let resp = self.http.post_form(WIKI_API, &form).await?;
        let json: Value = serde_json::from_str(&resp).context("Scribunto 控制台响应不是 JSON")?;
        if json.get("type").and_then(|v| v.as_str()) == Some("error") {
            bail!(
                "Scribunto 控制台错误: {}",
                json.get("message").and_then(|v| v.as_str()).unwrap_or("?")
            );
        }
        json.get("return")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .ok_or_else(|| anyhow::anyhow!("Scribunto 控制台响应缺少 return"))
    }
}

fn cached_regex(cell: &'static OnceLock<Regex>, pattern: &str) -> &'static Regex {
    cell.get_or_init(|| Regex::new(pattern).unwrap())
}

/// 复刻 wiki.js 的 `simplify`：rawKey 重命名、数字键转数组（1 基）、对象键排序。
fn simplify(value: &Value) -> Value {
    match value {
        Value::Array(items) => Value::Array(items.iter().map(simplify).collect()),
        Value::Object(obj) => {
            let mut new_obj: Vec<(String, Value)> = Vec::new();
            for (key, value) in obj {
                if key == "rawKey" || key == "keyNoLow" {
                    continue;
                }
                if let Value::Object(inner) = value {
                    if let Some(raw_key) = inner.get("rawKey").and_then(|v| v.as_str()) {
                        new_obj.push((raw_key.to_string(), simplify(value)));
                        continue;
                    }
                }
                new_obj.push((key.clone(), simplify(value)));
            }
            let has_numeric = new_obj.iter().any(|(k, _)| k.parse::<usize>().is_ok());
            if has_numeric {
                let mut arr: Vec<Option<Value>> = Vec::new();
                for (k, v) in &new_obj {
                    if let Ok(n) = k.parse::<usize>() {
                        if n >= 1 {
                            if arr.len() < n {
                                arr.resize(n, None);
                            }
                            arr[n - 1] = Some(v.clone());
                            continue;
                        }
                    }
                    // JS 中数组的字符串键会被 JSON.stringify 丢弃
                }
                return Value::Array(arr.into_iter().map(|v| v.unwrap_or(Value::Null)).collect());
            }
            new_obj.sort_by(|a, b| a.0.cmp(&b.0));
            Value::Object(new_obj.into_iter().collect())
        }
        other => other.clone(),
    }
}

/// 复刻 `postprocessEnumMap`：`[译名, hidden]` 取译名；`a|b` 取 b；`-{...}-` 取内部。
/// （与 JS 不同：隐藏条目不删除——caidlist 的 restoreHiddenEntries 会把它们加回来。）
fn postprocess_enum_map(maps: &mut Value) {
    let Value::Object(obj) = maps else { return };
    for (_, submap) in obj.iter_mut() {
        let Value::Object(entries) = submap else { continue };
        for (_, v) in entries.iter_mut() {
            let mut taken = std::mem::take(v);
            if let Value::Array(items) = &taken {
                if !items.is_empty() {
                    taken = items[0].clone();
                }
            }
            if let Value::String(s) = &taken {
                let mut s = s.clone();
                if let Some(pos) = s.rfind('|') {
                    s = s[pos + 1..].to_string();
                }
                // JS: v.replace(/-\{(.*?)\}-/, '$1') —— 只替换第一处
                static DASH_CURLY: OnceLock<Regex> = OnceLock::new();
                let re = cached_regex(&DASH_CURLY, r"-\{(.*?)\}-");
                s = re.replace(&s, "$1").to_string();
                taken = Value::String(s);
            }
            *v = taken;
        }
    }
}

/// 标准译名表：`{ BlockSprite: {...}, ItemSprite: {...}, ..., Exclusive*: {...} }`。
pub async fn fetch_standardized_translation(http: &HttpCache) -> Result<Value> {
    let pages: &[(&str, &str)] = &[
        ("Module:Autolink/Block", "BlockSprite"),
        ("Module:Autolink/Item", "ItemSprite"),
        ("Module:Autolink/Entity", "EntitySprite"),
        ("Module:Autolink/Biome", "BiomeSprite"),
        ("Module:Autolink/Effect", "EffectSprite"),
        ("Module:Autolink/Enchantment", "EnchantmentSprite"),
        ("Module:Autolink/Environment", "EnvSprite"),
        ("Module:Autolink/Other", "Other"),
        // Autolink/Exclusive 的键加 Exclusive 前缀平铺（ExclusiveBlockSprite 等）
        ("Module:Autolink/Exclusive", ""),
    ];
    let scribunto = Scribunto::new(http);
    let token = scribunto.csrf_token().await?;
    let mut result = serde_json::Map::new();
    for (title, target) in pages {
        let content = scribunto.raw(title).await?;
        let ret = scribunto
            .exec_console(title, &content, "=mw.text.jsonEncode(p)", &token)
            .await
            .with_context(|| format!("执行 Scribunto 控制台失败: {title}"))?;
        let parsed: Value =
            serde_json::from_str(ret.trim()).with_context(|| format!("解析模块数据失败: {title}"))?;
        let simplified = simplify(&parsed);
        if !target.is_empty() {
            result.insert(target.to_string(), simplified);
        } else if let Value::Object(obj) = simplified {
            for (k, v) in obj {
                result.insert(format!("Exclusive{k}"), v);
            }
        }
    }
    let mut value = Value::Object(result);
    convert_to_simplified_chinese(&scribunto, &mut value).await?;
    postprocess_enum_map(&mut value);
    Ok(value)
}

/// 收集对象中所有字符串并批量调用 `Module:ZhConversion` 的 `to_cn`。
async fn convert_to_simplified_chinese(scribunto: &Scribunto<'_>, value: &mut Value) -> Result<()> {
    let mut unique: Vec<String> = Vec::new();
    collect_strings(value, &mut unique);
    if unique.is_empty() {
        return Ok(());
    }
    let title = "Module:Autolink";
    // 转换函数只依赖 Module:ZhConversion；content 用任一可读模块页即可
    let content = scribunto.raw(title).await.unwrap_or_else(|_| "return {}".to_string());
    let token = scribunto.csrf_token().await?;
    let func = "function(input) local zhconv = require('Module:ZhConversion'); local result = {}; \
        for i, item in ipairs(input) do result[i] = zhconv.to_cn(item); end return result; end";
    let input = unique
        .iter()
        .map(|s| serde_json::to_string(s).unwrap())
        .collect::<Vec<_>>()
        .join(",");
    let question = format!("=mw.text.jsonEncode(({func})({{{input}}}))");
    let ret = scribunto
        .exec_console(title, &content, &question, &token)
        .await
        .context("执行简繁转换失败")?;
    let converted: Vec<String> = serde_json::from_str(ret.trim()).context("解析简繁转换结果失败")?;
    if converted.len() != unique.len() {
        bail!("简繁转换结果数量不匹配: {} vs {}", converted.len(), unique.len());
    }
    let mapping: IndexMap<String, String> = unique.into_iter().zip(converted).collect();
    replace_strings(value, &mapping);
    Ok(())
}

fn collect_strings(value: &Value, out: &mut Vec<String>) {
    match value {
        Value::String(s) => {
            if !out.contains(s) {
                out.push(s.clone());
            }
        }
        Value::Array(items) => items.iter().for_each(|v| collect_strings(v, out)),
        Value::Object(obj) => obj.values().for_each(|v| collect_strings(v, out)),
        _ => {}
    }
}

fn replace_strings(value: &mut Value, mapping: &IndexMap<String, String>) {
    match value {
        Value::String(s) => {
            if let Some(new) = mapping.get(s) {
                *s = new.clone();
            }
        }
        Value::Array(items) => items.iter_mut().for_each(|v| replace_strings(v, mapping)),
        Value::Object(obj) => obj.values_mut().for_each(|v| replace_strings(v, mapping)),
        _ => {}
    }
}

/// LSON 模块（`return {...}`）→ JSON。用于 gamerule 类型表与方块属性描述。
async fn fetch_lson_module(http: &HttpCache, title: &str) -> Result<Value> {
    let scribunto = Scribunto::new(http);
    let content = scribunto.raw(title).await?;
    let return_pos = content
        .find("return")
        .ok_or_else(|| anyhow::anyhow!("{title} 中找不到 return"))?;
    let expr = &content[return_pos + "return".len()..];
    parse_lson(expr).with_context(|| format!("解析 LSON 失败: {title}"))
}

/// 游戏规则类型表：camelCase 名称 → "int" | "bool"。
pub async fn fetch_gamerule_type_value(http: &HttpCache) -> Result<IndexMap<String, String>> {
    let value = fetch_lson_module(http, "Module:Gamerule_type_values_BE").await?;
    let Value::Object(obj) = value else { bail!("Gamerule_type_values_BE 不是对象") };
    let mut map = IndexMap::new();
    for (k, v) in obj {
        if let Some(s) = v.as_str() {
            map.insert(k, s.to_string());
        }
    }
    Ok(map)
}

/// 方块属性描述（`{common: {...}, block: [[blocks, props], ...]}`）。
pub async fn fetch_block_property_descriptions(http: &HttpCache) -> Result<Value> {
    fetch_lson_module(http, "Module:Block_property_descriptions_BE").await
}

// ---------------------------------------------------------------------------
// wikitext 页面解析（移植 update_translation.py）
// ---------------------------------------------------------------------------

/// 移除 `[[链接|别名]]`，保留别名（无 | 时保留链接文本）。
fn remove_reference(text: &str) -> Result<String> {
    let mut result = String::new();
    let mut current = 0usize;
    loop {
        let Some(index1) = text[current..].find("[[").map(|i| i + current) else {
            break;
        };
        result.push_str(&text[current..index1]);
        let Some(index2) = text[index1..].find("]]").map(|i| i + index1) else {
            bail!("Invalid tag (unclosed [[): {text}");
        };
        current = index2 + 2;
        match text[index1..index2].find('|').map(|i| i + index1) {
            None => result.push_str(&text[index1 + 2..index2]),
            Some(index3) => result.push_str(&text[index3 + 1..index2]),
        }
    }
    result.push_str(&text[current..]);
    Ok(result)
}

/// 处理 `{{模板}}`：tr/cmd/key/cd 取参数 1；only for=xx 与 ee 有特殊处理；info needed 等删除。
fn remove_tag(text: &str) -> Result<String> {
    let mut result = String::new();
    let mut current = 0usize;
    loop {
        let Some(index1) = text[current..].find("{{").map(|i| i + current) else {
            break;
        };
        result.push_str(&text[current..index1]);
        let Some(index2) = text[index1..].find("}}").map(|i| i + index1) else {
            bail!("Invalid tag (unclosed {{): {text}");
        };
        current = index2 + 2;
        let contents: Vec<&str> = text[index1 + 2..index2].split('|').collect();
        match contents[0] {
            "info needed" | "bug" | "Citation needed" => {}
            "tr" | "cmd" | "key" | "cd" => {
                if let Some(c) = contents.get(1) {
                    result.push_str(c);
                }
            }
            "only" => {
                for content in &contents[1..] {
                    if let Some(rest) = content.strip_prefix("for=") {
                        result.push_str(rest);
                        break;
                    }
                }
                for content in &contents[1..] {
                    if *content == "ee" {
                        result.push_str("（仅教育版）");
                        break;
                    }
                }
            }
            other => bail!("unknown tag: {other}"),
        }
    }
    result.push_str(&text[current..]);
    Ok(result)
}

/// 移除 `<ref ...>...</ref>` / `<ref ... />` 与 `<code>` 标签。
fn remove_html(text: &str) -> String {
    static REF_RE: OnceLock<Regex> = OnceLock::new();
    static CODE_RE: OnceLock<Regex> = OnceLock::new();
    let ref_re = cached_regex(&REF_RE, r"(?s)<ref\b[^>]*>.*?</ref>|<ref\b[^>]*?/?>");
    let code_re = cached_regex(&CODE_RE, r"(?s)</?code\b[^>]*?/?>");
    code_re.replace_all(&ref_re.replace_all(text, ""), "").to_string()
}

/// 游戏规则描述（小写 ID → 中文描述），移植 `update_translation.py::game_rule`。
pub async fn fetch_gamerule_descriptions(http: &HttpCache) -> Result<IndexMap<String, String>> {
    let url = raw_url("游戏规则");
    let content = http.get_text(&url, TTL).await?;
    let mut result = IndexMap::new();
    for row in content.split('\n') {
        if row.starts_with("|{{") || row.ends_with("}}") || !row.starts_with('|') {
            continue;
        }
        let Some(index) = row[1..].find('=') else {
            continue;
        };
        let mut name = row[1..index + 1].to_lowercase();
        if name.ends_with("-je") {
            continue;
        } else if name.ends_with("-be") {
            name.truncate(name.len() - 3);
        }
        let description = &row[index + 2..];
        let description = remove_reference(description)?;
        let description = remove_tag(&description)?;
        let description = remove_html(&description);
        result.insert(name, description);
    }
    Ok(result)
}

/// 基岩版粒子描述（`minecraft:xxx` → 中文描述），移植 `update_translation.py::particle`。
pub async fn fetch_particle_descriptions(http: &HttpCache) -> Result<IndexMap<String, String>> {
    let url = raw_url("基岩版粒子");
    let content = http.get_text(&url, TTL).await?;
    let rows: Vec<&str> = content.split('\n').collect();
    let mut result = IndexMap::new();
    let mut row_index = 0usize;
    while row_index < rows.len() {
        if !rows[row_index].starts_with("|-") {
            row_index += 1;
            continue;
        }
        if row_index + 2 >= rows.len() {
            break;
        }
        let name: String = rows[row_index + 1].chars().skip(1).collect();
        let name = format!("minecraft:{}", remove_html(&name));
        let description: String = rows[row_index + 2].chars().skip(1).collect();
        let description = remove_reference(&description)?;
        let description = remove_tag(&description)?;
        let mut description = remove_html(&description);
        description = description
            .replace("。''该粒子只能在空气中生成和存在。''", "（该粒子只能在空气中生成和存在）")
            .replace("。''该粒子只能在水中生成和存在。''", "（该粒子只能在水中生成和存在）")
            .replace("。该粒子只能在水中生成和存在。", "（该粒子只能在水中生成和存在）");
        result.insert(name, description);
        row_index += 1;
    }
    Ok(result)
}
