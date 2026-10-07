//! Reviewed resource patches required by Core's resource format.

use crate::{jfmt::to_string_pretty4, parse_jsonc, write_crlf};
use anyhow::{Context, Result};
use serde_json::Value;
use std::path::Path;

const EXECUTE_PATCH: &str = include_str!("../../../data/command_patches/execute.json");

/// Keep execute's original REPEAT entry and descriptions when synchronizing mcpews.
/// The subcommand grammar comes from data/resource_definitions/repeat/execute.json.
pub fn patched_command(command: &str) -> Option<Value> {
    match command {
        "execute" => Some(serde_json::from_str(EXECUTE_PATCH).expect("invalid execute patch")),
        _ => None,
    }
}

/// Export reviewed command structures; static definitions are loaded from data.
/// The pipeline subsequently replaces command text with source-derived proposals.
pub fn write_reviewed_commands(source: &Path, output: &Path) -> Result<usize> {
    let source_dir = source.join("command");
    if source_dir.is_dir() {
        copy_reviewed_directory(&source_dir, &output.join("command"))
    } else {
        Ok(0)
    }
}

/// Export the separately packed legacy block conversion data from the same reviewed source.
pub fn write_old2new_resources(source: &Path, output: &Path) -> Result<usize> {
    copy_reviewed_directory(&source.join("old2new"), &output.join("old2new"))
}

/// The pinned international 1.21.50 snapshot does not describe China's existing place extensions.
/// Keep their reviewed catalogs rather than inventing IDs or removing the command's capabilities.
pub fn write_netease_extension_ids(source: &Path, output: &Path) -> Result<usize> {
    let mut files = 0;
    for category in ["feature", "featureRule"] {
        let filename = format!("{category}.json");
        let target = output.join(&filename);
        let input = source.join(&filename);
        parse_jsonc(&std::fs::read_to_string(&input)?)
            .with_context(|| format!("解析网易扩展 ID 失败: {}", input.display()))?;
        std::fs::copy(&input, target)
            .with_context(|| format!("导出网易扩展 ID 失败: {}", input.display()))?;
        files += 1;
    }
    Ok(files)
}

fn copy_reviewed_directory(source: &Path, output: &Path) -> Result<usize> {
    std::fs::create_dir_all(output)?;
    let mut files = 0;
    for entry in std::fs::read_dir(source)? {
        let entry = entry?;
        let source_path = entry.path();
        let output_path = output.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            files += copy_reviewed_directory(&source_path, &output_path)?;
        } else if entry.file_type()?.is_file() {
            let patch = if source.file_name().and_then(|s| s.to_str()) == Some("command") {
                source_path
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .and_then(patched_command)
            } else {
                None
            };
            if let Some(patch) = patch {
                // Parse the input as well, so a damaged reviewed source is never hidden by a patch.
                parse_jsonc(&std::fs::read_to_string(&source_path)?)
                    .with_context(|| format!("解析命令资源失败: {}", source_path.display()))?;
                write_crlf(&output_path, &to_string_pretty4(&patch))?;
            } else {
                std::fs::copy(&source_path, &output_path)
                    .with_context(|| format!("复制已复核资源失败: {}", source_path.display()))?;
            }
            files += 1;
        }
    }
    // Reconcile files only within this known output directory; stale commands must not survive.
    for entry in std::fs::read_dir(output)? {
        let entry = entry?;
        if entry.file_type()?.is_file() && !source.join(entry.file_name()).exists() {
            std::fs::remove_file(entry.path())?;
        }
    }
    Ok(files)
}
