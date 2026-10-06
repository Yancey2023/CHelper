//! 对比资源包命令语法与 caidlist 的 mcpews 命令列表。

use crate::{command_patch::patched_command, jfmt::to_string_pretty4, parse_jsonc, write_crlf};
use anyhow::{bail, Context, Result};
use indexmap::{IndexMap, IndexSet};
use serde::Serialize;
use serde_json::Value;
use std::path::Path;

#[derive(Debug, Serialize)]
pub struct CommandCheckReport {
    pub edition: String,
    pub branch: String,
    pub applied: bool,
    pub changed: Vec<CommandDiff>,
    pub missing_resource_files: Vec<String>,
    pub obsolete_resource_files: Vec<String>,
    pub description_reminder: String,
}

#[derive(Debug, Serialize)]
pub struct CommandDiff {
    pub command: String,
    pub file: String,
    pub missing_syntax: Vec<String>,
    pub obsolete_syntax: Vec<String>,
    pub node_description_review: bool,
}

/// 将 `resources/<edition>/<branch>/command/*.json` 与 `result.commandList` 对比。
/// `apply` 更新单一主命令的语法，并应用已复核的 Core 格式 patch。
/// 其他节点和说明只能由逐命令 review 维护。
pub fn check_commands(
    resources_root: &Path,
    mcpews_path: &Path,
    edition: &str,
    branch: &str,
    apply: bool,
) -> Result<CommandCheckReport> {
    let mcpews_text = std::fs::read_to_string(mcpews_path)
        .with_context(|| format!("读取 mcpews 文件失败: {}", mcpews_path.display()))?;
    let mcpews: Value = parse_jsonc(&mcpews_text)
        .with_context(|| format!("解析 mcpews JSON 失败: {}", mcpews_path.display()))?;
    let command_list = mcpews
        .pointer("/result/commandList")
        .and_then(Value::as_array)
        .context("mcpews.json 缺少 result.commandList 字符串数组")?;

    let mut source = IndexMap::<String, IndexSet<String>>::new();
    for item in command_list {
        let Some(syntax) = item.as_str() else {
            continue;
        };
        let Some(command) = command_name(syntax) else {
            continue;
        };
        source
            .entry(command.to_ascii_lowercase())
            .or_default()
            .insert(syntax.to_string());
    }

    let command_dir = resources_root.join(edition).join(branch).join("command");
    if !command_dir.is_dir() {
        bail!("找不到命令资源目录: {}", command_dir.display());
    }
    let mut files = std::fs::read_dir(&command_dir)
        .with_context(|| format!("读取命令资源目录失败: {}", command_dir.display()))?
        .collect::<std::io::Result<Vec<_>>>()?;
    files.sort_by_key(|entry| entry.file_name());

    let mut represented = IndexSet::new();
    let mut changed = Vec::new();
    let mut obsolete_resource_files = Vec::new();
    for entry in files {
        let path = entry.path();
        if path.extension().and_then(|s| s.to_str()) != Some("json") {
            continue;
        }
        let Some(stem) = path.file_stem().and_then(|s| s.to_str()) else {
            continue;
        };
        let original = std::fs::read_to_string(&path)
            .with_context(|| format!("读取命令资源失败: {}", path.display()))?;
        let mut resource: Value = parse_jsonc(&original)
            .with_context(|| format!("解析命令资源 JSON 失败: {}", path.display()))?;
        let Some(object) = resource.as_object_mut() else {
            bail!("命令资源根节点必须是对象: {}", path.display());
        };
        // name 已包含别名；syntax 只保存一份主命令语法。
        let mut names = IndexSet::new();
        names.insert(stem.to_ascii_lowercase());
        if let Some(aliases) = object.get("name").and_then(Value::as_array) {
            names.extend(
                aliases
                    .iter()
                    .filter_map(Value::as_str)
                    .map(str::to_ascii_lowercase),
            );
        }
        let source_syntax = source
            .get(stem)
            .or_else(|| names.iter().find_map(|name| source.get(name)));
        if source_syntax.is_none() {
            obsolete_resource_files.push(stem.to_string());
            continue;
        }
        represented.extend(names.into_iter().filter(|name| source.contains_key(name)));
        let patch = patched_command(stem);
        let current: IndexSet<String> = if let Some(patch) = &patch {
            patch["syntax"]
                .as_array()
                .unwrap()
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_string)
                .collect()
        } else {
            source_syntax
                .unwrap()
                .iter()
                .map(|syntax| {
                    // When only an alias is exported upstream, preserve its arguments under the main name.
                    let source_name = command_name(syntax).unwrap();
                    syntax.replacen(&format!("/{source_name}"), &format!("/{stem}"), 1)
                })
                .collect()
        };
        let old = object
            .get("syntax")
            .and_then(Value::as_array)
            .context(format!("命令资源缺少 syntax 数组: {}", path.display()))?;
        let old_set: IndexSet<String> = old
            .iter()
            .filter_map(Value::as_str)
            .map(str::to_string)
            .collect();
        let patch_changed = patch
            .as_ref()
            .is_some_and(|patch| patch.as_object() != Some(&*object));
        if old_set == current && !patch_changed {
            continue;
        }
        let missing_syntax = current.difference(&old_set).cloned().collect();
        let obsolete_syntax = old_set.difference(&current).cloned().collect();
        if apply {
            if let Some(patch) = patch {
                *object = patch.as_object().unwrap().clone();
            } else {
                object.insert(
                    "syntax".into(),
                    Value::Array(current.iter().cloned().map(Value::String).collect()),
                );
            }
            write_crlf(&path, &to_string_pretty4(&resource))
                .with_context(|| format!("写入更新后的命令资源失败: {}", path.display()))?;
        }
        changed.push(CommandDiff {
            command: stem.to_string(),
            file: path.display().to_string(),
            missing_syntax,
            obsolete_syntax,
            node_description_review: stem != "execute",
        });
    }

    let missing_resource_files = source
        .keys()
        .filter(|key| !represented.contains(*key))
        .cloned()
        .collect();
    Ok(CommandCheckReport {
        edition: edition.to_string(),
        branch: branch.to_string(),
        applied: apply,
        changed,
        missing_resource_files,
        obsolete_resource_files,
        description_reminder: "请由 reviewer 逐命令依据 Wiki 核对参数语义，复用或重命名既有节点。节点和枚举 ID 需要清晰的作用说明，布尔两态分别解释；description 不包含类型限制、默认值或枚举引导句，也避免使用指向 Wiki 后续内容的“以下”，应按实际用途完整改写。syntax 只包含主命令；execute 自动应用已复核的 REPEAT patch。其他命令不会自动推导节点或说明，Wiki 原文位于 output/command_wiki。".into(),
    })
}

fn command_name(syntax: &str) -> Option<&str> {
    let token = syntax.trim().strip_prefix('/')?.split_whitespace().next()?;
    if token.is_empty() {
        None
    } else {
        Some(token)
    }
}
