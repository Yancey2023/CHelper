//! chelper 资源包输出（逐行对齐 caidlist `new_chelper` 分支的 `src/generators/chelper.js`）。
//!
//! 格式契约：4 空格缩进、CRLF 行尾、文件末尾无换行、`description`/`properties`/`valid`
//! 等为空时整个字段省略（对齐 JS `undefined` 字段不序列化）、键顺序即构造顺序。

use crate::jfmt::to_string_pretty4;
use crate::translate::ResultMaps;
use crate::write_crlf;
use anyhow::Result;
use indexmap::IndexMap;
use regex::Regex;
use serde_json::{json, Map, Value};
use std::path::Path;
use std::sync::OnceLock;

/// chelper 输出需要的全部输入。
pub struct ChelperInput<'a> {
    /// 翻译结果表（含 `lootTableWrapped` 等中间键）。
    pub result_maps: &'a ResultMaps,
    /// wiki 游戏规则类型表（camelCase → int|bool）。
    pub gamerule_type: &'a IndexMap<String, String>,
    /// wiki 方块属性描述（`{common, block}`）。
    pub block_property_descriptions: &'a Value,
    /// Script API 分析数据（`version/beta/gametest/all.json`）。
    pub gametest: &'a Value,
    /// 药水描述（47 条，data/potion_descriptions.json）。
    pub potion_descriptions: &'a Value,
    /// 手工维护的游戏模式内容（data/game_mode.json 的 content 数组）。
    pub game_mode: &'a Value,
    /// wiki 粒子描述（`minecraft:xxx` → 描述）。
    pub particle_emitter: &'a IndexMap<String, String>,
}

/// chelper.js 的键过滤与改名。
const EXCLUDE: &[&str] = &[
    "ability",
    "command",
    "blockState",
    "lootTable",
    "lootTool",
    "summonableEntity",
];
const RENAME: &[(&str, &str)] = &[
    ("enchant", "enchantType"),
    ("location", "structure"),
    ("gamerule", "gameRule"),
    ("lootTableWrapped", "lootTable"),
];

fn rename_maps(maps: &ResultMaps) -> IndexMap<String, &IndexMap<String, String>> {
    let mut renamed = IndexMap::new();
    for (key, value) in maps {
        if EXCLUDE.contains(&key.as_str()) {
            continue;
        }
        let mut new_key = key.clone();
        for (from, to) in RENAME {
            if *key == *from {
                new_key = to.to_string();
            }
        }
        renamed.insert(new_key, value);
    }
    renamed
}

fn content_array(map: &IndexMap<String, String>) -> Value {
    let entries: Vec<Value> = map
        .iter()
        .map(|(name, description)| {
            let mut obj = Map::new();
            obj.insert("name".into(), json!(name));
            if !description.is_empty() {
                obj.insert("description".into(), json!(description));
            }
            Value::Object(obj)
        })
        .collect();
    json!(entries)
}

fn parse_wiki_text(text: &str) -> String {
    static TEMPLATE_RE: OnceLock<Regex> = OnceLock::new();
    static LEFTOVER_RE: OnceLock<Regex> = OnceLock::new();
    let result = text
        .replace("<br>", " ")
        .replace("<b>", "")
        .replace("</b>", "")
        .replace("<code>", "")
        .replace("</code>", "")
        .replace("[[", "")
        .replace("]]", "");
    let re = TEMPLATE_RE.get_or_init(|| Regex::new(r"\{\{.*?\}\}").unwrap());
    let result = re.replace_all(&result, "").to_string();
    let leftover = LEFTOVER_RE.get_or_init(|| Regex::new(r"<\s*[a-zA-Z][^>]*>").unwrap());
    if leftover.is_match(&result) {
        tracing::warn!("Unknown wiki text: {result}");
    }
    result
}

/// 方块属性描述的三种形态 → (整体描述, 按值映射)。
/// 返回 (整体描述已 parseWikiText 或 None, 值描述查找表)。
fn describe_property(descriptions: &Value) -> (Option<String>, Option<Vec<(Vec<String>, String)>>) {
    match descriptions {
        Value::String(s) => (Some(parse_wiki_text(s)), None),
        Value::Array(items) => {
            if items
                .first()
                .map(|v| v.is_string())
                .unwrap_or(false)
            {
                // [标记?, 描述] —— 取末项
                let last = items.last().cloned().unwrap_or(Value::Null);
                match last {
                    Value::String(s) => (Some(parse_wiki_text(&s)), None),
                    _ => (None, None),
                }
            } else {
                // [ [键, 描述], ... ]，键为字符串或字符串数组
                let mut map = Vec::new();
                for item in items {
                    if let Value::Array(pair) = item {
                        if pair.len() < 2 {
                            continue;
                        }
                        let keys: Vec<String> = match &pair[0] {
                            Value::String(s) => vec![s.clone()],
                            Value::Array(ks) => ks
                                .iter()
                                .filter_map(|v| v.as_str().map(|s| s.to_string()))
                                .collect(),
                            _ => continue,
                        };
                        if let Value::String(desc) = &pair[1] {
                            map.push((keys, parse_wiki_text(desc)));
                        }
                    }
                }
                (None, Some(map))
            }
        }
        _ => (None, None),
    }
}

/// JS `typeof descriptions == 'string' || typeof descriptions[0] == 'string'` 的判断：
/// 此时整体描述存在，但按值描述为空。
fn property_entry(property_name: &str, descriptions: &Value, gametest: &Value) -> Option<Value> {
    let valid_values = gametest
        .pointer(&format!("/blockProperties/{property_name}/0/validValues"))?
        .as_array()?;
    let (overall, per_value) = describe_property(descriptions);
    let first_is_string = match descriptions {
        Value::Array(items) => items.first().map(|v| v.is_string()).unwrap_or(false),
        _ => false,
    };
    let has_overall_string = matches!(descriptions, Value::String(_)) || first_is_string;
    let mut arr = Vec::new();
    for value_name in valid_values {
        let value_str = match value_name {
            Value::String(s) => s.clone(),
            other => other.to_string(),
        };
        let mut description: Option<String> = None;
        if !has_overall_string {
            if let Some(map) = &per_value {
                for (keys, desc) in map {
                    if keys.iter().any(|k| *k == value_str) {
                        description = Some(desc.clone());
                        break;
                    }
                }
            }
        }
        let mut obj = Map::new();
        obj.insert("valueName".into(), value_name.clone());
        if let Some(d) = description {
            obj.insert("description".into(), json!(d));
        }
        arr.push(Value::Object(obj));
    }
    let mut entry = Map::new();
    entry.insert("propertyName".into(), json!(property_name));
    if let Some(d) = overall {
        entry.insert("description".into(), json!(d));
    }
    entry.insert("values".into(), Value::Array(arr));
    Some(Value::Object(entry))
}

/// 写出整个 chelper 资源包，返回写出的文件数。
pub fn write_resource_pack(input: ChelperInput<'_>, output_dir: &Path) -> Result<usize> {
    let mut file_count = 0usize;
    let renamed = rename_maps(input.result_maps);

    // 普通类别
    for (key, value) in &renamed {
        if matches!(key.as_str(), "gameRule" | "entity" | "block" | "item") {
            continue;
        }
        let doc = json!({
            "id": key,
            "type": "normal",
            "content": content_array(value),
        });
        write_crlf(&output_dir.join(format!("{key}.json")), &to_string_pretty4(&doc))?;
        file_count += 1;
    }

    // gameRule：按类型拆分为 Integer / Boolean
    let mut game_rule_integer: Vec<Value> = Vec::new();
    let mut game_rule_boolean: Vec<Value> = Vec::new();
    if let Some(game_rule) = renamed.get("gameRule") {
        for (name, description) in game_rule.iter() {
            let mut found = false;
            for (camel_case_name, rule_type) in input.gamerule_type {
                if camel_case_name.to_lowercase() == *name {
                    let mut obj = Map::new();
                    obj.insert("name".into(), json!(camel_case_name));
                    if !description.is_empty() {
                        obj.insert("description".into(), json!(description));
                    }
                    match rule_type.as_str() {
                        "int" => game_rule_integer.push(Value::Object(obj)),
                        "bool" => game_rule_boolean.push(Value::Object(obj)),
                        other => tracing::warn!("Unknown gamerule type: {other}"),
                    }
                    found = true;
                    break;
                }
            }
            if !found {
                tracing::warn!("Unknown gamerule: {name}");
            }
        }
    }
    for (id, content) in [("gameRuleInteger", game_rule_integer), ("gameRuleBoolean", game_rule_boolean)] {
        let doc = json!({"id": id, "type": "normal", "content": content});
        write_crlf(&output_dir.join(format!("{id}.json")), &to_string_pretty4(&doc))?;
        file_count += 1;
    }

    // entity：namespace 类型，跳过 minecraft: 前缀
    let mut entity_contents: Vec<Value> = Vec::new();
    if let Some(entity) = renamed.get("entity") {
        for (name, description) in entity.iter() {
            if name.starts_with("minecraft:") {
                continue;
            }
            let mut obj = Map::new();
            obj.insert("name".into(), json!(name));
            if !description.is_empty() {
                obj.insert("description".into(), json!(description));
            }
            entity_contents.push(Value::Object(obj));
        }
    }
    let doc = json!({"id": "entity", "type": "namespace", "content": entity_contents});
    write_crlf(&output_dir.join("entity.json"), &to_string_pretty4(&doc))?;
    file_count += 1;

    // block：方块状态值 + 方块属性描述（属性数据固定取 beta/gametest 缓存）
    let gametest = input.gametest;
    let mut block_state_values: Vec<Value> = Vec::new();
    if let Some(block) = renamed.get("block") {
        for (name, description) in block.iter() {
            let pointer = format!("/blocks/minecraft:{name}");
            let data = gametest.pointer(&pointer);
            let mut entry = Map::new();
            entry.insert("name".into(), json!(name));
            if !description.is_empty() {
                entry.insert("description".into(), json!(description));
            }
            match data {
                None => {
                    tracing::warn!("Unknown block: {name}");
                    // properties 字段整个省略
                }
                Some(data) => {
                    let properties = data.get("properties").and_then(|v| v.as_array());
                    match properties {
                        None => {
                            // 无 properties 字段：省略
                        }
                        Some(p) if p.is_empty() => {
                            // 空属性列表：properties 省略
                        }
                        Some(props) => {
                            let valid_overrides = data.get("validStateOverrides");
                            let mapped: Vec<Value> = props
                                .iter()
                                .map(|p| {
                                    let prop_name =
                                        p.get("name").and_then(|v| v.as_str()).unwrap_or("");
                                    let mut obj = Map::new();
                                    obj.insert("name".into(), json!(prop_name));
                                    if let Some(default) = p.get("defaultValue") {
                                        obj.insert("defaultValue".into(), default.clone());
                                    }
                                    if let Some(Value::Object(overrides)) = valid_overrides {
                                        if let Some(valid) = overrides.get(prop_name) {
                                            obj.insert("valid".into(), valid.clone());
                                        }
                                    }
                                    Value::Object(obj)
                                })
                                .collect();
                            entry.insert("properties".into(), Value::Array(mapped));
                        }
                    }
                }
            }
            block_state_values.push(Value::Object(entry));
        }
    }

    let mut common_contents: Vec<Value> = Vec::new();
    if let Some(Value::Object(common)) = input.block_property_descriptions.get("common") {
        for (property_name, descriptions) in common {
            if gametest
                .pointer(&format!("/blockProperties/{property_name}"))
                .is_none()
            {
                continue;
            }
            if let Some(entry) = property_entry(property_name, descriptions, gametest) {
                common_contents.push(entry);
            }
        }
    }
    let mut block_contents: Vec<Value> = Vec::new();
    if let Some(Value::Array(block_defs)) = input.block_property_descriptions.get("block") {
        for pair in block_defs {
            let Value::Array(pair) = pair else { continue };
            let (blocks_value, properties) = match pair.as_slice() {
                [blocks, properties] => (blocks, properties),
                _ => continue,
            };
            let blocks: Value = match blocks_value {
                Value::String(s) => json!([s]),
                other => other.clone(),
            };
            // JS: properties.size == 0 永远为假（对象无 size），因此 properties 总是数组
            let mut mapped: Vec<Value> = Vec::new();
            if let Value::Object(props) = properties {
                for (property_name, descriptions) in props {
                    if gametest
                        .pointer(&format!("/blockProperties/{property_name}"))
                        .is_none()
                    {
                        continue;
                    }
                    if let Some(entry) = property_entry(property_name, descriptions, gametest) {
                        mapped.push(entry);
                    }
                }
            }
            let mut obj = Map::new();
            obj.insert("blocks".into(), blocks);
            obj.insert("properties".into(), Value::Array(mapped));
            block_contents.push(Value::Object(obj));
        }
    }
    let block_doc = json!({
        "id": "block",
        "type": "block",
        "content": {
            "blockStateValues": block_state_values,
            "blockPropertyDescriptions": {
                "common": common_contents,
                "block": block_contents
            }
        }
    });
    write_crlf(&output_dir.join("block.json"), &to_string_pretty4(&block_doc))?;
    file_count += 1;

    // item：药水附加描述
    let potion_descriptions = input.potion_descriptions.clone();
    let mut item_contents: Vec<Value> = Vec::new();
    if let Some(item) = renamed.get("item") {
        for (name, description) in item.iter() {
            let mut obj = Map::new();
            obj.insert("name".into(), json!(name));
            if !description.is_empty() {
                obj.insert("description".into(), json!(description));
            }
            if name == "potion" || name == "splash_potion" || name == "lingering_potion" {
                obj.insert("descriptions".into(), potion_descriptions.clone());
            }
            item_contents.push(Value::Object(obj));
        }
    }
    let doc = json!({"id": "item", "type": "item", "content": item_contents});
    write_crlf(&output_dir.join("item.json"), &to_string_pretty4(&doc))?;
    file_count += 1;

    // gameMode：项目手工数据（data/game_mode.json 的 content 数组）
    let game_mode_content = input
        .game_mode
        .get("content")
        .cloned()
        .unwrap_or_else(|| input.game_mode.clone());
    let doc = json!({"id": "gameMode", "type": "normal", "content": game_mode_content});
    write_crlf(&output_dir.join("gameMode.json"), &to_string_pretty4(&doc))?;
    file_count += 1;

    // particleEmitter：wiki 粒子描述
    let particle_content: Vec<Value> = input
        .particle_emitter
        .iter()
        .map(|(name, description)| {
            let mut obj = Map::new();
            obj.insert("name".into(), json!(name));
            if !description.is_empty() {
                obj.insert("description".into(), json!(description));
            }
            Value::Object(obj)
        })
        .collect();
    let doc = json!({"id": "particleEmitter", "type": "normal", "content": particle_content});
    write_crlf(&output_dir.join("particleEmitter.json"), &to_string_pretty4(&doc))?;
    file_count += 1;

    Ok(file_count)
}
