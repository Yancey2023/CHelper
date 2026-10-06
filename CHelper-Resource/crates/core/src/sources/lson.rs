//! Lua 表字面量（LSON）解析器，移植自 caidlist 的 `src/util/lson.js`（chevrotain 实现）。
//!
//! 用于解析中文 Minecraft Wiki 上 `Module:Gamerule_type_values_BE`、
//! `Module:Block_property_descriptions_BE` 等 Lua 模块 `return {...}` 的数据。

use anyhow::{anyhow, bail, Context, Result};
use serde_json::Value;

#[derive(Debug, Clone, PartialEq)]
enum LValue {
    Nil,
    Bool(bool),
    Num(f64),
    Str(String),
    Table(LTable),
}

#[derive(Debug, Clone, PartialEq)]
enum LKey {
    Name(String),
    Num(f64),
    Str(String),
    Bool(bool),
    Nil,
}

/// 与 JS `visitNode` 语义一致：既有位置字段又有键字段时是数组；
/// 位置字段按 1 计数（`shift` 后 0 基），数字键 `[k]=v` 落在 `k-1` 槽位，稀疏处补 null。
#[derive(Debug, Clone, PartialEq)]
struct LTable {
    /// 按插入顺序的字段；键为 None 表示位置字段。
    sequence: Vec<(Option<LKey>, LValue)>,
    has_positional: bool,
}

fn key_to_string(k: &LKey) -> Result<String> {
    Ok(match k {
        LKey::Name(s) | LKey::Str(s) => s.clone(),
        LKey::Num(n) => {
            if n.fract() == 0.0 {
                format!("{}", *n as i64)
            } else {
                format!("{n}")
            }
        }
        LKey::Bool(b) => b.to_string(),
        LKey::Nil => "nil".to_string(),
    })
}

impl LTable {
    fn to_json(&self) -> Result<Value> {
        if !self.has_positional {
            let mut map = serde_json::Map::new();
            for (k, v) in &self.sequence {
                map.insert(key_to_string(k.as_ref().unwrap())?, v.to_json()?);
            }
            Ok(Value::Object(map))
        } else {
            // 复刻 JS: ret[counter]=v（counter 从 1 起）+ ret[k]=v + ret.shift()
            let mut slots: Vec<Value> = Vec::new();
            let set_slot = |slots: &mut Vec<Value>, idx_f: f64, v: Value| -> Result<()> {
                if idx_f < 0.0 || idx_f.fract() != 0.0 {
                    bail!("非法的数字槽位: {idx_f}");
                }
                let idx = idx_f as usize;
                if slots.len() <= idx {
                    slots.resize(idx + 1, Value::Null);
                }
                slots[idx] = v;
                Ok(())
            };
            let mut counter: f64 = 1.0;
            for (k, v) in &self.sequence {
                match k {
                    None => {
                        set_slot(&mut slots, counter - 1.0, v.to_json()?)?;
                        counter += 1.0;
                    }
                    Some(LKey::Num(n)) => set_slot(&mut slots, n - 1.0, v.to_json()?)?,
                    Some(key) => {
                        // JS 数组上的字符串键在 JSON.stringify 时被丢弃
                        tracing::warn!("LSON 数组中的字符串键被忽略: {}", key_to_string(key)?);
                    }
                }
            }
            Ok(Value::Array(slots))
        }
    }
}

impl LValue {
    fn to_json(&self) -> Result<Value> {
        Ok(match self {
            LValue::Nil => Value::Null,
            LValue::Bool(b) => Value::Bool(*b),
            LValue::Num(n) => {
                if n.is_finite() {
                    serde_json::Number::from_f64(*n).map(Value::Number).unwrap_or(Value::Null)
                } else {
                    // JS: JSON.stringify(Infinity) === "null"
                    Value::Null
                }
            }
            LValue::Str(s) => Value::String(s.clone()),
            LValue::Table(t) => t.to_json()?,
        })
    }
}

/// 解析 Lua 表达式（模块源码 `return` 之后的部分）并转为 JSON。
pub fn parse_lson(input: &str) -> Result<Value> {
    let mut parser = Parser::new(input);
    let v = parser.parse_expression()?;
    parser.skip_ws();
    if parser.pos < parser.src.len() {
        let end = (parser.pos + 40).min(parser.src.len());
        bail!(
            "LSON 解析后仍有剩余内容: {}",
            String::from_utf8_lossy(&parser.src[parser.pos..end])
        );
    }
    v.to_json()
}

struct Parser<'a> {
    src: &'a [u8],
    pos: usize,
}

impl<'a> Parser<'a> {
    fn new(src: &'a str) -> Self {
        Self { src: src.as_bytes(), pos: 0 }
    }

    fn peek(&self) -> Option<u8> {
        self.src.get(self.pos).copied()
    }

    fn skip_ws(&mut self) {
        loop {
            while self.pos < self.src.len() && (self.src[self.pos] as char).is_ascii_whitespace() {
                self.pos += 1;
            }
            if self.src[self.pos..].starts_with(b"--") {
                if self.src[self.pos..].starts_with(b"--[[") {
                    if let Some(end) = find_sub(&self.src[self.pos + 4..], b"]]") {
                        self.pos += 4 + end + 2;
                        continue;
                    }
                    self.pos = self.src.len();
                    continue;
                }
                while self.pos < self.src.len() && self.src[self.pos] != b'\n' {
                    self.pos += 1;
                }
                continue;
            }
            break;
        }
    }

    fn expect(&mut self, b: u8) -> Result<()> {
        self.skip_ws();
        if self.peek() == Some(b) {
            self.pos += 1;
            Ok(())
        } else {
            bail!(
                "期望 '{}'，实际 {}",
                b as char,
                self.peek().map(|c| c as char).unwrap_or('\0')
            );
        }
    }

    fn parse_expression(&mut self) -> Result<LValue> {
        self.skip_ws();
        match self.peek() {
            Some(b'{') => self.parse_table(),
            _ => self.parse_primitive(),
        }
    }

    fn parse_primitive(&mut self) -> Result<LValue> {
        self.skip_ws();
        match self.peek() {
            None => bail!("LSON 意外结束"),
            Some(b'"') | Some(b'\'') => Ok(LValue::Str(self.parse_string()?)),
            Some(b'[') if self.looks_like_long_string() => Ok(LValue::Str(self.parse_long_string()?)),
            Some(c) if c == b'-' || c == b'+' || c.is_ascii_digit() => self.parse_number(),
            Some(_) => {
                for (word, val) in
                    [("nil", LValue::Nil), ("false", LValue::Bool(false)), ("true", LValue::Bool(true))]
                {
                    if self.src[self.pos..].starts_with(word.as_bytes()) {
                        let after = self.src[self.pos + word.len()..].first().copied();
                        let boundary =
                            after.map(|c| !(c.is_ascii_alphanumeric() || c == b'_')).unwrap_or(true);
                        if boundary {
                            self.pos += word.len();
                            return Ok(val);
                        }
                    }
                }
                let end = (self.pos + 30).min(self.src.len());
                bail!(
                    "无法识别的 LSON 记号: {}",
                    String::from_utf8_lossy(&self.src[self.pos..end])
                )
            }
        }
    }

    fn looks_like_long_string(&self) -> bool {
        self.src[self.pos..].starts_with(b"[[")
    }

    fn parse_long_string(&mut self) -> Result<String> {
        // [[...]] 或 [==[...]==]
        self.pos += 1; // '['
        let eq_count = self.src[self.pos..].iter().take_while(|&&c| c == b'=').count();
        self.pos += eq_count;
        self.expect(b'[')?;
        let close = format!("]{}]", "=".repeat(eq_count));
        let rest = &self.src[self.pos..];
        match find_sub(rest, close.as_bytes()) {
            Some(end) => {
                let content = &rest[..end];
                self.pos += end + close.len();
                let mut s = String::from_utf8_lossy(content).to_string();
                // Lua 长字符串紧跟的首个换行不计入
                if s.starts_with('\r') {
                    s.remove(0);
                }
                if s.starts_with('\n') {
                    s.remove(0);
                }
                Ok(s)
            }
            None => bail!("LSON 长字符串未闭合"),
        }
    }

    fn parse_number(&mut self) -> Result<LValue> {
        let start = self.pos;
        if self.src[self.pos..].starts_with(b"0x") || self.src[self.pos..].starts_with(b"0X") {
            self.pos += 2;
            let hex_start = self.pos;
            while self.pos < self.src.len() && (self.src[self.pos] as char).is_ascii_hexdigit() {
                self.pos += 1;
            }
            let hex = std::str::from_utf8(&self.src[hex_start..self.pos])
                .map_err(|_| anyhow!("非法十六进制数"))?;
            let mut n = i64::from_str_radix(hex, 16)? as f64;
            if self.peek() == Some(b'.') {
                self.pos += 1;
                let frac_start = self.pos;
                while self.pos < self.src.len() && (self.src[self.pos] as char).is_ascii_hexdigit() {
                    self.pos += 1;
                }
                let frac = std::str::from_utf8(&self.src[frac_start..self.pos])
                    .map_err(|_| anyhow!("非法十六进制数"))?;
                let mut scale = 1.0 / 16.0;
                for c in frac.chars() {
                    n += c.to_digit(16).unwrap() as f64 * scale;
                    scale /= 16.0;
                }
            }
            return Ok(LValue::Num(n));
        }
        while self.pos < self.src.len() {
            let c = self.src[self.pos];
            if c.is_ascii_digit() || c == b'.' {
                self.pos += 1;
                continue;
            }
            if matches!(c, b'e' | b'E') {
                // 指数：e 后可跟正负号
                self.pos += 1;
                if matches!(self.peek(), Some(b'+') | Some(b'-')) {
                    self.pos += 1;
                }
                continue;
            }
            break;
        }
        let text = std::str::from_utf8(&self.src[start..self.pos])
            .map_err(|_| anyhow!("非法数字"))?
            .trim();
        let n: f64 = text.parse().with_context(|| format!("非法数字: {text}"))?;
        Ok(LValue::Num(n))
    }

    fn parse_string(&mut self) -> Result<String> {
        let quote = self.peek().ok_or_else(|| anyhow!("LSON 意外结束（字符串）"))?;
        self.pos += 1;
        let mut out = String::new();
        loop {
            let Some(c) = self.peek() else {
                bail!("LSON 字符串未闭合");
            };
            self.pos += 1;
            if c == quote {
                break;
            }
            if c == b'\\' {
                let Some(esc) = self.peek() else { bail!("LSON 字符串未闭合") };
                self.pos += 1;
                match esc {
                    b'a' => out.push('\x07'),
                    b'b' => out.push('\u{8}'),
                    b'f' => out.push('\u{c}'),
                    b'n' => out.push('\n'),
                    b'r' => out.push('\r'),
                    b't' => out.push('\t'),
                    b'v' => out.push('\u{b}'),
                    b'\\' => out.push('\\'),
                    b'"' => out.push('"'),
                    b'\'' => out.push('\''),
                    b'\n' => out.push('\n'),
                    b'z' => {
                        while self.pos < self.src.len() && (self.src[self.pos] as char).is_ascii_whitespace() {
                            self.pos += 1;
                        }
                    }
                    b'x' => {
                        let hex = self.take_hex(2)?;
                        out.push(char::from_u32(hex as u32).unwrap_or('\u{fffd}'));
                    }
                    b'u' => {
                        self.expect(b'{')?;
                        let mut hex = 0u32;
                        while let Some(c) = self.peek() {
                            if let Some(d) = (c as char).to_digit(16) {
                                hex = hex * 16 + d;
                                self.pos += 1;
                            } else {
                                break;
                            }
                        }
                        self.expect(b'}')?;
                        out.push(char::from_u32(hex).unwrap_or('\u{fffd}'));
                    }
                    c if c.is_ascii_digit() => {
                        let mut val = (c - b'0') as u32;
                        for _ in 0..2 {
                            match self.peek() {
                                Some(d) if d.is_ascii_digit() => {
                                    val = val * 10 + (d - b'0') as u32;
                                    self.pos += 1;
                                }
                                _ => break,
                            }
                        }
                        out.push(char::from_u32(val).unwrap_or('\u{fffd}'));
                    }
                    other => out.push(other as char),
                }
                continue;
            }
            if c < 0x80 {
                out.push(c as char);
            } else {
                // 回退一个字节，按 UTF-8 字符读取
                self.pos -= 1;
                match std::str::from_utf8(&self.src[self.pos..]) {
                    Ok(s) => {
                        let ch = s.chars().next().unwrap();
                        out.push(ch);
                        self.pos += ch.len_utf8();
                    }
                    Err(_) => {
                        out.push('\u{fffd}');
                        self.pos += 1;
                    }
                }
            }
        }
        Ok(out)
    }

    fn take_hex(&mut self, n: usize) -> Result<u64> {
        let mut val = 0u64;
        for _ in 0..n {
            let Some(c) = self.peek() else { bail!("非法十六进制转义") };
            let d = (c as char).to_digit(16).ok_or_else(|| anyhow!("非法十六进制转义"))? as u64;
            val = val * 16 + d;
            self.pos += 1;
        }
        Ok(val)
    }

    fn parse_table(&mut self) -> Result<LValue> {
        self.expect(b'{')?;
        let mut sequence: Vec<(Option<LKey>, LValue)> = Vec::new();
        loop {
            self.skip_ws();
            match self.peek() {
                Some(b'}') => {
                    self.pos += 1;
                    break;
                }
                None => bail!("LSON 表未闭合"),
                _ => {}
            }
            let field = self.parse_field()?;
            sequence.push(field);
            self.skip_ws();
            match self.peek() {
                Some(b',') | Some(b';') => {
                    self.pos += 1;
                }
                Some(b'}') => {
                    self.pos += 1;
                    break;
                }
                None => bail!("LSON 表未闭合"),
                Some(c) => bail!("LSON 表内期望 ',' 或 '}}'，实际 '{}'", c as char),
            }
        }
        let has_positional = sequence.iter().any(|(k, _)| k.is_none());
        Ok(LValue::Table(LTable { sequence, has_positional }))
    }

    fn parse_field(&mut self) -> Result<(Option<LKey>, LValue)> {
        self.skip_ws();
        if self.peek() == Some(b'[') && !self.looks_like_long_string() {
            self.pos += 1;
            let key_val = self.parse_primitive()?;
            self.expect(b']')?;
            self.expect(b'=')?;
            let value = self.parse_expression()?;
            return Ok((
                Some(match key_val {
                    LValue::Str(s) => LKey::Str(s),
                    LValue::Num(n) => LKey::Num(n),
                    LValue::Bool(b) => LKey::Bool(b),
                    LValue::Nil => LKey::Nil,
                    LValue::Table(_) => bail!("LSON 不支持表作键"),
                }),
                value,
            ));
        }
        // NAME = expr 或位置字段
        let save = self.pos;
        if let Some(c) = self.peek() {
            if c.is_ascii_alphabetic() || c == b'_' {
                let start = self.pos;
                while self.pos < self.src.len() {
                    let c = self.src[self.pos] as char;
                    if c.is_ascii_alphanumeric() || c == '_' {
                        self.pos += 1;
                    } else {
                        break;
                    }
                }
                let name = std::str::from_utf8(&self.src[start..self.pos])?.to_string();
                let mut probe = self.pos;
                while probe < self.src.len() && (self.src[probe] as char).is_ascii_whitespace() {
                    probe += 1;
                }
                if self.src.get(probe) == Some(&b'=') && self.src.get(probe + 1) != Some(&b'=') {
                    self.pos = probe + 1;
                    let value = self.parse_expression()?;
                    return Ok((Some(LKey::Name(name)), value));
                }
                self.pos = save;
            }
        }
        let value = self.parse_expression()?;
        Ok((None, value))
    }
}

fn find_sub(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn parses_simple_map() {
        let v = parse_lson("{ a = 1, b = 'x', c = true }").unwrap();
        assert_eq!(v, json!({"a": 1.0, "b": "x", "c": true}));
    }

    #[test]
    fn parses_array() {
        let v = parse_lson("{ 'a', 'b', 'c' }").unwrap();
        assert_eq!(v, json!(["a", "b", "c"]));
    }

    #[test]
    fn parses_nested_with_bracket_keys() {
        let v = parse_lson("{ [\"false\"] = { [[a]], }, x = { ['k'] = 2.5 } }").unwrap();
        assert_eq!(v, json!({"false": ["a"], "x": {"k": 2.5}}));
    }

    #[test]
    fn numeric_keys_and_positional() {
        // {[2]="x", "a"} → JS: ret[2]="x", ret[1]="a" → shift → [ "a", "x" ]
        let v = parse_lson("{ [2] = \"x\", \"a\" }").unwrap();
        assert_eq!(v, json!(["a", "x"]));
    }

    #[test]
    fn comments_and_trailing_sep() {
        let v = parse_lson("{ --[[c]] a = 1, -- line\n b = 2, }").unwrap();
        assert_eq!(v, json!({"a": 1.0, "b": 2.0}));
    }

    #[test]
    fn string_escapes() {
        let v = parse_lson(r#"{ "\x41A\n\'q\'" }"#).unwrap();
        assert_eq!(v, json!(["AA\n'q'"]));
    }
}
