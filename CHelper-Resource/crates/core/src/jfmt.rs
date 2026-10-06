//! 与 JavaScript `JSON.stringify(value, null, 4)` 兼容的 JSON 格式化器。
//!
//! 差异点处理：
//! - 整数值的浮点数按 JS 规则输出为整数（`1.0` → `1`）；
//! - 键顺序由 `serde_json` 的 `preserve_order` 特性保证（插入序）；
//! - `Option`/`Value::Null` 字段在构造 Value 时就应当省略（对齐 JS `undefined` 字段不序列化的行为）；
//! - 输出为 LF，最终写文件时统一替换为 CRLF（见 [`crate::write_crlf`]）。

use serde_json::Value;

pub fn to_string_pretty4(value: &Value) -> String {
    let mut out = String::new();
    write_value(&mut out, value, 0);
    out
}

fn indent(depth: usize) -> usize {
    depth * 4
}

fn write_value(out: &mut String, value: &Value, depth: usize) {
    match value {
        Value::Object(map) => {
            if map.is_empty() {
                out.push_str("{}");
                return;
            }
            out.push_str("{\n");
            for (i, (k, v)) in map.iter().enumerate() {
                if i > 0 {
                    out.push_str(",\n");
                }
                for _ in 0..indent(depth + 1) {
                    out.push(' ');
                }
                out.push_str(&serde_json::to_string(k).unwrap());
                out.push_str(": ");
                write_value(out, v, depth + 1);
            }
            out.push('\n');
            for _ in 0..indent(depth) {
                out.push(' ');
            }
            out.push('}');
        }
        Value::Array(items) => {
            if items.is_empty() {
                out.push_str("[]");
                return;
            }
            out.push_str("[\n");
            for (i, v) in items.iter().enumerate() {
                if i > 0 {
                    out.push_str(",\n");
                }
                for _ in 0..indent(depth + 1) {
                    out.push(' ');
                }
                write_value(out, v, depth + 1);
            }
            out.push('\n');
            for _ in 0..indent(depth) {
                out.push(' ');
            }
            out.push(']');
        }
        Value::String(_) | Value::Bool(_) | Value::Null => {
            out.push_str(&value.to_string());
        }
        Value::Number(n) => {
            // JS 中整数值的 Number 输出没有小数部分。
            if let Some(f) = n.as_f64() {
                if n.is_f64() && f.fract() == 0.0 && f.abs() < 1e15 {
                    out.push_str(&format!("{}", f as i64));
                    return;
                }
            }
            out.push_str(&n.to_string());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn formats_like_js() {
        let v = json!({"id": "cameraEasing", "type": "normal", "content": [{"name": "a"}, {"name": "b", "description": "描述"}]});
        let expected = "{\n    \"id\": \"cameraEasing\",\n    \"type\": \"normal\",\n    \"content\": [\n        {\n            \"name\": \"a\"\n        },\n        {\n            \"name\": \"b\",\n            \"description\": \"描述\"\n        }\n    ]\n}";
        assert_eq!(to_string_pretty4(&v), expected);
    }

    #[test]
    fn integral_floats() {
        assert_eq!(to_string_pretty4(&json!(1.0)), "1");
        assert_eq!(to_string_pretty4(&json!(1.5)), "1.5");
    }

    #[test]
    fn empty_containers() {
        assert_eq!(to_string_pretty4(&json!({})), "{}");
        assert_eq!(to_string_pretty4(&json!([])), "[]");
    }
}
