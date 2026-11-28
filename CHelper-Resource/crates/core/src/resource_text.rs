use crate::{jfmt::to_string_pretty4, parse_jsonc, write_crlf};
use anyhow::{Context, Result};
use serde_json::Value;
use std::path::Path;

pub(crate) fn normalize_descriptions(value: &mut Value) -> bool {
    let mut changed = false;
    match value {
        Value::Object(fields) => {
            for (name, value) in fields {
                changed |= match (name.as_str(), value) {
                    (
                        "description" | "descriptionTrue" | "descriptionFalse",
                        Value::String(text),
                    ) => normalize_description(text),
                    (_, value) => normalize_descriptions(value),
                };
            }
        }
        Value::Array(items) => {
            for item in items {
                changed |= normalize_descriptions(item);
            }
        }
        _ => {}
    }
    changed
}

fn normalize_description(text: &mut String) -> bool {
    let end = text.trim_end().len();
    let mut start = end;
    while let Some(last) = text[..start].chars().next_back() {
        match last {
            '。' | '．' => start -= last.len_utf8(),
            '.' if !text[..start].ends_with("..") => start -= 1,
            _ => break,
        }
    }
    text.replace_range(start..end, "");
    if text.contains('。') {
        *text = text.replace('。', "；");
        return true;
    }
    start != end
}

pub(crate) fn normalize_exported_descriptions(directory: &Path) -> Result<()> {
    for entry in std::fs::read_dir(directory)? {
        let entry = entry?;
        let path = entry.path();
        if entry.file_type()?.is_dir() {
            normalize_exported_descriptions(&path)?;
        } else if path
            .extension()
            .is_some_and(|extension| extension == "json")
        {
            let text = std::fs::read_to_string(&path)?;
            let mut value = parse_jsonc(&text)
                .with_context(|| format!("解析导出的资源文件失败: {}", path.display()))?;
            if normalize_descriptions(&mut value) {
                write_crlf(&path, &to_string_pretty4(&value))?;
            }
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn normalizes_punctuation_in_nested_descriptions() {
        let mut resource = json!({
            "description": "第一句。第二句。\n",
            "content": [
                { "description": "Version 1.2.", "name": "Version 1.2." },
                { "description": "继续等待...。" },
                { "description": "是否继续？" },
                { "description": "全角句点．" },
                {
                    "descriptionTrue": "启用。保留其他设置。",
                    "descriptionFalse": "停用。",
                    "name": "布尔值。",
                },
            ],
        });

        assert!(normalize_descriptions(&mut resource));
        assert_eq!(resource["description"], "第一句；第二句\n");
        assert_eq!(resource["content"][0]["description"], "Version 1.2");
        assert_eq!(resource["content"][0]["name"], "Version 1.2.");
        assert_eq!(resource["content"][1]["description"], "继续等待...");
        assert_eq!(resource["content"][2]["description"], "是否继续？");
        assert_eq!(resource["content"][3]["description"], "全角句点");
        assert_eq!(
            resource["content"][4]["descriptionTrue"],
            "启用；保留其他设置"
        );
        assert_eq!(resource["content"][4]["descriptionFalse"], "停用");
        assert_eq!(resource["content"][4]["name"], "布尔值。");
        assert!(!normalize_descriptions(&mut resource));
    }
}
