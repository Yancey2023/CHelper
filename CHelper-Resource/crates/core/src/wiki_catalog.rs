//! Refresh catalog descriptions for IDs referenced by generated command nodes.

use crate::command_descriptions::{parameters_for_node, EnrichmentReport, ExtractedField};
use crate::command_doc::{WikiCommandDocument, WikiOption};
use crate::config::Config;
use crate::{jfmt::to_string_pretty4, parse_jsonc, write_crlf};
use anyhow::{bail, Context, Result};
use serde::Serialize;
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

#[derive(Debug, Serialize)]
pub struct CatalogDocumentation {
    pub edition: String,
    pub branch: String,
    pub catalog: String,
    pub command: String,
    pub excluded_ids: Vec<String>,
    pub proposal: String,
    pub report: EnrichmentReport,
}

fn referenced_ids(
    command: &Value,
    parent_key: Option<&str>,
    output: &mut BTreeSet<(String, String)>,
) {
    match command {
        Value::Object(object) => {
            if parent_key.is_none() {
                if let Some(nodes) = object.get("node") {
                    referenced_ids(nodes, None, output);
                    return;
                }
            }
            if let Some(key) = object.get("key").and_then(Value::as_str) {
                if let Some(node) = parent_key {
                    output.insert((key.to_string(), node.to_string()));
                }
            }
            for (name, value) in object {
                referenced_ids(value, parent_key.or(Some(name.as_str())), output);
            }
        }
        Value::Array(values) => {
            for value in values {
                referenced_ids(value, parent_key, output);
            }
        }
        _ => {}
    }
}

fn catalog_owners(
    resources: &Path,
    edition: &str,
    branch: &str,
) -> Result<BTreeMap<String, BTreeMap<String, BTreeSet<String>>>> {
    let directory = resources.join(edition).join(branch).join("command");
    let entries = match std::fs::read_dir(&directory) {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(BTreeMap::new()),
        Err(error) => {
            return Err(error)
                .with_context(|| format!("读取命令资源目录失败: {}", directory.display()))
        }
    };
    let mut owners = BTreeMap::<String, BTreeMap<String, BTreeSet<String>>>::new();
    for entry in entries {
        let entry = entry?;
        if !entry.file_type()?.is_file()
            || entry.path().extension().and_then(|v| v.to_str()) != Some("json")
        {
            continue;
        }
        let command_name = entry
            .path()
            .file_stem()
            .and_then(|v| v.to_str())
            .context("命令名不是UTF-8")?
            .to_string();
        let value: Value = serde_json::from_str(&std::fs::read_to_string(entry.path())?)
            .with_context(|| format!("解析命令资源失败: {}", entry.path().display()))?;
        let mut references = BTreeSet::new();
        if let Some(nodes) = value.get("node").and_then(Value::as_object) {
            for (node, definition) in nodes {
                referenced_ids(definition, Some(node), &mut references);
            }
        }
        for (id, node) in references {
            if id.is_empty()
                || !id
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
            {
                bail!("{command_name}: 无效的目录引用 {id}");
            }
            owners
                .entry(id)
                .or_default()
                .entry(command_name.clone())
                .or_default()
                .insert(node);
        }
    }
    Ok(owners)
}

fn collect_options(
    options: &[WikiOption],
    source: &str,
    output: &mut BTreeMap<String, Vec<ExtractedField>>,
) {
    for option in options {
        if !option.description.trim().is_empty() {
            for value in &option.values {
                output
                    .entry(value.clone())
                    .or_default()
                    .push(ExtractedField {
                        target: String::new(),
                        value: option.description.clone(),
                        sources: vec![format!("{source}:option@{}", option.line)],
                    });
            }
        }
        collect_options(&option.options, source, output);
    }
}

fn has_closed_options(parameter: &crate::command_doc::WikiParameter) -> bool {
    !parameter.options.is_empty()
        && (parameter.enumeration_intro.as_deref() == Some("必须为以下其中之一：")
            || parameter.description.contains("见下表"))
}

fn enrich_catalog(
    catalog: &mut Value,
    documents: &[(&str, &str, &WikiCommandDocument)],
) -> Result<(EnrichmentReport, Vec<String>)> {
    let mut candidates = BTreeMap::<String, Vec<ExtractedField>>::new();
    let mut unsupported = BTreeSet::new();
    for (_command, node, document) in documents {
        for parameter in parameters_for_node(node, document) {
            if parameter.edition.as_deref() == Some("je") || !has_closed_options(parameter) {
                continue;
            }
            let source = format!(
                "{}:parameter:{}@{}",
                document.source_url,
                parameter.names.join("|"),
                parameter.line
            );
            unsupported.extend(parameter.unavailable_values.iter().cloned());
            collect_options(&parameter.options, &source, &mut candidates);
        }
    }
    let content = catalog
        .get_mut("content")
        .and_then(Value::as_array_mut)
        .context("Wiki目录缺少content数组")?;
    content.retain(|item| {
        item.get("name")
            .and_then(Value::as_str)
            .is_none_or(|name| !unsupported.contains(name) || candidates.contains_key(name))
    });
    let mut report = EnrichmentReport::default();
    for (index, item) in content.iter_mut().enumerate() {
        let item = item.as_object_mut().context("Wiki目录项必须是对象")?;
        let name = item
            .get("name")
            .and_then(Value::as_str)
            .context("Wiki目录项缺少name")?;
        let fields = candidates.get(name).map(Vec::as_slice).unwrap_or_default();
        let distinct: BTreeSet<_> = fields.iter().map(|f| f.value.as_str()).collect();
        let target = format!("/content/{index}/description");
        if distinct.len() != 1 {
            if distinct.len() > 1 {
                report
                    .warnings
                    .push(format!("{target} ({name}): 多个Wiki选项用途有歧义"));
            }
            item.remove("description");
            report.missing_fields.push(target);
            continue;
        }
        let value = fields[0].value.clone();
        let sources: BTreeSet<_> = fields
            .iter()
            .flat_map(|f| f.sources.iter().cloned())
            .collect();
        item.insert("description".into(), Value::String(value.clone()));
        report.extracted_fields.push(ExtractedField {
            target,
            value,
            sources: sources.into_iter().collect(),
        });
    }
    let excluded = unsupported
        .into_iter()
        .filter(|id| !candidates.contains_key(id))
        .collect();
    Ok((report, excluded))
}

fn require_catalog_entries(name: &str, catalog: &Value) -> Result<()> {
    if catalog
        .get("content")
        .and_then(Value::as_array)
        .is_none_or(Vec::is_empty)
    {
        bail!("{name}: 已引用目录没有ID，不能把空目录视为说明提取完整；请检查版本数据及补充枚举");
    }
    Ok(())
}

fn catalog_input(resources: &Path, edition: &str, branch: &str, key: &str) -> PathBuf {
    resources
        .join(edition)
        .join(branch)
        .join("id")
        .join(format!("{key}.json"))
}

pub fn extract_catalog_docs(
    cfg: &Config,
    documents: &BTreeMap<String, WikiCommandDocument>,
    editions: &[String],
) -> Result<Vec<CatalogDocumentation>> {
    let resources = cfg.project_root.join("resources");
    let mut reports = Vec::new();
    for edition in editions {
        for branch in ["vanilla", "experiment"] {
            let owners = catalog_owners(&resources, edition, branch)?;
            for (key, command_nodes) in owners {
                let input = catalog_input(&resources, edition, branch, &key);
                let mut relevant = Vec::new();
                for (command, nodes) in &command_nodes {
                    if let Some(doc) = documents.get(command) {
                        for node in nodes {
                            if parameters_for_node(node, doc)
                                .iter()
                                .any(|p| has_closed_options(p))
                            {
                                relevant.push((command.as_str(), node.as_str(), doc));
                            }
                        }
                    }
                }
                if relevant.is_empty() {
                    continue;
                }
                let mut catalog = parse_jsonc(
                    &std::fs::read_to_string(&input)
                        .with_context(|| format!("读取引用目录失败: {}", input.display()))?,
                )?;
                let (report, excluded_ids) = enrich_catalog(&mut catalog, &relevant)?;
                require_catalog_entries(&key, &catalog)?;
                let output = cfg
                    .command_docs_dir
                    .join("catalogs")
                    .join(edition)
                    .join(branch);
                std::fs::create_dir_all(&output)?;
                let proposal = output.join(format!("{key}.json"));
                write_crlf(&proposal, &to_string_pretty4(&catalog))?;
                std::fs::write(
                    output.join(format!("{key}.sources.json")),
                    serde_json::to_string_pretty(&report)?,
                )?;
                reports.push(CatalogDocumentation {
                    edition: edition.clone(),
                    branch: branch.into(),
                    catalog: key,
                    command: command_nodes.keys().cloned().collect::<Vec<_>>().join(","),
                    excluded_ids,
                    proposal: proposal.display().to_string(),
                    report,
                });
            }
        }
    }
    Ok(reports)
}

pub fn refresh_generated_catalogs(
    cfg: &Config,
    output: &Path,
    edition: &str,
    branch: &str,
) -> Result<()> {
    let resources = cfg.project_root.join("resources");
    let owners = catalog_owners(&resources, edition, branch)?;
    for (key, command_nodes) in owners {
        let mut owned_documents = Vec::new();
        for (command, nodes) in &command_nodes {
            let path = cfg.command_wiki_dir.join(command).join("extracted.json");
            let doc: WikiCommandDocument =
                serde_json::from_str(&std::fs::read_to_string(&path)?)
                    .with_context(|| format!("解析Wiki命令文档失败: {}", path.display()))?;
            for node in nodes {
                if parameters_for_node(node, &doc)
                    .iter()
                    .any(|p| has_closed_options(p))
                {
                    owned_documents.push((command.clone(), node.clone(), doc.clone()));
                }
            }
        }
        if owned_documents.is_empty() {
            continue;
        }
        let docs: Vec<_> = owned_documents
            .iter()
            .map(|(name, node, doc)| (name.as_str(), node.as_str(), doc))
            .collect();
        let path = output.join(format!("{key}.json"));
        let mut catalog = parse_jsonc(
            &std::fs::read_to_string(&path)
                .with_context(|| format!("读取生成目录失败: {}", path.display()))?,
        )?;
        require_catalog_entries(&key, &catalog)?;
        let (report, _) = enrich_catalog(&mut catalog, &docs)?;
        require_catalog_entries(&key, &catalog)?;
        let diagnostics = cfg
            .command_docs_dir
            .join("generated_catalogs")
            .join(edition)
            .join(branch);
        std::fs::create_dir_all(&diagnostics)?;
        write_crlf(
            &diagnostics.join(format!("{key}.json")),
            &to_string_pretty4(&catalog),
        )?;
        std::fs::write(
            diagnostics.join(format!("{key}.sources.json")),
            serde_json::to_string_pretty(&report)?,
        )?;
        if !report.missing_fields.is_empty() {
            let missing_ids: Vec<_> = report
                .missing_fields
                .iter()
                .map(|pointer| {
                    pointer
                        .rsplit_once('/')
                        .and_then(|(owner, _)| catalog.pointer(owner))
                        .and_then(|item| item.get("name"))
                        .and_then(Value::as_str)
                        .unwrap_or(pointer)
                })
                .collect();
            bail!(
                "{edition}/{branch}/{key}: {}个目录ID缺少明确Wiki用途，禁止导出: {}",
                missing_ids.len(),
                missing_ids.join(", ")
            );
        }
        write_crlf(&path, &to_string_pretty4(&catalog))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::wiki_extract::parse_document;

    #[test]
    fn referenced_catalog_cannot_pass_validation_with_no_ids() {
        assert!(require_catalog_entries("slots", &serde_json::json!({"content":[]})).is_err());
        assert!(require_catalog_entries("slots", &serde_json::json!({})).is_err());
        assert!(require_catalog_entries(
            "slots",
            &serde_json::json!({"content":[{"name":"known"}]})
        )
        .is_ok());
    }

    #[test]
    fn catalog_refresh_removes_unsourced_labels_and_preserves_identity() {
        let doc = parse_document("demo", "wiki", "示例。\n==参数==\n{{argument|slot|enum|be=1}}\n: 指定槽位。\n: 必须为以下其中之一：\n:* <code>head</code> — 头盔\n", 12).unwrap();
        let mut catalog = serde_json::json!({"id":"slots","type":"normal","content":[{"name":"head","description":"旧","extra":1},{"name":"unknown","description":"猜测"}]});
        let (report, _) = enrich_catalog(&mut catalog, &[("demo", "<slot: enum>", &doc)]).unwrap();
        assert_eq!(report.missing_fields, vec!["/content/1/description"]);
        assert_eq!(catalog["content"][0]["description"], "头盔");
        assert_eq!(catalog["content"][0]["extra"], 1);
        assert_eq!(catalog["content"][1]["name"], "unknown");
        assert!(catalog["content"][1].get("description").is_none());
        assert_eq!(catalog["id"], "slots");
    }

    #[test]
    fn shared_catalog_requires_consistent_option_meanings() {
        let doc = parse_document("demo", "wiki", "示例。\n==参数==\n{{argument|first|enum|be=1}}\n: 必须为以下其中之一：\n:* <code>same</code> — 第一种含义\n{{argument|second|enum|be=1}}\n: 必须为以下其中之一：\n:* <code>same</code> — 第二种含义\n", 12).unwrap();
        let mut catalog = serde_json::json!({"content":[{"name":"same","description":"旧"}]});
        let (report, _) = enrich_catalog(
            &mut catalog,
            &[("demo", "first", &doc), ("demo", "second", &doc)],
        )
        .unwrap();
        assert_eq!(report.missing_fields.len(), 1);
        assert_eq!(report.warnings.len(), 1);
        assert!(catalog["content"][0].get("description").is_none());
    }

    #[test]
    fn reviewed_exclusions_do_not_hide_unknown_ids_or_reorder_valid_entries() {
        let doc = parse_document("demo", "wiki", "示例。\n==参数==\n{{argument|slot|enum|be=1}}\n: 必须为以下其中之一：\n:* <code>first</code> — 第一项\n:* <code>last</code> — 末项\n", 12).unwrap();
        let mut catalog = serde_json::json!({"id":"slots","type":"normal","content":[{"name":"first","description":"旧","extra":1},{"name":"unsupported","description":"旧"},{"name":"unknown","description":"无来源"},{"name":"last","description":"旧"}]});
        let (report, _) = enrich_catalog(&mut catalog, &[("demo", "slot", &doc)]).unwrap();
        let names: Vec<_> = catalog["content"]
            .as_array()
            .unwrap()
            .iter()
            .map(|item| item["name"].as_str().unwrap())
            .collect();
        assert_eq!(names, vec!["first", "unsupported", "unknown", "last"]);
        assert_eq!(catalog["content"][0]["extra"], 1);
        assert_eq!(catalog["content"][0]["description"], "第一项");
        assert_eq!(catalog["content"][3]["description"], "末项");
        assert_eq!(
            report.missing_fields,
            vec!["/content/1/description", "/content/2/description"]
        );
    }

    #[test]
    fn excluding_every_id_cannot_make_an_empty_catalog_publishable() {
        let doc = parse_document("demo", "wiki", "示例。\n==参数==\n", 12).unwrap();
        let mut catalog = serde_json::json!({"content":[{"name":"unsupported"}]});
        let (report, _) = enrich_catalog(&mut catalog, &[("demo", "slot", &doc)]).unwrap();
        assert_eq!(report.missing_fields.len(), 1);
        assert!(require_catalog_entries("slots", &catalog).is_ok());
    }

    #[test]
    fn command_nodes_define_catalog_owners() {
        let mut ids = BTreeSet::new();
        referenced_ids(
            &serde_json::json!({"<ease: Easing>":{"key":"cameraEasing"},"<preset: string>":{"key":"cameraPreset"}}),
            None,
            &mut ids,
        );
        assert_eq!(
            ids,
            BTreeSet::from([
                ("cameraEasing".to_string(), "<ease: Easing>".to_string()),
                ("cameraPreset".to_string(), "<preset: string>".to_string())
            ])
        );
        assert_eq!(
            crate::command_descriptions::short_parameter_name("control scheme: controlscheme"),
            "control scheme"
        );
    }
}
