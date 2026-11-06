//! Attach source-derived Wiki text to existing command resource fields.
//!
//! Matching is deliberately conservative: when a resource field cannot be traced to
//! an unambiguous Wiki passage, its old value is left in place and the field is reported
//! as missing so the caller can reject publication.

use crate::command_doc::{WikiCommandDocument, WikiOption, WikiParameter, WikiSyntaxDescription};
use anyhow::{bail, Result};
use serde::Serialize;
use serde_json::Value;
use std::collections::BTreeSet;

#[derive(Debug, Clone, Default, Serialize)]
pub struct EnrichmentReport {
    pub missing_fields: Vec<String>,
    pub warnings: Vec<String>,
    pub extracted_fields: Vec<ExtractedField>,
}

#[derive(Debug, Clone, Serialize)]
pub struct ExtractedField {
    pub target: String,
    pub value: String,
    pub sources: Vec<String>,
}

#[derive(Debug, Clone)]
struct Passage {
    text: String,
    source: String,
}

/// Enrich documentation beneath existing nodes and IDs, adding required text
/// fields when absent without creating new syntax nodes.
///
/// Resource syntax, node names, types, versions, and other metadata are never changed.
pub fn enrich_command(
    resource: &mut Value,
    document: &WikiCommandDocument,
    max_brief_chars: usize,
) -> Result<EnrichmentReport> {
    if !resource.is_object() {
        bail!("命令资源根节点必须是JSON对象");
    }
    let mut report = EnrichmentReport::default();

    if document.command == "execute" {
        *resource = crate::command_patch::patched_command("execute")
            .expect("execute command patch must exist");
        let mut preserved = Vec::new();
        collect_preserved_text(resource, "", &mut preserved);
        report
            .extracted_fields
            .extend(preserved.into_iter().map(|(target, value)| ExtractedField {
                target,
                value,
                sources: vec!["fixed resource exception: data/command_patches/execute.json".into()],
            }));
        return Ok(report);
    }

    {
        resolve_target(
            resource,
            "/description",
            TargetKind::Intro,
            None,
            document,
            max_brief_chars,
            &mut report,
        );
    }

    let Some(nodes) = resource.get("node").and_then(Value::as_object) else {
        return Ok(report);
    };
    let node_keys: Vec<String> = nodes.keys().cloned().collect();
    for node_key in node_keys {
        let pointer = format!("/node/{}", escape_pointer(&node_key));
        let Some(node) = resource.pointer(&pointer).cloned() else {
            continue;
        };
        if node.get("units").is_some()
            && node.get("type").and_then(Value::as_str) != Some("INTEGER_WITH_UNIT")
        {
            bail!(
                "{}: {node_key} 声明了 units，但类型不是 INTEGER_WITH_UNIT，Core 打包会丢失单位说明",
                document.command
            );
        }
        let scoped_document = document_for_node(resource, document, &node_key);
        let mut parameters = parameters_for_node(&node_key, &scoped_document);
        if parameters.is_empty() && !node_key.contains(['<', '[', ':']) {
            let dedicated = scoped_document.syntax_descriptions.iter().any(|source| {
                !source.description.trim().is_empty()
                    && literal_words(&source.syntax)
                        .last()
                        .is_some_and(|word| word == &node_key)
            });
            if !dedicated {
                parameters = following_parameters(resource, &node_key, &scoped_document);
            }
        }
        let param_ref = if parameters.is_empty() {
            None
        } else {
            Some(parameters.as_slice())
        };
        let mut targets = Vec::new();
        collect_documentation_targets(&node, &pointer, false, false, &mut targets);
        if node.get("description").is_none()
            && !targets
                .iter()
                .any(|target| target.pointer == format!("{pointer}/description"))
        {
            targets.push(CollectedTarget {
                pointer: format!("{pointer}/description"),
                kind: CollectedKind::Parameter,
            });
        }
        if node.get("brief").is_none()
            && !parameter_names_in_key(&node_key).is_empty()
            && !(node.get("type").and_then(Value::as_str) == Some("TEXT")
                && !node_key.contains(['<', '[']))
        {
            targets.push(CollectedTarget {
                pointer: format!("{pointer}/brief"),
                kind: CollectedKind::Brief,
            });
        }
        for target in targets {
            let kind = match &target.kind {
                CollectedKind::Parameter => {
                    let owner = target
                        .pointer
                        .rsplit_once('/')
                        .and_then(|(owner, _)| resource.pointer(owner));
                    TargetKind::Parameter {
                        boolean: owner.is_some_and(|value| has_value_type(value, "BOOLEAN")),
                        numeric: owner.is_some_and(|value| {
                            has_value_type(value, "INTEGER") || has_value_type(value, "FLOAT")
                        }),
                    }
                }
                CollectedKind::Brief => TargetKind::Brief,
                CollectedKind::Option(option) => TargetKind::Option(option.as_str()),
                CollectedKind::Unit(unit) => TargetKind::Unit(unit.as_str()),
            };
            resolve_target(
                resource,
                &target.pointer,
                kind,
                param_ref,
                &scoped_document,
                max_brief_chars,
                &mut report,
            );
        }
    }
    report.missing_fields.sort();
    report.missing_fields.dedup();
    report.warnings.sort();
    report.warnings.dedup();
    Ok(report)
}

#[derive(Clone, Copy)]
enum TargetKind<'a> {
    Intro,
    Parameter { boolean: bool, numeric: bool },
    Brief,
    Option(&'a str),
    Unit(&'a str),
}

fn resolve_target(
    resource: &mut Value,
    pointer: &str,
    kind: TargetKind<'_>,
    parameters: Option<&[&WikiParameter]>,
    document: &WikiCommandDocument,
    max_brief_chars: usize,
    report: &mut EnrichmentReport,
) {
    let node_key = pointer
        .strip_prefix("/node/")
        .and_then(|rest| rest.split('/').next())
        .map(unescape_pointer);
    let passages = select_automatic(kind, parameters, node_key.as_deref(), document);
    let mut unique = Vec::<Passage>::new();
    for passage in passages {
        if passage.text.trim().is_empty() {
            continue;
        }
        if let Some(existing) = unique.iter_mut().find(|p| p.text == passage.text) {
            if existing.source != passage.source {
                existing.source.push_str(" + ");
                existing.source.push_str(&passage.source);
            }
        } else {
            unique.push(passage);
        }
    }
    let distinct: BTreeSet<_> = unique.iter().map(|p| p.text.as_str()).collect();
    if unique.is_empty() || distinct.len() != 1 {
        if distinct.len() > 1 {
            report.warnings.push(format!(
                "{pointer}: 多个Wiki候选内容不一致，需调整 Wiki 原文 patch 以消除歧义"
            ));
        }
        report.missing_fields.push(pointer.to_string());
        return;
    }
    let passage = unique.remove(0);
    if matches!(kind, TargetKind::Brief) && passage.text.chars().count() > max_brief_chars {
        report.warnings.push(format!(
            "{pointer}: Wiki brief 长度 {} 超过上限 {}",
            passage.text.chars().count(),
            max_brief_chars
        ));
    }
    let Some(slot) = resource.pointer_mut(pointer) else {
        // A missing field may be added only beneath a parent that already exists.
        let value = Value::String(passage.text.clone());
        if set_pointer(resource, pointer, value).is_err() {
            report.missing_fields.push(pointer.to_string());
            return;
        }
        report.extracted_fields.push(ExtractedField {
            target: pointer.to_string(),
            value: passage.text,
            sources: vec![passage.source],
        });
        return;
    };
    *slot = Value::String(passage.text.clone());
    report.extracted_fields.push(ExtractedField {
        target: pointer.to_string(),
        value: passage.text,
        sources: vec![passage.source],
    });
}

fn select_automatic(
    kind: TargetKind<'_>,
    parameters: Option<&[&WikiParameter]>,
    node_key: Option<&str>,
    document: &WikiCommandDocument,
) -> Vec<Passage> {
    match kind {
        TargetKind::Intro => (!document.intro.trim().is_empty())
            .then(|| Passage {
                text: document.intro.clone(),
                source: format!("intro:{}", document.source_url),
            })
            .into_iter()
            .collect(),
        TargetKind::Parameter { boolean, numeric } => {
            let selected: Vec<_> = parameters
                .unwrap_or_default()
                .iter()
                .copied()
                .filter(|parameter| parameter.edition.as_deref() != Some("je"))
                .filter(|parameter| !parameter.description.trim().is_empty())
                .collect();
            let documented_options: Vec<_> = parameters
                .unwrap_or_default()
                .iter()
                .copied()
                .filter(|parameter| parameter.edition.as_deref() != Some("je"))
                .filter(|parameter| !parameter.options.is_empty())
                .filter(|_| !numeric)
                .collect();
            let passages = if !boolean && selected.is_empty() && !numeric {
                documented_options
                    .into_iter()
                    .map(|parameter| {
                        let mut fragments: Vec<_> = parameter
                            .options
                            .iter()
                            .filter(|option| !option.description.trim().is_empty())
                            .map(|option| {
                                format!("{}：{}", option.values.join("/"), option.description)
                            })
                            .collect();
                        if !parameter.description.trim().is_empty() {
                            fragments.push(parameter.description.clone());
                        }
                        Passage {
                            text: fragments.join(" "),
                            source: parameter_source(parameter),
                        }
                    })
                    .collect()
            } else {
                combine_compound(
                    selected,
                    |parameter| &parameter.description,
                    parameter_source,
                    " ",
                )
            };
            if passages.is_empty() && !boolean {
                node_key
                    .map(|key| syntax_for_node(key, document))
                    .unwrap_or_default()
            } else {
                passages
            }
        }
        TargetKind::Brief => {
            let selected = parameters
                .unwrap_or_default()
                .iter()
                .copied()
                .filter(|parameter| parameter.edition.as_deref() != Some("je"))
                .filter(|parameter| !parameter.brief.trim().is_empty())
                .collect();
            let passages =
                combine_compound(selected, |parameter| &parameter.brief, brief_source, "、");
            if passages.is_empty() {
                node_key
                    .map(|key| syntax_for_node_kind(key, document, TargetKind::Brief))
                    .unwrap_or_default()
            } else {
                passages
            }
        }
        TargetKind::Option(option) | TargetKind::Unit(option) => {
            let passages: Vec<_> = parameters
                .unwrap_or_default()
                .iter()
                .filter(|parameter| parameter.edition.as_deref() != Some("je"))
                .flat_map(|parameter| {
                    find_options(&parameter.options, option)
                        .into_iter()
                        .filter(|item| !item.description.trim().is_empty())
                        .map(|item| Passage {
                            text: item.description.clone(),
                            source: format!("{}:option@{}", parameter_source(parameter), item.line),
                        })
                        .collect::<Vec<_>>()
                })
                .collect();
            if !passages.is_empty() || matches!(kind, TargetKind::Unit(_)) {
                return passages;
            }

            let same_name: Vec<_> = document
                .parameters
                .iter()
                .filter(|parameter| parameter.edition.as_deref() != Some("je"))
                .filter(|parameter| parameter.names.iter().any(|name| name == option))
                .filter(|parameter| !parameter.description.trim().is_empty())
                .collect();
            if !same_name.is_empty() {
                return same_name
                    .into_iter()
                    .map(|parameter| Passage {
                        text: parameter.description.clone(),
                        source: parameter_source(parameter),
                    })
                    .collect();
            }

            syntax_for_node(option, document)
        }
    }
}

pub(crate) fn parameters_for_node<'a>(
    key: &str,
    document: &'a WikiCommandDocument,
) -> Vec<&'a WikiParameter> {
    let mut names = parameter_names_in_key(key);
    if names.is_empty() && !key.trim().is_empty() {
        // Some Wiki parameter sections describe a named literal node directly,
        // rather than using an angle/bracket argument name. Match only the exact
        // literal key here; unrelated parent parameters must not be reused.
        names.push(key.trim().to_string());
    }
    let alternatives = names.clone();
    if alternatives.len() > 1 && alternatives.iter().all(|name| !name.contains(':')) {
        let enums: Vec<_> = document
            .parameters
            .iter()
            .filter(|parameter| {
                parameter.edition.as_deref() != Some("je")
                    && alternatives.iter().all(|name| {
                        parameter
                            .options
                            .iter()
                            .any(|option| option.values.contains(name))
                    })
            })
            .collect();
        if !enums.is_empty() {
            return enums;
        }
    }
    let mut matches = Vec::new();
    for name in names {
        let exact_exists = document
            .parameters
            .iter()
            .any(|parameter| parameter.names.iter().any(|candidate| candidate == &name));
        let mut candidates: Vec<_> = document
            .parameters
            .iter()
            .filter(|parameter| {
                if key.contains(':') && parameter.literal {
                    return false;
                }
                parameter.names.iter().any(|candidate| {
                    candidate == &name
                        || (!exact_exists
                            && name.contains(':')
                            && candidate == short_parameter_name(&name))
                })
            })
            .collect();
        if candidates.is_empty() {
            let wanted = short_parameter_name(&name).trim_matches(['<', '>', '[', ']', '"', '\'']);
            candidates = document
                .parameters
                .iter()
                .filter(|parameter| {
                    if key.contains(':') && parameter.literal {
                        return false;
                    }
                    parameter.names.iter().any(|candidate| {
                        short_parameter_name(candidate)
                            .split('|')
                            .any(|alternative| {
                                alternative
                                    .trim()
                                    .trim_matches(['<', '>', '[', ']', '"', '\''])
                                    == wanted
                            })
                    })
                })
                .collect();
        }
        // Syntax-only placeholders can share the exact typed name with a later
        // parameter section (especially for Bedrock enums). Prefer actual source
        // documentation when it exists; keep empty placeholders only when no
        // documented candidate matched, so a blank `edition=None` entry cannot
        // hide the useful Bedrock prose and options.
        let documented: Vec<_> = candidates
            .iter()
            .copied()
            .filter(|parameter| {
                !parameter.description.trim().is_empty()
                    || !parameter.brief.trim().is_empty()
                    || !parameter.options.is_empty()
                    || !parameter.constraints.is_empty()
            })
            .collect();
        if !documented.is_empty() {
            candidates = documented;
        }
        // A shared Wiki block wins; otherwise select the Bedrock version. If two
        // candidates remain, preserve both so conflicting text is reported as ambiguous.
        let preferred_edition = if candidates.iter().any(|p| p.edition.is_none()) {
            None
        } else if candidates
            .iter()
            .any(|p| p.edition.as_deref() == Some("be"))
        {
            Some("be")
        } else {
            Some("je")
        };
        let mut preferred: Vec<_> = candidates
            .into_iter()
            .filter(|candidate| match preferred_edition {
                None => candidate.edition.is_none(),
                Some(edition) => candidate.edition.as_deref() == Some(edition),
            })
            .collect();
        if !key.contains(':') && preferred.iter().any(|parameter| parameter.literal) {
            preferred.retain(|parameter| parameter.literal);
        }
        let canonical: Vec<_> = preferred
            .iter()
            .copied()
            .filter(|parameter| {
                let primaries = if parameter.primary_names.is_empty() {
                    &parameter.names[..parameter.names.len().min(1)]
                } else {
                    parameter.primary_names.as_slice()
                };
                primaries
                    .iter()
                    .any(|primary| short_parameter_name(primary) == short_parameter_name(&name))
            })
            .collect();
        if !canonical.is_empty() {
            preferred = canonical;
        }
        matches.extend(preferred);
    }
    let mut unique: Vec<&WikiParameter> = Vec::new();
    for parameter in matches {
        if !unique
            .iter()
            .any(|existing| std::ptr::eq(*existing, parameter))
        {
            unique.push(parameter);
        }
    }
    if unique.is_empty()
        && alternatives.len() > 1
        && alternatives.iter().all(|name| !name.contains(':'))
    {
        unique.extend(document.parameters.iter().filter(|parameter| {
            parameter.edition.as_deref() != Some("je")
                && alternatives.iter().all(|name| {
                    parameter
                        .options
                        .iter()
                        .any(|option| option.values.contains(name))
                })
        }));
    }
    unique
}

fn parameter_names_in_key(key: &str) -> Vec<String> {
    let mut names = Vec::new();
    let mut rest = key;
    while let Some(start) = rest.find(['<', '[']) {
        let open = rest.as_bytes()[start] as char;
        let close = if open == '<' { '>' } else { ']' };
        let after = &rest[start + 1..];
        let Some(end) = after.find(close) else { break };
        let segment = &after[..end];
        for candidate in segment.split('|') {
            let candidate = candidate.trim().trim_matches(['<', '>', '[', ']']);
            if !candidate.is_empty() && !names.iter().any(|name| name == candidate) {
                names.push(candidate.to_string());
            }
        }
        rest = &after[end + 1..];
    }
    names
}

pub(crate) fn short_parameter_name(name: &str) -> &str {
    name.split_once(':').map_or(name, |(short, _)| short.trim())
}

/// Restrict duplicate source names to command paths that actually consume a node.
/// Section headings remain ordinary Wiki prose; no source selectors are maintained.
fn document_for_node(
    resource: &Value,
    document: &WikiCommandDocument,
    key: &str,
) -> WikiCommandDocument {
    let mut scoped = document.clone();
    let syntaxes: Vec<_> = resource
        .get("syntax")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .collect();
    let routes: Vec<_> = syntaxes
        .iter()
        .copied()
        .filter(|syntax| {
            if key.contains(['<', '[']) {
                syntax.contains(key)
            } else {
                literal_words(syntax).iter().any(|word| word == key)
            }
        })
        .collect();
    if routes.is_empty() {
        return scoped;
    }
    let route_words: Vec<_> = routes.iter().map(|route| literal_words(route)).collect();
    let all_literals: BTreeSet<_> = syntaxes
        .iter()
        .flat_map(|syntax| literal_words(syntax))
        .collect();
    let command_words: BTreeSet<_> = syntaxes
        .iter()
        .filter_map(|syntax| literal_words(syntax).into_iter().next())
        .collect();
    scoped.parameters.retain(|parameter| {
        let groups: Vec<Vec<_>> = parameter
            .context
            .split(" / ")
            .map(|heading| {
                heading
                    .split(|character: char| !character.is_ascii_alphanumeric() && character != '_')
                    .filter(|word| {
                        !word.is_empty()
                            && all_literals.contains(*word)
                            && !command_words.contains(*word)
                    })
                    .collect()
            })
            .filter(|group: &Vec<_>| !group.is_empty())
            .collect();
        groups.is_empty()
            || route_words.iter().any(|route| {
                groups.iter().all(|group| {
                    group
                        .iter()
                        .any(|word| route.iter().any(|literal| literal == word))
                })
            })
    });
    let compatible: Vec<_> = scoped
        .syntax_descriptions
        .iter()
        .filter(|source| {
            let words = literal_words(&source.syntax);
            route_words.iter().any(|route| {
                ordered_subsequence(&words, route) || ordered_subsequence(route, &words)
            })
        })
        .cloned()
        .collect();
    if !compatible.is_empty() {
        scoped.syntax_descriptions = compatible;
    }
    scoped
}

fn literal_words(syntax: &str) -> Vec<String> {
    without_argument_placeholders(syntax)
        .split(|character: char| {
            !character.is_ascii_alphanumeric() && character != '_' && character != '.'
        })
        .filter(|word| !word.is_empty())
        .map(str::to_string)
        .collect()
}

fn ordered_subsequence(needles: &[String], haystack: &[String]) -> bool {
    let mut remaining = needles.iter();
    let mut next = remaining.next();
    for value in haystack {
        if next == Some(value) {
            next = remaining.next();
        }
    }
    next.is_none()
}

/// A keyword can introduce a parameter group, e.g. `facing <entity>` or
/// `of <clock>`. Follow the existing syntax until the next literal keyword.
fn following_parameters<'a>(
    resource: &Value,
    key: &str,
    document: &'a WikiCommandDocument,
) -> Vec<&'a WikiParameter> {
    let mut groups = Vec::new();
    for syntax in resource
        .get("syntax")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
    {
        let mut depth = 0usize;
        for (index, character) in syntax.char_indices() {
            if matches!(character, '<' | '[') {
                depth += 1;
                continue;
            }
            if matches!(character, '>' | ']') {
                depth = depth.saturating_sub(1);
                continue;
            }
            if depth != 0 || !syntax[index..].starts_with(key) {
                continue;
            }
            if syntax[..index].trim_matches(['/', ' ']).is_empty() {
                continue;
            }
            let previous = syntax[..index].chars().next_back();
            let after = &syntax[index + key.len()..];
            if previous.is_some_and(|value| value.is_alphanumeric() || value == '_')
                || after
                    .chars()
                    .next()
                    .is_some_and(|value| value.is_alphanumeric() || value == '_')
            {
                continue;
            }
            let mut rest = after.trim_start();
            while rest.starts_with(['<', '[']) {
                let mut stack = Vec::new();
                let mut end = None;
                for (offset, value) in rest.char_indices() {
                    match value {
                        '<' | '[' => stack.push(value),
                        '>' if stack.last() == Some(&'<') => {
                            stack.pop();
                        }
                        ']' if stack.last() == Some(&'[') => {
                            stack.pop();
                        }
                        _ => {}
                    }
                    if stack.is_empty() {
                        end = Some(offset + value.len_utf8());
                        break;
                    }
                }
                let Some(end) = end else {
                    break;
                };
                groups.push(rest[..end].to_string());
                rest = rest[end..].trim_start();
            }
        }
    }
    parameters_for_node(&groups.join(" "), document)
}

fn combine_compound<'a>(
    parameters: Vec<&'a WikiParameter>,
    text: impl Fn(&'a WikiParameter) -> &'a String,
    source: impl Fn(&WikiParameter) -> String,
    separator: &str,
) -> Vec<Passage> {
    if parameters.len() <= 1 {
        return parameters
            .into_iter()
            .filter(|parameter| !text(parameter).trim().is_empty())
            .map(|parameter| Passage {
                text: text(parameter).clone(),
                source: source(parameter),
            })
            .collect();
    }
    let unique_names: BTreeSet<_> = parameters
        .iter()
        .map(|parameter| parameter.names.join("|"))
        .collect();
    if unique_names.len() != parameters.len() {
        return parameters
            .into_iter()
            .filter(|parameter| !text(parameter).trim().is_empty())
            .map(|parameter| Passage {
                text: text(parameter).clone(),
                source: source(parameter),
            })
            .collect();
    }
    let values: Vec<_> = parameters
        .into_iter()
        .filter(|parameter| !text(parameter).trim().is_empty())
        .collect();
    if values.is_empty() {
        return Vec::new();
    }
    let mut distinct_text = Vec::new();
    for parameter in &values {
        let value = text(parameter).trim();
        if !distinct_text.contains(&value) {
            distinct_text.push(value);
        }
    }
    vec![Passage {
        text: distinct_text.join(separator),
        source: values
            .iter()
            .map(|parameter| source(parameter))
            .collect::<Vec<_>>()
            .join(" + "),
    }]
}

fn find_options<'a>(options: &'a [WikiOption], wanted: &str) -> Vec<&'a WikiOption> {
    let mut found = Vec::new();
    for option in options {
        if option.values.iter().any(|value| value == wanted) {
            found.push(option);
        }
        found.extend(find_options(&option.options, wanted));
    }
    found
}

#[derive(Debug)]
struct CollectedTarget {
    pointer: String,
    kind: CollectedKind,
}

#[derive(Debug)]
enum CollectedKind {
    Parameter,
    Brief,
    Option(String),
    Unit(String),
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unit_annotations_cannot_be_published_as_plain_integers() {
        let document =
            crate::wiki_extract::parse_document("demo", "wiki", "用途。\n==参数==\n", 12).unwrap();
        let mut resource = serde_json::json!({"node": {
            "<time: int>": {"type":"INTEGER", "units":[{"name":"s","description":"秒"}]}
        }});
        let error = enrich_command(&mut resource, &document, 12).unwrap_err();
        assert!(error.to_string().contains("INTEGER_WITH_UNIT"));
        assert!(error.to_string().contains("<time: int>"));
    }

    #[test]
    fn shared_boolean_and_integer_source_keeps_integer_purpose_separate() {
        let document = crate::wiki_extract::parse_document("demo", "wiki", "用途。\n==参数==\n{{argument|value: Boolean|value: int|enum|be=1}}\n: 指定设置的值。\n: 必须为以下其中之一：\n:* <code>true</code> — 启用规则。\n:* <code>false</code> — 禁用规则。\n", 12).unwrap();
        let mut resource = serde_json::json!({"node": {
            "[value: Boolean]": {"type":"BOOLEAN"},
            "[value: int]": {"type":"INTEGER"}
        }});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report.missing_fields.is_empty());
        assert_eq!(
            resource["node"]["[value: int]"]["description"],
            "指定设置的值。"
        );
        assert_eq!(
            resource["node"]["[value: Boolean]"]["descriptionTrue"],
            "启用规则。"
        );
        assert_eq!(
            resource["node"]["[value: Boolean]"]["descriptionFalse"],
            "禁用规则。"
        );
    }

    #[test]
    fn shared_argument_headings_preserve_both_coordinate_roles() {
        let document = crate::wiki_extract::parse_document("demo", "wiki", "用途。\n==参数==\n{{argument|from: x y z|be=1}}和{{argument|to: x y z|be=1}}\n: 保存时指定源区域的对角坐标。\n: brief: 保存坐标\n\n{{argument|to: x y z|be=1}}\n: 加载时指定目标位置。\n: brief: 加载坐标\n", 12).unwrap();
        let key = "<to: x y z>";
        let mut resource = serde_json::json!({"node":{key:{"type":"POSITION"}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report.missing_fields.is_empty());
        assert_eq!(resource["node"][key]["brief"], "保存坐标、加载坐标");
        let text = resource["node"][key]["description"].as_str().unwrap();
        assert!(text.contains("保存时指定源区域"));
        assert!(text.contains("加载时指定目标位置"));
    }

    #[test]
    fn compound_purpose_deduplicates_text_and_preserves_each_source() {
        let document = crate::wiki_extract::parse_document("demo", "wiki", "用途。\n==参数==\n{{argument|a: int|be=1}}\n: 指定偏移量。\n{{argument|b: int|be=1}}\n: 指定偏移量。\n{{argument|c: int|be=1}}\n: 指定偏移量。\n", 12).unwrap();
        let key = "<a: int> <b: int> <c: int>";
        let mut resource = serde_json::json!({"node":{key:{"type":"POSITION"}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report.missing_fields.is_empty());
        assert_eq!(resource["node"][key]["description"], "指定偏移量。");
        let field = report
            .extracted_fields
            .iter()
            .find(|field| {
                field.target.starts_with("/node/") && field.target.ends_with("/description")
            })
            .unwrap();
        for name in ["a: int", "b: int", "c: int"] {
            assert!(field.sources.iter().any(|source| source.contains(name)));
        }
    }

    #[test]
    fn literal_definitions_do_not_replace_typed_or_untyped_arguments() {
        let document = crate::wiki_extract::parse_document("demo", "wiki", "用途。\n==参数==\n{{argument|time: Query|enum|be=1}}\n: 指定查询类型。\n; 关键字 <code>time</code>\n: 查询时钟经过的刻数。\n; <code>value</code>\n: 指定数值。\n", 12).unwrap();
        assert!(document
            .parameters
            .iter()
            .all(|parameter| parameter.context.is_empty()));
        let mut resource = serde_json::json!({"syntax":["/demo time [value: int]", "/demo <time: Query>"],"node": {
            "time":{"type":"TEXT","data":{"name":"time"}},
            "<time: Query>":{"type":"STRING"},
            "[value: int]":{"type":"INTEGER"}
        }});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report.missing_fields.is_empty());
        assert_eq!(
            resource["node"]["time"]["description"],
            "查询时钟经过的刻数。"
        );
        assert_eq!(
            resource["node"]["<time: Query>"]["description"],
            "指定查询类型。"
        );
        assert_eq!(
            resource["node"]["[value: int]"]["description"],
            "指定数值。"
        );
    }

    #[test]
    fn absent_inline_and_nested_boolean_descriptions_remain_required() {
        let document =
            crate::wiki_extract::parse_document("demo", "test", "示例。\n==参数==\n", 12).unwrap();
        let mut resource = serde_json::json!({"node":{
            "push":{"type":"TEXT","data":{"name":"push"}},
            "flag":{"type":"WRAPPER","data":{"type":"BOOLEAN"}}
        }});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        for pointer in [
            "/node/push/data/description",
            "/node/flag/data/description",
            "/node/flag/data/descriptionTrue",
            "/node/flag/data/descriptionFalse",
        ] {
            assert!(
                report
                    .missing_fields
                    .iter()
                    .any(|missing| missing == pointer),
                "{pointer}"
            );
        }
    }

    #[test]
    fn boolean_purpose_keeps_omitted_query_without_copying_value_meanings() {
        let document = crate::wiki_extract::parse_document("demo", "test", "示例。\n==语法==\n: <code>/demo [flag: bool]</code>\n==参数==\n{{argument|flag|bool|be=1}}\n: 指定是否启用事件。如果忽略，则显示当前启用状态。\n: 必须为以下其中之一：\n:* <code>true</code> — 启用事件。\n:* <code>false</code> — 禁用事件。\n: brief：事件状态\n", 12).unwrap();
        let mut resource = serde_json::json!({"node":{"[flag: bool]":{"type":"BOOLEAN"}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report.missing_fields.is_empty());
        assert_eq!(
            resource["node"]["[flag: bool]"]["description"],
            "指定是否启用事件。 如果忽略，则显示当前启用状态。"
        );
        assert_eq!(
            resource["node"]["[flag: bool]"]["descriptionTrue"],
            "启用事件。"
        );
        assert_eq!(
            resource["node"]["[flag: bool]"]["descriptionFalse"],
            "禁用事件。"
        );
    }

    #[test]
    fn boolean_value_meanings_cannot_replace_missing_parameter_purpose() {
        let document = crate::wiki_extract::parse_document("demo", "test", "示例。\n==参数==\n{{argument|flag: bool|bool|be=1}}\n: 必须为以下其中之一：\n:* <code>true</code> — 启用事件。\n:* <code>false</code> — 禁用事件。\n: brief：事件状态\n", 12).unwrap();
        let mut resource = serde_json::json!({"node":{"[flag: bool]":{"type":"BOOLEAN"}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert_eq!(
            report.missing_fields,
            vec!["/node/[flag: bool]/description"]
        );
        assert!(resource["node"]["[flag: bool]"]
            .get("description")
            .is_none());
        assert_eq!(
            resource["node"]["[flag: bool]"]["descriptionTrue"],
            "启用事件。"
        );
        assert_eq!(
            resource["node"]["[flag: bool]"]["descriptionFalse"],
            "禁用事件。"
        );
    }

    #[test]
    fn action_brief_uses_unambiguous_syntax_note_without_binding() {
        let document = crate::wiki_extract::parse_document(
            "demo", "test", "示例。\n==语法==\n: <code>/demo clear</code>\n:: 清除指定目标的效果。\n:: brief：清除效果\n==参数==\n", 12,
        ).unwrap();
        let mut resource = serde_json::json!({"node":{"clear":{"type":"TEXT","brief":"旧标签","data":{"name":"clear"}}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(
            report.missing_fields.is_empty(),
            "{:?}",
            report.missing_fields
        );
        assert_eq!(resource["node"]["clear"]["brief"], "清除效果");
        assert_eq!(
            resource["node"]["clear"]["data"]["description"],
            "清除指定目标的效果。"
        );
    }

    #[test]
    fn conflicting_syntax_briefs_remain_ambiguous() {
        let document = crate::wiki_extract::parse_document(
            "demo", "test", "示例。\n==语法==\n: <code>/demo clear first</code>\n:: 清除第一种效果。\n:: brief：第一种效果\n: <code>/demo clear second</code>\n:: 清除第二种效果。\n:: brief：第二种效果\n==参数==\n", 12,
        ).unwrap();
        let mut resource = serde_json::json!({"node":{"clear":{"type":"TEXT","brief":"旧标签","data":{"name":"clear"}}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert!(report
            .missing_fields
            .contains(&"/node/clear/brief".to_string()));
        assert_eq!(resource["node"]["clear"]["brief"], "旧标签");
    }

    #[test]
    fn named_units_require_unit_definitions_instead_of_syntax_prose() {
        let document = crate::wiki_extract::parse_document(
            "demo", "test", "示例。\n==语法==\n: <code>/demo delay <time: int>D</code>\n:: 延时运行。\n==参数==\n{{argument|time|int|be=1}}\n: 指定延迟。\n: 必须为以下其中之一：\n:* <code>D</code> — 游戏日。\n", 12,
        ).unwrap();
        let mut resource = serde_json::json!({"node":{"<time: int>":{"type":"INTEGER_WITH_UNIT", "units":[{"name":"D"},{"name":"d"}]}}});
        let report = enrich_command(&mut resource, &document, 12).unwrap();
        assert_eq!(
            resource["node"]["<time: int>"]["units"][0]["description"],
            "游戏日。"
        );
        assert!(report
            .missing_fields
            .contains(&"/node/<time: int>/units/1/description".to_string()));
        assert!(resource["node"]["<time: int>"]["units"][1]
            .get("description")
            .is_none());
    }
}

fn collect_documentation_targets(
    value: &Value,
    pointer: &str,
    in_option_list: bool,
    in_unit_list: bool,
    output: &mut Vec<CollectedTarget>,
) {
    match value {
        Value::Object(object) => {
            let option_name = object
                .get("name")
                .or_else(|| object.get("unit"))
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string();
            let is_unit =
                in_unit_list || (object.get("unit").is_some() && object.get("name").is_none());
            for (key, child) in object {
                let child_pointer = format!("{pointer}/{}", escape_pointer(key));
                let kind = match key.as_str() {
                    "description" if in_option_list && !option_name.is_empty() => {
                        Some(if is_unit {
                            CollectedKind::Unit(option_name.clone())
                        } else {
                            CollectedKind::Option(option_name.clone())
                        })
                    }
                    "description" => Some(CollectedKind::Parameter),
                    "brief" if in_option_list && !option_name.is_empty() => Some(if is_unit {
                        CollectedKind::Unit(option_name.clone())
                    } else {
                        CollectedKind::Option(option_name.clone())
                    }),
                    "brief" => Some(CollectedKind::Brief),
                    "descriptionTrue" => Some(CollectedKind::Option("true".into())),
                    "descriptionFalse" => Some(CollectedKind::Option("false".into())),
                    _ => None,
                };
                if let Some(kind) = kind {
                    output.push(CollectedTarget {
                        pointer: child_pointer.clone(),
                        kind,
                    });
                }
                let child_in_option_list =
                    in_option_list || matches!(key.as_str(), "contents" | "units");
                collect_documentation_targets(
                    child,
                    &child_pointer,
                    child_in_option_list,
                    in_unit_list || key == "units",
                    output,
                );
            }
            let inline_id = pointer.ends_with("/data") && !option_name.is_empty();
            let boolean = object.get("type").and_then(Value::as_str) == Some("BOOLEAN");
            if !object.contains_key("description")
                && ((in_option_list && !option_name.is_empty()) || inline_id || boolean)
            {
                output.push(CollectedTarget {
                    pointer: format!("{pointer}/description"),
                    kind: if !in_option_list {
                        CollectedKind::Parameter
                    } else if is_unit {
                        CollectedKind::Unit(option_name)
                    } else {
                        CollectedKind::Option(option_name)
                    },
                });
            }
            if boolean {
                for (field, option) in [("descriptionTrue", "true"), ("descriptionFalse", "false")]
                {
                    if !object.contains_key(field) {
                        output.push(CollectedTarget {
                            pointer: format!("{pointer}/{field}"),
                            kind: CollectedKind::Option(option.into()),
                        });
                    }
                }
            }
        }
        Value::Array(array) => {
            for (index, child) in array.iter().enumerate() {
                collect_documentation_targets(
                    child,
                    &format!("{pointer}/{index}"),
                    in_option_list,
                    in_unit_list,
                    output,
                );
            }
        }
        _ => {}
    }
}

fn collect_preserved_text(value: &Value, pointer: &str, output: &mut Vec<(String, String)>) {
    match value {
        Value::Object(object) => {
            for (key, child) in object {
                let child_pointer = format!("{pointer}/{}", escape_pointer(key));
                if matches!(
                    key.as_str(),
                    "description" | "brief" | "descriptionTrue" | "descriptionFalse"
                ) {
                    if let Some(text) = child.as_str() {
                        output.push((child_pointer.clone(), text.to_string()));
                    }
                }
                collect_preserved_text(child, &child_pointer, output);
            }
        }
        Value::Array(array) => {
            for (index, child) in array.iter().enumerate() {
                collect_preserved_text(child, &format!("{pointer}/{index}"), output);
            }
        }
        _ => {}
    }
}

fn syntax_text<'a>(item: &'a WikiSyntaxDescription, kind: TargetKind<'_>) -> &'a str {
    if matches!(kind, TargetKind::Brief) {
        &item.brief
    } else {
        &item.description
    }
}

fn syntax_passage_for_kind(item: &WikiSyntaxDescription, kind: TargetKind<'_>) -> Passage {
    Passage {
        text: syntax_text(item, kind).to_string(),
        source: format!(
            "syntax:{}@{} [edition:{}; context:{}]{}",
            item.syntax,
            item.line,
            item.edition.as_deref().unwrap_or("shared"),
            item.context,
            if matches!(kind, TargetKind::Brief) {
                if item.brief_explicit {
                    ":brief-explicit"
                } else {
                    ":brief-derived"
                }
            } else {
                ""
            }
        ),
    }
}

fn syntax_for_node(key: &str, document: &WikiCommandDocument) -> Vec<Passage> {
    syntax_for_node_kind(
        key,
        document,
        TargetKind::Parameter {
            boolean: false,
            numeric: false,
        },
    )
}

fn has_value_type(value: &Value, wanted: &str) -> bool {
    value.get("type").and_then(Value::as_str) == Some(wanted)
        || value
            .get("data")
            .is_some_and(|data| has_value_type(data, wanted))
}

fn syntax_for_node_kind(
    key: &str,
    document: &WikiCommandDocument,
    kind: TargetKind<'_>,
) -> Vec<Passage> {
    let key = key.trim_matches(['<', '>', '[', ']']);
    if !key.contains(':') && key.contains('|') {
        let alternatives: Vec<_> = key.split('|').collect();
        let mut passages = Vec::new();
        for alternative in alternatives {
            let choices = syntax_for_node_kind(alternative, document, kind);
            let distinct: BTreeSet<_> = choices
                .iter()
                .map(|passage| passage.text.as_str())
                .collect();
            if distinct.len() != 1 {
                return choices;
            }
            passages.push(Passage {
                text: if matches!(kind, TargetKind::Brief) {
                    choices[0].text.clone()
                } else {
                    format!("{alternative}：{}", choices[0].text)
                },
                source: choices[0].source.clone(),
            });
        }
        let mut values = Vec::new();
        for passage in &passages {
            if !matches!(kind, TargetKind::Brief) || !values.contains(&passage.text.as_str()) {
                values.push(passage.text.as_str());
            }
        }
        return vec![Passage {
            text: values.join(if matches!(kind, TargetKind::Brief) {
                "、"
            } else {
                " "
            }),
            source: passages
                .iter()
                .map(|passage| passage.source.as_str())
                .collect::<Vec<_>>()
                .join(" + "),
        }];
    }
    let candidates: Vec<_> = document
        .syntax_descriptions
        .iter()
        .filter(|item| item.edition.as_deref() != Some("je"))
        .filter(|item| !syntax_text(item, kind).trim().is_empty())
        .filter(|item| {
            // Parameter placeholders are not command literals. In particular,
            // D/S/T inside a time argument must never resolve to the command's
            // behavior paragraph as a unit explanation.
            let syntax = without_argument_placeholders(&item.syntax);
            syntax
                .split(|character: char| !character.is_alphanumeric() && character != '_')
                .filter(|token| !token.is_empty())
                .enumerate()
                .filter(|(index, token)| {
                    *index != 0 || !token.eq_ignore_ascii_case(&document.command)
                })
                .map(|(_, token)| token)
                .any(|token| token.eq_ignore_ascii_case(key))
        })
        .collect();
    let direct: Vec<_> = candidates
        .iter()
        .copied()
        .filter(|item| {
            literal_words(&item.syntax)
                .last()
                .is_some_and(|word| word == key)
        })
        .collect();
    let selected = if direct.is_empty() {
        candidates
    } else {
        direct
    };
    selected
        .into_iter()
        .map(|item| syntax_passage_for_kind(item, kind))
        .collect()
}

fn without_argument_placeholders(syntax: &str) -> String {
    let mut output = String::new();
    let mut stack = Vec::new();
    for character in syntax.chars() {
        match character {
            '<' | '[' => stack.push(character),
            '>' if stack.last() == Some(&'<') => {
                stack.pop();
            }
            ']' if stack.last() == Some(&'[') => {
                stack.pop();
            }
            _ if stack.is_empty() => output.push(character),
            _ => {}
        }
    }
    output
}

fn parameter_source(parameter: &WikiParameter) -> String {
    format!(
        "parameter:{}@{} [edition:{}]{}",
        parameter.names.join("|"),
        parameter.line,
        parameter.edition.as_deref().unwrap_or("shared"),
        if parameter.context.is_empty() {
            String::new()
        } else {
            format!(" [{}]", parameter.context)
        }
    )
}

fn brief_source(parameter: &WikiParameter) -> String {
    format!(
        "{}:{}",
        parameter_source(parameter),
        if parameter.brief_explicit {
            "brief-explicit"
        } else {
            "brief-derived"
        }
    )
}

fn escape_pointer(value: &str) -> String {
    value.replace('~', "~0").replace('/', "~1")
}

fn set_pointer(root: &mut Value, pointer: &str, value: Value) -> Result<()> {
    let (parent, key) = pointer
        .rsplit_once('/')
        .ok_or_else(|| anyhow::anyhow!("无效JSON Pointer: {pointer}"))?;
    let parent = if parent.is_empty() { "" } else { parent };
    let parent_value = root
        .pointer_mut(parent)
        .ok_or_else(|| anyhow::anyhow!("JSON Pointer父节点不存在: {parent}"))?;
    if let Some(array) = parent_value.as_array_mut() {
        let index: usize = key.parse()?;
        let slot = array
            .get_mut(index)
            .ok_or_else(|| anyhow::anyhow!("JSON Pointer数组索引不存在: {pointer}"))?;
        *slot = value;
    } else if let Some(object) = parent_value.as_object_mut() {
        object.insert(unescape_pointer(key), value);
    } else {
        bail!("JSON Pointer父节点不是对象或数组: {parent}");
    }
    Ok(())
}

fn unescape_pointer(value: &str) -> String {
    value.replace("~1", "/").replace("~0", "~")
}
