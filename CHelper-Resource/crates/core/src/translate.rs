//! 翻译匹配管线：4 级优先级（按 ID 回退）+ 引用 DSL 解析。
//!
//! 优先级（每个 ID 独立回退）：
//! 1. 项目已复核翻译 `translations/<category>.json`（允许 AI 起草后经人工复核）
//! 2. wiki 数据（游戏规则、基岩版粒子，由本工具自行抓取解析）
//! 3. caidlist `translation/` 目录，其引用的
//!    ST（wiki 标准译名表）/ JE（Java 版语言文件）/ BE（基岩版语言文件）资源由本工具自行抓取
//! 4. 项目未复核 AI 翻译 `ai_translations/<category>.json`
//!
//! 都没有则结果为空字符串（输出时省略 description）。
//!
//! 引用 DSL（对齐 caidlist `src/util/templateMatch.js`）：
//! `ST: key` / `JE: key` / `BE: key` / `this: key` / `<枚举>: key` / `Missing: 字面量` /
//! `{{pick|...}}`、`{{format|...}}`、`{{if_exists|...}}` 拼接模板、`ST!key` 模板内引用。

use indexmap::IndexMap;
use serde_json::Value;
use std::collections::HashMap;
use std::sync::OnceLock;

/// 单个类别的四级翻译来源（持有所有权，便于按类别构建）。
#[derive(Default)]
pub struct CategoryLevels {
    /// 第 1 级：项目已复核翻译。
    pub human: Option<serde_json::Map<String, Value>>,
    /// 第 2 级：wiki（gamerule / particle）。
    pub wiki: Option<IndexMap<String, String>>,
    /// 第 3 级：caidlist translation 目录。
    pub caidlist: Option<serde_json::Map<String, Value>>,
    /// 第 4 级：尚未复核的 AI 翻译。
    pub ai: Option<serde_json::Map<String, Value>>,
}

impl CategoryLevels {
    /// 返回可尝试的原始值（优先级：已复核 > wiki > caidlist > AI）。
    /// 空值跳过；`"EMPTY"` 是 caidlist 写回的硬性 notFound 标记。
    fn lookup_candidates(&self, id: &str) -> Vec<RawValue> {
        // (级别来源, 是否为 wiki 映射) 依优先级排列
        let sources: [(Option<&dyn LevelSource>, bool); 4] = [
            (self.human.as_ref().map(|m| m as &dyn LevelSource), false),
            (self.wiki.as_ref().map(|m| m as &dyn LevelSource), false),
            (self.caidlist.as_ref().map(|m| m as &dyn LevelSource), false),
            (self.ai.as_ref().map(|m| m as &dyn LevelSource), true),
        ];
        let mut candidates = Vec::new();
        for (source, is_ai) in sources {
            let Some(source) = source else { continue };
            match source.get(id) {
                Some(s) if s == "EMPTY" => {
                    candidates.push(RawValue::EmptyMarker);
                    break;
                }
                // 纯空白也没有翻译；继续检查低优先级来源。
                Some(s) if !s.trim().is_empty() => candidates.push(RawValue::Found {
                    value: s.to_string(),
                    is_ai,
                }),
                _ => {}
            }
        }
        if candidates.is_empty() {
            candidates.push(RawValue::Miss);
        }
        candidates
    }

    /// ID 是否在任一级别中有条目（无论值是否为空）。
    pub fn contains(&self, id: &str) -> bool {
        self.human.as_ref().map(|m| m.contains_key(id)).unwrap_or(false)
            || self.wiki.as_ref().map(|m| m.contains_key(id)).unwrap_or(false)
            || self.caidlist.as_ref().map(|m| m.contains_key(id)).unwrap_or(false)
            || self.ai.as_ref().map(|m| m.contains_key(id)).unwrap_or(false)
    }
}

/// 统一 JSON map 与 wiki IndexMap 的查找接口。
trait LevelSource {
    fn get(&self, id: &str) -> Option<&str>;
}

impl LevelSource for serde_json::Map<String, Value> {
    fn get(&self, id: &str) -> Option<&str> {
        self.get(id).and_then(|v| v.as_str())
    }
}

impl LevelSource for IndexMap<String, String> {
    fn get(&self, id: &str) -> Option<&str> {
        IndexMap::get(self, id).map(|s| s.as_str())
    }
}

/// lookup_raw 的结果。
enum RawValue {
    Found { value: String, is_ai: bool },
    EmptyMarker,
    Miss,
}

/// 标准译名表级联视图（复刻 `cascadeMap`）：优先子表在前，其余子表按插入顺序拼入；
/// 同时保留 `k (子表)` 复合键。大小写不敏感查找取第一个命中。
pub struct CascadeMap {
    #[allow(dead_code)]
    entries: IndexMap<String, String>,
    lowercase_first: HashMap<String, String>,
}

pub fn cascade_map(st: &Value, priority: &[&str]) -> CascadeMap {
    let empty = serde_json::Map::new();
    let obj = st.as_object().unwrap_or(&empty);
    let mut map_keys: Vec<&String> = Vec::new();
    for p in priority {
        if let Some(key) = obj.keys().find(|k| k == p) {
            map_keys.push(key);
        }
    }
    for k in obj.keys() {
        if !map_keys.contains(&k) {
            map_keys.push(k);
        }
    }
    let mut entries = IndexMap::new();
    for map_key in map_keys {
        if let Some(Value::Object(sub)) = obj.get(map_key) {
            for (k, v) in sub {
                if let Some(s) = v.as_str() {
                    entries.insert(format!("{k} ({map_key})"), s.to_string());
                    entries.entry(k.clone()).or_insert_with(|| s.to_string());
                }
            }
        }
    }
    let mut lowercase_first = HashMap::new();
    for (k, v) in &entries {
        lowercase_first.entry(k.to_lowercase()).or_insert_with(|| v.clone());
    }
    CascadeMap {
        entries,
        lowercase_first,
    }
}

impl CascadeMap {
    pub fn get_ignore_case(&self, key: &str) -> Option<&String> {
        self.lowercase_first.get(&key.to_lowercase())
    }
}

/// 翻译状态。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    Provided,
    NotFound,
}

/// 配方 dataDriven 引用解析所需的上下文。
pub struct DataDrivenContext<'a> {
    pub recipe_data: &'a serde_json::Map<String, Value>,
}

/// 翻译器：持有跨类别结果表（供 `<枚举>: key` 引用）与缓存。
pub struct Translator<'a> {
    je_lang: &'a serde_json::Map<String, Value>,
    be_lang: &'a serde_json::Map<String, Value>,
    /// 类别名 → (ID → 译文)。
    pub results: IndexMap<String, IndexMap<String, String>>,
    /// 各类别的原始 ID 列表（`if_exists` 需要）。
    original_arrays: HashMap<String, Vec<String>>,
    data_driven: Option<DataDrivenContext<'a>>,
    cascades: Vec<(String, CascadeMap)>,
    warnings: Vec<String>,
}

const CIRCULAR: &str = "\u{0}circular\u{0}";
const AI_TRANSLATION_SUFFIX: &str = "（AI翻译，仅供参考）";

/// ST 级联优先级组合。
pub mod st_priority {
    pub const FULL: &[&str] = &[];
    pub const BLOCK: &[&str] = &["BlockSprite", "ExclusiveBlockSprite"];
    pub const ITEM: &[&str] = &["ItemSprite", "ExclusiveItemSprite"];
    pub const ENTITY: &[&str] = &["EntitySprite", "ExclusiveEntitySprite"];
    pub const EFFECT: &[&str] = &["EffectSprite", "ExclusiveEffectSprite"];
    pub const ENCHANT: &[&str] = &["EnchantmentSprite"];
    pub const BIOME: &[&str] = &["BiomeSprite", "ExclusiveBiomeSprite"];
    pub const ENV: &[&str] = &["EnvSprite"];
}

impl<'a> Translator<'a> {
    pub fn new(
        je_lang: &'a serde_json::Map<String, Value>,
        be_lang: &'a serde_json::Map<String, Value>,
        data_driven: Option<DataDrivenContext<'a>>,
    ) -> Self {
        Self {
            je_lang,
            be_lang,
            results: IndexMap::new(),
            original_arrays: HashMap::new(),
            data_driven,
            cascades: Vec::new(),
            warnings: Vec::new(),
        }
    }

    pub fn take_warnings(&mut self) -> Vec<String> {
        std::mem::take(&mut self.warnings)
    }

    fn warn(&mut self, text: String) {
        self.warnings.push(text);
    }

    fn cascade_for(&mut self, st: &'a Value, priority: &'static [&'static str]) -> &CascadeMap {
        let key = priority.concat();
        if let Some(idx) = self.cascades.iter().position(|(k, _)| *k == key) {
            return &self.cascades[idx].1;
        }
        let cascade = cascade_map(st, priority);
        self.cascades.push((key.to_string(), cascade));
        &self.cascades.last().unwrap().1
    }

    /// 匹配一个类别的全部 ID（对齐 `matchTranslations`：结果表包含所有 ID，未找到为空字符串）。
    pub fn match_translations(
        &mut self,
        name: &str,
        original_array: &[String],
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
    ) {
        let _ = self.cascade_for(st, priority);
        self.original_arrays
            .insert(name.to_string(), original_array.to_vec());
        let mut result = IndexMap::new();
        let mut cache = HashMap::new();
        for original_value in original_array {
            let (_, translation) =
                self.translate_cached(name, original_value, levels, st, priority, false, &mut cache);
            result.insert(original_value.clone(), translation);
        }
        self.results.insert(name.to_string(), result);
    }

    #[allow(clippy::too_many_arguments)]
    fn translate_cached(
        &mut self,
        category: &str,
        original_value: &str,
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
        inside_template: bool,
        cache: &mut HashMap<String, (State, String)>,
    ) -> (State, String) {
        if let Some(hit) = cache.get(original_value) {
            return hit.clone();
        }
        // 环形引用哨兵
        cache.insert(original_value.to_string(), (State::NotFound, CIRCULAR.to_string()));
        let result =
            self.translate_uncached(category, original_value, levels, st, priority, inside_template, cache);
        cache.insert(original_value.to_string(), result.clone());
        result
    }

    #[allow(clippy::too_many_arguments)]
    fn translate_uncached(
        &mut self,
        category: &str,
        original_value: &str,
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
        inside_template: bool,
        cache: &mut HashMap<String, (State, String)>,
    ) -> (State, String) {
        if inside_template && original_value.contains('|') {
            return self.run_template_args(category, original_value, levels, st, priority, cache);
        }
        if inside_template && original_value.contains('!') {
            // 模板内外部引用：`ST!key` → `ST:key`
            let raw = original_value.replace('!', ":");
            return self.resolve_raw(category, "", &raw, levels, st, priority, cache);
        }
        for candidate in levels.lookup_candidates(original_value) {
            match candidate {
                RawValue::Found { value: raw, is_ai } => {
                    let result = self.resolve_raw(
                        category,
                        original_value,
                        &raw,
                        levels,
                        st,
                        priority,
                        cache,
                    );
                    if result.0 == State::Provided && !result.1.trim().is_empty() {
                        let mut translation = result.1;
                        if is_ai {
                            translation.push_str(AI_TRANSLATION_SUFFIX);
                        }
                        return (State::Provided, translation);
                    }
                }
                // caidlist 写回的 EMPTY 标记 = 硬性未找到（JS 在 autoMatch 之前返回）
                RawValue::EmptyMarker => return (State::NotFound, String::new()),
                RawValue::Miss => return (State::NotFound, String::new()),
            }
        }
        (State::NotFound, String::new())
    }

    /// 解析一个非空原始值（字面量或 `source: key` 引用），对齐 `matchTranslation`。
    #[allow(clippy::too_many_arguments)]
    fn resolve_raw(
        &mut self,
        category: &str,
        original_value: &str,
        raw: &str,
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
        cache: &mut HashMap<String, (State, String)>,
    ) -> (State, String) {
        let mut user_translation: Option<String> = Some(raw.to_string());
        if raw.contains("{{") && raw.contains("}}") {
            // 拼接模板：逐个 {{key}} 内联解析，任一失败则整体置空
            let re = template_regex();
            let mut failed_keys: Vec<String> = Vec::new();
            let mut out = String::new();
            let mut cursor = 0usize;
            for cap in re.captures_iter(raw) {
                let whole = cap.get(0).unwrap();
                out.push_str(&raw[cursor..whole.start()]);
                cursor = whole.end();
                let key = cap.get(1).unwrap().as_str().trim().to_string();
                let real_key = if let Some(rest) = key.strip_prefix('#') {
                    format!("{original_value}.{rest}")
                } else {
                    key.clone()
                };
                let (state, translation) =
                    self.translate_cached(category, &real_key, levels, st, priority, true, cache);
                if state != State::Provided {
                    failed_keys.push(key);
                }
                out.push_str(&translation);
            }
            out.push_str(&raw[cursor..]);
            if !failed_keys.is_empty() {
                self.warn(format!(
                    "[{category}] Should provide inline references: {original_value}({})",
                    failed_keys.join(",")
                ));
                user_translation = Some(String::new());
            } else {
                user_translation = Some(out);
            }
        } else if let Some((source, colon_pos)) = split_reference(raw) {
            let key = raw[colon_pos + 1..].trim();
            let mut resolved: Option<String> = None;
            // 配方 dataDriven 自定义引用解析
            if source == "dataDriven" {
                if let Some(ctx) = &self.data_driven {
                    if let Some(Value::String(untranslated)) = ctx.recipe_data.get(key) {
                        resolved = Some(self.resolve_data_driven_string(
                            untranslated,
                            levels,
                            st,
                            priority,
                            cache,
                        ));
                    }
                }
            }
            if resolved.is_none() {
                resolved = match source {
                    // `: xxx` 直接使用
                    "" => Some(raw[colon_pos + 1..].to_string()),
                    s if s.eq_ignore_ascii_case("st") => {
                        self.cascade_for(st, priority).get_ignore_case(key).cloned()
                    }
                    s if s.eq_ignore_ascii_case("je") => self
                        .je_lang
                        .get(key)
                        .and_then(|v| v.as_str())
                        .map(|s| s.to_string()),
                    s if s.eq_ignore_ascii_case("be") => self
                        .be_lang
                        .get(key)
                        .and_then(|v| v.as_str())
                        .map(|s| s.to_string()),
                    s if s.eq_ignore_ascii_case("this") => {
                        let (state, translation) =
                            self.translate_cached(category, key, levels, st, priority, false, cache);
                        (state == State::Provided).then_some(translation)
                    }
                    s if s.eq_ignore_ascii_case("missing") => {
                        let (state, translation) =
                            self.resolve_raw(category, original_value, key, levels, st, priority, cache);
                        let literal = if state == State::Provided { translation } else { String::new() };
                        let found = self
                            .cascade_for(st, priority)
                            .get_ignore_case(&literal)
                            .cloned();
                        match found {
                            Some(found) => {
                                self.warn(format!(
                                    "[{category}] Translation Found: {original_value} -> {found}"
                                ));
                                Some(found)
                            }
                            None => {
                                self.warn(format!(
                                    "[{category}] Missing Translation: {original_value} -> {literal}"
                                ));
                                Some(literal)
                            }
                        }
                    }
                    s => self
                        .results
                        .get(s)
                        .and_then(|m| m.get(key))
                        .filter(|v| !v.is_empty())
                        .cloned(),
                };
            }
            let was_unresolved = resolved.is_none();
            user_translation = resolved.filter(|t| !t.is_empty());
            if was_unresolved {
                self.warn(format!(
                    "[{category}] Failed to resolve reference: {original_value}({source}: {key})"
                ));
            }
        }
        match user_translation {
            Some(t) if !t.trim().is_empty() => (State::Provided, t),
            _ => (State::NotFound, String::new()),
        }
    }

    /// 配方 dataDriven 字符串中的标识符替换为物品翻译。
    #[allow(clippy::too_many_arguments)]
    fn resolve_data_driven_string(
        &mut self,
        untranslated: &str,
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
        cache: &mut HashMap<String, (State, String)>,
    ) -> String {
        static IDENT: OnceLock<regex::Regex> = OnceLock::new();
        let re = IDENT.get_or_init(|| regex::Regex::new(r"[A-Za-z][A-Za-z_:0-9]*").unwrap());
        let mut out = String::new();
        let mut last = 0usize;
        for m in re.find_iter(untranslated) {
            out.push_str(&untranslated[last..m.start()]);
            last = m.end();
            let id = format!("${}", m.as_str());
            let (_, translation) =
                self.translate_cached("recipe", &id, levels, st, priority, false, cache);
            out.push_str(&translation);
        }
        out.push_str(&untranslated[last..]);
        out
    }

    /// `{{a|b|c}}` 模板参数级解析（format / pick / if_exists）。
    #[allow(clippy::too_many_arguments)]
    fn run_template_args(
        &mut self,
        category: &str,
        original_value: &str,
        levels: &CategoryLevels,
        st: &'a Value,
        priority: &'static [&'static str],
        cache: &mut HashMap<String, (State, String)>,
    ) -> (State, String) {
        let parts: Vec<String> = original_value.split('|').map(|s| s.trim().to_string()).collect();
        let (first, mut refs) = (parts[0].clone(), parts[1..].to_vec());
        let func = first.to_lowercase();
        let known = matches!(func.as_str(), "format" | "pick" | "if_exists");
        if !known {
            refs.insert(0, first.clone());
        }
        enum Arg {
            Raw(String),
            Ref(String),
        }
        let args: Vec<Arg> = refs
            .iter()
            .map(|r| {
                if let Some(rest) = r.strip_prefix('\'') {
                    let raw = rest.strip_suffix('\'').unwrap_or(rest);
                    Arg::Raw(raw.to_string())
                } else {
                    Arg::Ref(r.clone())
                }
            })
            .collect();
        fn resolve_arg<'x>(
            this: &mut Translator<'x>,
            arg: &Arg,
            category: &str,
            levels: &CategoryLevels,
            st: &'x Value,
            priority: &'static [&'static str],
            cache: &mut HashMap<String, (State, String)>,
        ) -> (State, String) {
            match arg {
                Arg::Raw(s) => (State::Provided, s.clone()),
                Arg::Ref(r) => this.translate_cached(category, r, levels, st, priority, true, cache),
            }
        }
        if !known || func == "format" {
            let mut state = State::Provided;
            let mut translations: Vec<String> = Vec::new();
            for arg in &args {
                let (s, t) = resolve_arg(self, arg, category, levels, st, priority, cache);
                if s != State::Provided {
                    state = State::NotFound;
                    self.warn(format!(
                        "[{category}] Should provide inline references: {original_value}({})",
                        refs.join(",")
                    ));
                }
                translations.push(t);
            }
            let fmt = translations.first().cloned().unwrap_or_default();
            return (state, js_format(&fmt, &translations[1..]));
        }
        if func == "pick" {
            let mut last: (State, String) = (State::NotFound, String::new());
            for arg in &args {
                let resolved = resolve_arg(self, arg, category, levels, st, priority, cache);
                if resolved.0 == State::Provided {
                    return resolved;
                }
                last = resolved;
            }
            self.warn(format!(
                "[{category}] None of items is provided: {original_value}({})",
                refs.join(",")
            ));
            return last;
        }
        // if_exists
        if refs.len() != 3 {
            self.warn(format!(
                "[{category}] Expect 3 arguments instead of {} argument(s): {original_value}",
                refs.len()
            ));
            return (State::NotFound, String::new());
        }
        let test_value = refs[0].clone();
        let exists = self
            .original_arrays
            .get(category)
            .map(|arr| arr.contains(&test_value))
            .unwrap_or(false);
        let target = if exists { &args[1] } else { &args[2] };
        resolve_arg(self, target, category, levels, st, priority, cache)
    }
}

/// `raw` 是否为 `source: key` 引用，返回 (source, 冒号位置)。
/// 对齐 JS `^(\S*):`：冒号前是非空白前缀且直达冒号。
fn split_reference(raw: &str) -> Option<(&str, usize)> {
    let first_colon = raw.find(':')?;
    let prefix = &raw[..first_colon];
    if prefix.chars().any(|c| c.is_whitespace()) {
        return None;
    }
    Some((prefix.trim(), first_colon))
}

fn template_regex() -> &'static regex::Regex {
    static RE: OnceLock<regex::Regex> = OnceLock::new();
    RE.get_or_init(|| regex::Regex::new(r"\{\{\s*([^}]+?)\s*\}\}").unwrap())
}

/// 复刻 Node `util.format` 的常用子集（%s %d %i %f %j %%；多余参数以空格追加）。
pub fn js_format(fmt: &str, args: &[String]) -> String {
    let mut out = String::new();
    let mut arg_index = 0usize;
    let chars: Vec<char> = fmt.chars().collect();
    let mut i = 0usize;
    while i < chars.len() {
        let c = chars[i];
        if c == '%' && i + 1 < chars.len() {
            let spec = chars[i + 1];
            match spec {
                '%' => {
                    out.push('%');
                    i += 2;
                    continue;
                }
                's' | 'd' | 'i' | 'f' | 'j' | 'o' | 'O' => {
                    if let Some(arg) = args.get(arg_index) {
                        arg_index += 1;
                        match spec {
                            's' | 'o' | 'O' => out.push_str(arg),
                            'd' | 'i' => match arg.trim().parse::<f64>() {
                                Ok(n) => out.push_str(&js_number(n)),
                                Err(_) => out.push_str("NaN"),
                            },
                            'f' => match arg.trim().parse::<f64>() {
                                Ok(n) => out.push_str(&js_number(n)),
                                Err(_) => out.push_str("NaN"),
                            },
                            'j' => out.push_str(&serde_json::to_string(arg).unwrap_or_default()),
                            _ => out.push_str(arg),
                        }
                        i += 2;
                        continue;
                    }
                    out.push('%');
                    out.push(spec);
                    i += 2;
                    continue;
                }
                _ => {}
            }
        }
        out.push(c);
        i += 1;
    }
    for arg in &args[arg_index.min(args.len())..] {
        out.push(' ');
        out.push_str(arg);
    }
    out
}

/// JS Number → String。
fn js_number(n: f64) -> String {
    if n.is_nan() {
        "NaN".to_string()
    } else if n.fract() == 0.0 && n.abs() < 1e15 {
        (n as i64).to_string()
    } else {
        n.to_string()
    }
}

/// 类别名 → (ID → 译文) 的结果表。
pub type ResultMaps = IndexMap<String, IndexMap<String, String>>;

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn map_of(v: Value) -> serde_json::Map<String, Value> {
        v.as_object().unwrap().clone()
    }

    #[test]
    fn literal_and_reference() {
        let je = map_of(json!({"subtitles.foo": "怪异的噪声"}));
        let be = map_of(json!({"tile.stone.name": "石头"}));
        let st = json!({"BlockSprite": {"stone": "石头"}});
        let levels = CategoryLevels {
            caidlist: Some(map_of(json!({
                "foo": "JE: subtitles.foo",
                "bar": "ST: stone",
                "baz": "BE: tile.stone.name",
                "empty": "",
                "circ": "this: circ"
            }))),
            ..Default::default()
        };
        let mut t = Translator::new(&je, &be, None);
        let arr = vec!["foo".into(), "bar".into(), "baz".into(), "empty".into(), "circ".into()];
        t.match_translations("block", &arr, &levels, &st, st_priority::BLOCK);
        let m = &t.results["block"];
        assert_eq!(m["foo"], "怪异的噪声");
        assert_eq!(m["bar"], "石头");
        assert_eq!(m["baz"], "石头");
        assert_eq!(m["empty"], "");
        assert_eq!(m["circ"], "");
    }

    #[test]
    fn template_format() {
        let je = map_of(json!({"subtitles.ambient.cave": "怪异的噪声"}));
        let be = map_of(json!({}));
        let st = json!({"EnvSprite": {"basalt deltas": "玄武岩三角洲"}});
        let levels = CategoryLevels {
            caidlist: Some(map_of(json!({
                "x": "{{ambient.*.additions|ST!basalt deltas}}",
                "ambient.*.additions": "%s：环境附加音效"
            }))),
            ..Default::default()
        };
        let mut t = Translator::new(&je, &be, None);
        t.match_translations("sound", &["x".into()], &levels, &st, st_priority::FULL);
        assert_eq!(t.results["sound"]["x"], "玄武岩三角洲：环境附加音效");
    }

    #[test]
    fn levels_fallback() {
        let je = map_of(json!({}));
        let be = map_of(json!({}));
        let st = json!({});
        let human = map_of(json!({"a": "人工A", "b": ""}));
        let ai = map_of(json!({"b": "AI B", "c": "AI C"}));
        let caidlist = map_of(json!({"b": "清单B", "d": "清单D"}));
        let levels = CategoryLevels {
            human: Some(human),
            caidlist: Some(caidlist),
            ai: Some(ai),
            ..Default::default()
        };
        let mut t = Translator::new(&je, &be, None);
        let arr: Vec<String> = ["a", "b", "c", "d", "e"].iter().map(|s| s.to_string()).collect();
        t.match_translations("x", &arr, &levels, &st, st_priority::FULL);
        let m = &t.results["x"];
        assert_eq!(m["a"], "人工A");
        // b: human="" → 跳过 → caidlist="清单B" 命中，AI 不会被用到
        assert_eq!(m["b"], "清单B");
        assert_eq!(m["c"], "AI C");
        assert_eq!(m["d"], "清单D");
        assert_eq!(m["e"], "");
    }

    #[test]
    fn format_d_percent() {
        assert_eq!(js_format("%s-%s", &["a".into(), "b".into()]), "a-b");
        assert_eq!(js_format("%d 个", &["3".into()]), "3 个");
        assert_eq!(js_format("100%%", &[]), "100%");
        assert_eq!(js_format("%s", &["a".into(), "b".into()]), "a b");
        assert_eq!(js_format("%s", &[]), "%s");
    }
}
