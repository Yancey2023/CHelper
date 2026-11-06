//! Wiki source patches, extracted documents, provenance and resource proposals.

use crate::command_descriptions::{enrich_command, EnrichmentReport};
use crate::command_doc::WikiCommandDocument;
use crate::config::Config;
use crate::wiki_extract::parse_document;
use crate::wiki_patch::apply_page_patch_for_title;
use crate::{jfmt::to_string_pretty4, parse_jsonc, write_crlf};
use anyhow::{bail, Context, Result};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

#[derive(Debug, Default, Serialize)]
pub struct DocumentationReport {
    pub commands: usize,
    pub patched_pages: usize,
    pub resource_files: usize,
    pub unresolved_fields: usize,
    pub unresolved_catalog_fields: usize,
    pub warnings: usize,
    pub applied: bool,
    pub output: String,
    pub pages: Vec<PageProvenance>,
    pub resources: Vec<ResourceDocumentation>,
    pub catalogs: Vec<crate::wiki_catalog::CatalogDocumentation>,
}

#[derive(Debug, Serialize)]
pub struct PageProvenance {
    pub command: String,
    pub source_url: String,
    pub source_status: String,
    pub page_metadata: Vec<crate::command_doc::WikiPageMetadata>,
    pub original_sha256: String,
    pub patched_sha256: String,
    pub patch_file: Option<String>,
    pub patch_reason: Option<String>,
    pub replacement_reasons: Vec<Option<String>>,
    pub replacements: usize,
    pub warnings: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct ResourceDocumentation {
    pub edition: String,
    pub branch: String,
    pub command: String,
    pub proposal: String,
    pub report: EnrichmentReport,
}

/// Prepare source-derived definitions without mutating resources by default.
/// Applying is all-or-nothing with respect to missing source fields: proposals and
/// diagnostics are written first, then the complete batch is checked before writes.
pub fn extract_command_docs(
    cfg: &Config,
    editions: &[String],
    selected_commands: &[String],
    apply: bool,
) -> Result<DocumentationReport> {
    if cfg.max_brief_chars == 0 {
        bail!("max_brief_chars 必须大于0");
    }
    let selected: BTreeSet<_> = selected_commands.iter().cloned().collect();
    let mut inputs = Vec::new();
    let mut commands = BTreeSet::new();
    for edition in editions {
        if !matches!(edition.as_str(), "release" | "beta" | "netease") {
            bail!("未知版本: {edition}");
        }
        for branch in ["vanilla", "experiment"] {
            let root = cfg
                .project_root
                .join("resources")
                .join(edition)
                .join(branch)
                .join("command");
            let mut entries = std::fs::read_dir(&root)?.collect::<std::io::Result<Vec<_>>>()?;
            entries.sort_by_key(|entry| entry.file_name());
            for entry in entries {
                let path = entry.path();
                if !entry.file_type()?.is_file()
                    || path.extension().and_then(|s| s.to_str()) != Some("json")
                {
                    continue;
                }
                let command = path
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .context("命令文件名不是UTF-8")?
                    .to_string();
                if !selected.is_empty() && !selected.contains(&command) {
                    continue;
                }
                commands.insert(command.clone());
                inputs.push((edition.clone(), branch.to_string(), command, path));
            }
        }
    }
    for selected in &selected {
        if !commands.contains(selected) {
            bail!("所选版本不存在命令: {selected}");
        }
    }
    std::fs::create_dir_all(&cfg.command_docs_dir)?;
    let mut documents = BTreeMap::<String, WikiCommandDocument>::new();
    let mut pages = Vec::new();
    for command in &commands {
        let page_dir = cfg.command_wiki_dir.join(command);
        let source: serde_json::Value =
            serde_json::from_str(&std::fs::read_to_string(page_dir.join("source.json"))?)?;
        let source_status = source["status"].as_str().unwrap_or("unavailable");
        if source_status == "unavailable" {
            bail!("{command}: Wiki 页面不可用，不能生成说明");
        }
        let source_url = source["source_url"]
            .as_str()
            .context("Wiki source.json缺少source_url")?;
        let original = std::fs::read_to_string(page_dir.join("page.wiki"))?;
        let patched = apply_page_patch_for_title(
            command,
            source["resolved_title"].as_str(),
            &original,
            &cfg.wiki_patches_dir,
        )?;
        let document = parse_document(command, source_url, &patched.text, cfg.max_brief_chars)
            .with_context(|| format!("提取 {command} 的 Wiki 原文失败"))?;
        std::fs::write(page_dir.join("patched.wiki"), &patched.text)?;
        std::fs::write(
            page_dir.join("extracted.json"),
            serde_json::to_string_pretty(&document)?,
        )?;
        for warning in &document.warnings {
            tracing::warn!(command, "{warning}");
        }
        pages.push(PageProvenance {
            command: command.clone(),
            source_url: source_url.to_string(),
            source_status: source_status.to_string(),
            page_metadata: document.page_metadata.clone(),
            original_sha256: hex::encode(Sha256::digest(original.as_bytes())),
            patched_sha256: hex::encode(Sha256::digest(patched.text.as_bytes())),
            patch_file: patched.patch_file,
            patch_reason: patched.patch_reason,
            replacement_reasons: patched.replacement_reasons,
            replacements: patched.replacements,
            warnings: document.warnings.clone(),
        });
        documents.insert(command.clone(), document);
    }
    if let Some(document) = documents.get("gamemode") {
        let mut catalog = parse_jsonc(&std::fs::read_to_string(
            cfg.data_dir.join("game_mode.json"),
        )?)?;
        let sources = enrich_option_catalog(&mut catalog, document, "gameMode")?;
        let directory = cfg.command_docs_dir.join("catalogs");
        std::fs::create_dir_all(&directory)?;
        std::fs::write(
            directory.join("gameMode.json"),
            serde_json::to_string_pretty(&crate::chelper::game_mode_document(&catalog))?,
        )?;
        std::fs::write(
            directory.join("gameMode.sources.json"),
            serde_json::to_string_pretty(&sources)?,
        )?;
    }
    let mut resources = Vec::new();
    let mut pending = Vec::<(PathBuf, String)>::new();
    for (edition, branch, command, source_path) in inputs {
        let mut resource = parse_jsonc(&std::fs::read_to_string(&source_path)?)?;
        let report = enrich_command(&mut resource, &documents[&command], cfg.max_brief_chars)
            .with_context(|| format!("{edition}/{branch}/{command}: 匹配 Wiki 说明失败"))?;
        for warning in &report.warnings {
            tracing::warn!(edition, branch, command, "{warning}");
        }
        let serialized = to_string_pretty4(&resource);
        let directory = cfg.command_docs_dir.join(&edition).join(&branch);
        let proposal = directory.join(format!("{command}.json"));
        write_crlf(&proposal, &serialized)?;
        std::fs::write(
            directory.join(format!("{command}.sources.json")),
            serde_json::to_string_pretty(&report)?,
        )?;
        pending.push((source_path, serialized));
        resources.push(ResourceDocumentation {
            edition,
            branch,
            command,
            proposal: proposal.display().to_string(),
            report,
        });
    }
    let catalogs = crate::wiki_catalog::extract_catalog_docs(cfg, &documents, editions)?;
    let mut report = DocumentationReport {
        commands: documents.len(),
        patched_pages: pages
            .iter()
            .filter(|page| page.patch_file.is_some())
            .count(),
        resource_files: resources.len(),
        unresolved_fields: resources
            .iter()
            .map(|r| r.report.missing_fields.len())
            .sum(),
        unresolved_catalog_fields: catalogs
            .iter()
            .map(|catalog| catalog.report.missing_fields.len())
            .sum(),
        warnings: resources
            .iter()
            .map(|r| r.report.warnings.len())
            .sum::<usize>()
            + pages.iter().map(|p| p.warnings.len()).sum::<usize>()
            + catalogs
                .iter()
                .map(|catalog| catalog.report.warnings.len())
                .sum::<usize>(),
        applied: false,
        output: cfg.command_docs_dir.display().to_string(),
        pages,
        resources,
        catalogs,
    };
    write_report(cfg, &report)?;
    if apply {
        require_complete(&report)?;
        for (path, content) in pending {
            write_crlf(&path, &content)?;
        }
        for catalog in &report.catalogs {
            let destination = cfg
                .project_root
                .join("resources")
                .join(&catalog.edition)
                .join(&catalog.branch)
                .join("id")
                .join(format!("{}.json", catalog.catalog));
            std::fs::copy(&catalog.proposal, &destination).with_context(|| {
                format!("写入自动提取的目录说明失败: {}", destination.display())
            })?;
        }
        report.applied = true;
        write_report(cfg, &report)?;
    }
    Ok(report)
}

/// Refresh an existing option catalog from a parameter's documented values and
/// aliases. Structure and ordering remain owned by the catalog's maintenance source.
pub fn enrich_option_catalog(
    catalog: &mut serde_json::Value,
    document: &WikiCommandDocument,
    parameter_name: &str,
) -> Result<Vec<crate::command_descriptions::ExtractedField>> {
    let mut sources = BTreeMap::<String, (String, Vec<String>)>::new();
    for parameter in &document.parameters {
        if parameter.edition.as_deref() == Some("je")
            || !parameter.names.iter().any(|name| name == parameter_name)
        {
            continue;
        }
        for option in &parameter.options {
            // “代表” introduces the definition after an optional alias clause.
            // Take the contiguous original definition, without drafting new prose.
            let definition = option
                .description
                .split_once("代表")
                .map_or(option.description.as_str(), |(_, definition)| definition)
                .trim();
            if definition.is_empty() {
                continue;
            }
            for value in &option.values {
                let source = format!(
                    "{}:{}@{}:option@{}",
                    document.source_url, parameter_name, parameter.line, option.line
                );
                if let Some((previous, paths)) = sources.get_mut(value) {
                    if previous != definition {
                        bail!("{value}: Wiki目录定义有歧义");
                    }
                    paths.push(source);
                } else {
                    sources.insert(value.clone(), (definition.to_string(), vec![source]));
                }
            }
        }
    }
    let content = catalog
        .get_mut("content")
        .and_then(serde_json::Value::as_array_mut)
        .context("选项目录缺少content数组")?;
    let mut pending = Vec::new();
    for (index, item) in content.iter().enumerate() {
        let name = item
            .get("name")
            .and_then(serde_json::Value::as_str)
            .context("目录项缺少name")?;
        let (text, paths) = sources
            .get(name)
            .with_context(|| format!("{name}: 目录项缺少Wiki选项来源"))?;
        pending.push((index, text.clone(), paths.clone()));
    }
    let mut fields = Vec::new();
    for (index, text, paths) in pending {
        content[index]
            .as_object_mut()
            .context("目录项必须为object")?
            .insert(
                "description".into(),
                serde_json::Value::String(text.clone()),
            );
        fields.push(crate::command_descriptions::ExtractedField {
            target: format!("/content/{index}/description"),
            value: text,
            sources: paths,
        });
    }
    Ok(fields)
}

pub fn require_complete(report: &DocumentationReport) -> Result<()> {
    if report.unresolved_fields != 0 || report.unresolved_catalog_fields != 0 {
        bail!(
            "Wiki说明有{}个命令字段、{}个自动关联目录字段缺少明确来源，禁止应用或生成资源包；请检查 {}/index.json",
            report.unresolved_fields,
            report.unresolved_catalog_fields,
            report.output
        );
    }
    Ok(())
}

/// Copy proposals only after the whole extraction report has complete field sources.
pub fn export_documented_commands(
    report: &DocumentationReport,
    edition: &str,
    branch: &str,
    output: &Path,
) -> Result<()> {
    require_complete(report)?;
    for resource in report
        .resources
        .iter()
        .filter(|r| r.edition == edition && r.branch == branch)
    {
        std::fs::copy(
            &resource.proposal,
            output
                .join("command")
                .join(format!("{}.json", resource.command)),
        )?;
    }
    Ok(())
}

fn write_report(cfg: &Config, report: &DocumentationReport) -> Result<()> {
    std::fs::write(
        cfg.command_docs_dir.join("index.json"),
        serde_json::to_string_pretty(report)?,
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unresolved_reviewed_catalog_also_blocks_publication() {
        let mut report = DocumentationReport::default();
        assert!(require_complete(&report).is_ok());
        report.unresolved_catalog_fields = 1;
        assert!(require_complete(&report).is_err());
    }

    #[test]
    fn catalog_uses_option_definitions_and_aliases_without_changing_structure() {
        let document = parse_document("demo", "test", "示例。\n==语法==\n: <code>/demo <mode: Mode></code>\n==参数==\n{{argument|mode|Mode|be=1}}\n: 指定模式。\n: 必须为以下其中之一：\n:* <code>example</code>（可简写为{{cd|e}}）代表示例模式\n", 12).unwrap();
        let mut catalog = serde_json::json!({"content":[{"name":"example","metadata":1},{"name":"e","metadata":2}]});
        let sources = enrich_option_catalog(&mut catalog, &document, "mode").unwrap();
        assert_eq!(sources.len(), 2);
        assert_eq!(catalog["content"][1]["description"], "示例模式");
        assert_eq!(catalog["content"][1]["metadata"], 2);
        let runtime = crate::chelper::game_mode_document(&catalog);
        assert_eq!(runtime["id"], "gameMode");
        assert_eq!(runtime["type"], "normal");
        assert_eq!(runtime["content"], catalog["content"]);
        assert_eq!(
            crate::chelper::game_mode_document(&catalog["content"]),
            runtime
        );
        let mut incomplete = serde_json::json!({"content":[{"name":"example","description":"旧"},{"name":"unknown"}]});
        let previous = incomplete.clone();
        assert!(enrich_option_catalog(&mut incomplete, &document, "mode").is_err());
        assert_eq!(incomplete, previous);
    }
}
