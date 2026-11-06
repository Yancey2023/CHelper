//! Conservative extraction of command parameter facts from Chinese Minecraft Wiki wikitext.
//!
//! This module records source text and structural relationships. It does not write or invent
//! command-resource descriptions; ambiguous source patterns are reported as warnings.

use crate::command_doc::{
    WikiCommandDocument, WikiOption, WikiPageMetadata, WikiParagraph, WikiParameter,
    WikiSyntaxDescription, WikiTextPassage,
};
use anyhow::{bail, Result};
use regex::Regex;
use std::sync::OnceLock;

/// Parse the introduction, syntax notes, and argument blocks from a raw Wiki page.
pub fn parse_document(
    command: &str,
    source_url: &str,
    text: &str,
    max_brief_chars: usize,
) -> Result<WikiCommandDocument> {
    if text.trim().is_empty() {
        bail!("Wiki page is empty for command {command}");
    }

    let lines: Vec<&str> = text.lines().collect();
    let mut document = WikiCommandDocument {
        command: command.to_string(),
        source_url: source_url.to_string(),
        ..WikiCommandDocument::default()
    };
    let (intro_markup, page_metadata) = extract_intro_markup(&lines);
    document.page_metadata = page_metadata;
    document.intro = extract_intro_description(
        &select_experimental_intro(&intro_markup, None),
        command,
        &mut document.warnings,
    );
    for template in find_named_templates(&intro_markup, "in exp") {
        if let Some(feature) = split_top_level(&template)
            .iter()
            .find_map(|field| field.trim().strip_prefix("exg=").map(str::to_string))
        {
            document.experimental_intros.insert(
                feature.clone(),
                extract_intro_description(
                    &select_experimental_intro(&intro_markup, Some(&feature)),
                    command,
                    &mut document.warnings,
                ),
            );
        }
    }
    document.syntax_descriptions = extract_syntax(&lines, command, &mut document.warnings);
    document.parameters =
        extract_parameters(&lines, command, max_brief_chars, &mut document.warnings);
    document.passages = extract_prose_passages(&lines, command);
    Ok(document)
}

fn extract_prose_passages(lines: &[&str], command: &str) -> Vec<WikiTextPassage> {
    let (start, end) = command_region(lines, command).unwrap_or((0, lines.len()));
    let mut sections = Vec::<(usize, String)>::new();
    let mut output = Vec::new();
    for (index, line) in lines.iter().enumerate().take(end).skip(start) {
        let line = line.trim();
        if line.starts_with("==") {
            let level = heading_level(line);
            while sections
                .last()
                .is_some_and(|(previous, _)| *previous >= level)
            {
                sections.pop();
            }
            sections.push((level, clean_heading(line)));
            continue;
        }
        if sections.is_empty()
            || sections
                .iter()
                .any(|(_, section)| matches!(section.as_str(), "语法" | "参数"))
        {
            continue;
        }
        let body = strip_leading_colons(line)
            .trim_start_matches(['*', ';'])
            .trim();
        if body.is_empty()
            || body.starts_with(['|', '!'])
            || body.starts_with("{{")
            || body.starts_with("{|")
        {
            continue;
        }
        let section = sections
            .iter()
            .map(|(_, name)| name.as_str())
            .collect::<Vec<_>>()
            .join(" / ");
        for (edition, text) in render_edition_fragments(body) {
            if !text.is_empty() {
                output.push(WikiTextPassage {
                    section: section.clone(),
                    edition,
                    text,
                    line: index + 1,
                });
            }
        }
    }
    output
}

fn extract_intro_markup(lines: &[&str]) -> (String, Vec<WikiPageMetadata>) {
    let mut after_infobox = false;
    let mut intro = Vec::new();
    let mut infobox_depth = 0_i32;

    for line in lines {
        let trimmed = line.trim();
        if trimmed.starts_with("==") {
            break;
        }
        if trimmed.starts_with("{{Infobox") || trimmed.starts_with("{{infobox") {
            infobox_depth += brace_delta(trimmed);
            after_infobox = infobox_depth > 0;
            continue;
        }
        if after_infobox {
            infobox_depth += brace_delta(trimmed);
            if infobox_depth <= 0 {
                after_infobox = false;
            }
            continue;
        }
        if trimmed.is_empty() {
            continue;
        }
        if trimmed.starts_with("{{tr|")
            && find_named_templates(trimmed, "tr").iter().any(|body| {
                split_top_level(body)
                    .iter()
                    .any(|field| field.trim() == "mode=H")
            })
        {
            continue;
        }
        intro.push(trimmed.to_string());
    }
    // Render complete markup once. Rendering a multiline hatnote line by line
    // leaves unmatched template closers in the introductory description.
    let markup = intro.join("\n");
    let metadata = named_templates_with_content(&markup)
        .into_iter()
        .filter_map(|(name, body)| {
            is_preserved_page_metadata(&name.to_ascii_lowercase(), &split_top_level(&body)).then(
                || WikiPageMetadata {
                    template: name.to_ascii_lowercase(),
                    arguments: split_top_level(&body)
                        .into_iter()
                        .skip(1)
                        .map(|value| value.trim().to_string())
                        .collect(),
                },
            )
        })
        .collect();
    (strip_intro_metadata(&markup), metadata)
}

fn extract_intro_description(markup: &str, command: &str, warnings: &mut Vec<String>) -> String {
    let mut unhandled = Vec::new();
    warn_unhandled_templates(markup, command, 0, &mut unhandled);
    if !unhandled.is_empty() {
        warnings.extend(unhandled);
        // An unknown visible template may supply a feature name or an essential
        // qualifier. Do not label a sentence with silently missing words as sourced.
        return String::new();
    }
    warn_experiment_identifiers(markup, command, warnings);
    render_intro(markup)
}

fn warn_experiment_identifiers(markup: &str, command: &str, warnings: &mut Vec<String>) {
    for (name, body) in named_templates_with_content(markup) {
        if name.eq_ignore_ascii_case("experiment") {
            warnings.push(format!(
                "{command}: experiment 模板中文显示名未核实，保留原文实验ID（代码标记）"
            ));
        }
        for field in split_top_level(&body).iter().skip(1) {
            warn_experiment_identifiers(field, command, warnings);
        }
    }
}

fn render_intro(markup: &str) -> String {
    // `el` is an edition link used as a noun in prose (e.g. “在基岩版中”),
    // not an `in` scope marker. Expand it before the fragment parser, which also
    // accepts edition links as standalone parameter-section headings elsewhere.
    static EDITION_LINK: OnceLock<Regex> = OnceLock::new();
    let markup = EDITION_LINK
        .get_or_init(|| {
            Regex::new(r"(?i)\{\{\s*el\s*\|\s*(be|bedrock|je|java|edu|education)\s*\}\}").unwrap()
        })
        .replace_all(markup, |captures: &regex::Captures<'_>| {
            edition_display_name(&captures[1])
        });
    render_edition_fragments(&markup)
        .into_iter()
        .filter(|(edition, _)| edition.as_deref() != Some("je"))
        .map(|(_, text)| text)
        .collect::<Vec<_>>()
        .join(" ")
}

fn select_experimental_intro(input: &str, enabled: Option<&str>) -> String {
    let mut output = String::new();
    let mut cursor = 0;
    while let Some(relative) = input[cursor..].find("{{") {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        let Some(length) = find_template_end(&input[start..]) else {
            output.push_str(&input[start..]);
            return output;
        };
        let body = &input[start + 2..start + length - 2];
        let fields = split_top_level(body);
        if fields[0].trim().eq_ignore_ascii_case("in exp") {
            let feature = fields
                .iter()
                .find_map(|field| field.trim().strip_prefix("exg="));
            let content = fields
                .iter()
                .find_map(|field| field.trim().strip_prefix("for="));
            if feature.is_some() && feature == enabled {
                if let Some(content) = content {
                    output.push_str(content);
                }
            } else {
                // The conjunction belongs to the omitted list item, not the remaining noun.
                if output.ends_with(['或', '和', '及', '、']) {
                    let conjunction = output.pop().unwrap();
                    if let Some(index) = output.rfind('、') {
                        if !output[index..].contains(['。', '；', '\n']) {
                            output.replace_range(
                                index..index + '、'.len_utf8(),
                                &conjunction.to_string(),
                            );
                        }
                    }
                }
            }
        } else {
            output.push_str(&input[start..start + length]);
        }
        cursor = start + length;
    }
    output.push_str(&input[cursor..]);
    output
}

fn is_preserved_page_metadata(name: &str, fields: &[String]) -> bool {
    matches!(
        name,
        "exclusive" | "education feature" | "upcoming in draft"
    ) && !fields
        .iter()
        .skip(1)
        .any(|field| field.trim().starts_with("for="))
}

fn strip_intro_metadata(input: &str) -> String {
    let mut output = String::new();
    let mut cursor = 0;
    while let Some(relative) = input[cursor..].find("{{") {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        let Some(length) = find_template_end(&input[start..]) else {
            output.push_str(&input[start..]);
            return output;
        };
        let body = &input[start + 2..start + length - 2];
        let fields = split_top_level(body);
        let name = fields[0].trim().to_ascii_lowercase();
        let metadata = matches!(
            name.as_str(),
            "redirect"
                | "for"
                | "about"
                | "distinguish"
                | "other uses"
                | "fn"
                | "bug"
                | "missing information"
                | "info needed"
                | "cleanup"
                | "stub"
                | "relevant tutorial"
        ) || is_preserved_page_metadata(&name, &fields)
            || (name == "tr" && fields.iter().any(|field| field.trim() == "mode=H"))
            || (name == "outdated"
                && fields.iter().skip(1).any(|field| {
                    field.split_once('=').is_some_and(|(key, value)| {
                        key.trim().eq_ignore_ascii_case("edition")
                            && matches!(value.trim().to_ascii_lowercase().as_str(), "java" | "je")
                    })
                }));
        if !metadata {
            output.push_str(&input[start..start + length]);
        }
        cursor = start + length;
    }
    output.push_str(&input[cursor..]);
    output
}

fn extract_syntax(
    lines: &[&str],
    command: &str,
    warnings: &mut Vec<String>,
) -> Vec<WikiSyntaxDescription> {
    let (region_start, region_end) = command_region(lines, command).unwrap_or((0, lines.len()));
    let Some((start, section_level)) =
        find_section_heading(lines, region_start, region_end, "语法")
    else {
        if command != "execute" {
            warnings.push(format!("{command}: 未找到“语法”段落"));
        }
        return Vec::new();
    };
    let end = next_heading_at_or_above(lines, start + 1, section_level).unwrap_or(region_end);
    let mut output = Vec::<WikiSyntaxDescription>::new();
    let mut edition: Option<String> = None;
    let mut context = String::new();
    let mut parent_syntax: Option<String> = None;
    let mut last_syntax_line = None;
    let mut last_syntax_depth = None;
    let mut last_list_depth = 0;
    let mut syntax_group = Vec::<usize>::new();
    let mut in_table = false;
    let page_commands = page_command_names(lines, command);

    for (index, line) in lines.iter().enumerate().take(end).skip(start + 1) {
        let trimmed = line.trim();
        let body = strip_leading_colons(trimmed);
        if body.starts_with("{|") {
            in_table = true;
        }
        if in_table {
            if body.starts_with("|}") {
                in_table = false;
            }
            continue;
        }
        if body.starts_with(';') {
            let heading = render_wikitext(body.trim_start_matches(';').trim());
            if let Some(label) = edition_label(&heading).or_else(|| latest_edition_marker(body)) {
                edition = Some(label);
            }
            context = heading;
            parent_syntax = None;
            syntax_group.clear();
            continue;
        }
        if is_heading(trimmed, 3) {
            let heading = clean_heading(trimmed);
            if let Some(label) = edition_label(&heading).or_else(|| latest_edition_marker(trimmed))
            {
                edition = Some(label);
                context = heading;
                parent_syntax = None;
            } else {
                context = heading;
            }
            syntax_group.clear();
            continue;
        }
        if let Some(label) = edition_label(trimmed).or_else(|| latest_edition_marker(trimmed)) {
            let rendered = render_wikitext(strip_leading_colons(trimmed));
            let rendered = rendered.trim_start_matches(['*', ';']).trim();
            if rendered.is_empty()
                || matches!(rendered, "Java版" | "基岩版" | "Bedrock版")
                || ["Java版（", "基岩版（", "Bedrock版（"]
                    .iter()
                    .any(|prefix| rendered.starts_with(prefix))
            {
                context = if rendered.is_empty() {
                    label.clone()
                } else {
                    rendered.to_string()
                };
                edition = Some(label);
                syntax_group.clear();
                continue;
            }
        }
        let mut found_syntax = false;
        for code in code_values(trimmed) {
            let syntax = normalize_space(&render_wikitext(&code));
            let line_no = index + 1;
            let depth = leading_colons(trimmed);
            let source_body = strip_leading_colons(trimmed);
            let list_child = source_body.starts_with('*');
            let list_depth = source_body
                .chars()
                .take_while(|character| *character == '*')
                .count();
            let source_body = source_body.trim_start_matches('*').trim();
            let code_start = first_code_start(source_body);
            let code_leading = code_start.is_some_and(|start| {
                render_wikitext(&source_body[..start])
                    .trim_matches([' ', ':', '：'])
                    .is_empty()
            });
            let item_edition = code_start
                .and_then(|start| latest_edition_marker(&source_body[..start]))
                .or_else(|| edition.clone());
            let is_full_syntax = code_leading && looks_like_command_syntax(&syntax, &page_commands);
            let omitted_root = syntax.starts_with("... ") || syntax.starts_with("… ");
            let is_subsyntax = code_leading
                && !is_full_syntax
                && ((depth >= 3 && looks_like_subsyntax(&syntax)) || (depth >= 1 && omitted_root));
            if !syntax.is_empty() && (is_full_syntax || is_subsyntax) {
                let item_context = if is_subsyntax && (!omitted_root || list_child) {
                    parent_syntax
                        .as_deref()
                        .map(|parent| format!("{context} / parent syntax: {parent}"))
                        .unwrap_or_else(|| context.clone())
                } else {
                    context.clone()
                };
                let adjacent = last_syntax_line.is_some_and(|previous| {
                    previous == index + 1
                        || (previous <= index
                            && lines[previous..index]
                                .iter()
                                .all(|line| line.trim().is_empty()))
                }) && last_syntax_depth == Some(depth)
                    && last_list_depth == list_depth
                    && syntax_group.last().is_some_and(|&previous| {
                        (output[previous].edition == item_edition
                            || (output[previous].edition.is_some() && item_edition.is_some()))
                            && output[previous].context == item_context
                            && output[previous].description.is_empty()
                            && (list_depth == 0
                                || syntax_signature(&output[previous].syntax)
                                    == syntax_signature(&syntax))
                    });
                if !adjacent {
                    syntax_group.clear();
                }
                syntax_group.push(output.len());
                output.push(WikiSyntaxDescription {
                    syntax: syntax.clone(),
                    description: String::new(),
                    brief: String::new(),
                    brief_explicit: false,
                    edition: item_edition,
                    context: item_context,
                    line: line_no,
                });
                last_syntax_line = Some(line_no);
                last_syntax_depth = Some(depth);
                last_list_depth = list_depth;
                if is_full_syntax || (omitted_root && !list_child) {
                    parent_syntax = Some(syntax);
                }
                found_syntax = true;
            }
        }
        if found_syntax {
            continue;
        }

        if let (Some(_), Some(syntax_depth)) = (last_syntax_line, last_syntax_depth) {
            if leading_colons(trimmed) == syntax_depth + 1
                && !strip_leading_colons(trimmed).starts_with("<code")
                && !strip_leading_colons(trimmed).starts_with("{{cd")
            {
                let fragments = render_edition_fragments(strip_leading_colons(trimmed));
                if fragments.iter().any(|(edition, _)| edition.is_some()) {
                    let mut variants = Vec::new();
                    for &item_index in &syntax_group {
                        if output[item_index].edition.is_none() {
                            output[item_index].edition = Some("je".to_string());
                            let mut bedrock = output[item_index].clone();
                            bedrock.edition = Some("be".to_string());
                            variants.push(item_index);
                            variants.push(output.len());
                            output.push(bedrock);
                        } else {
                            variants.push(item_index);
                        }
                    }
                    syntax_group = variants;
                }
                for (note_edition, note) in fragments {
                    let (explicit_brief, note) = take_explicit_brief(&note);
                    for &item_index in &syntax_group {
                        let item = &mut output[item_index];
                        if note_edition
                            .as_deref()
                            .is_some_and(|edition| item.edition.as_deref() != Some(edition))
                        {
                            continue;
                        }
                        if let Some(brief) = &explicit_brief {
                            if item.brief_explicit {
                                warnings.push(format!(
                                    "{command}: 语法 {} 有多个 brief 覆盖，行 {}",
                                    item.syntax,
                                    index + 1
                                ));
                            } else {
                                item.brief = brief.clone();
                                item.brief_explicit = true;
                            }
                        }
                        if note.is_empty() {
                            continue;
                        }
                        if item.description.is_empty() {
                            item.description = note.clone();
                        } else {
                            item.description.push(' ');
                            item.description.push_str(&note);
                        }
                    }
                }
            }
        }
    }

    if output.is_empty() && command != "execute" {
        warnings.push(format!("{command}: “语法”段中未提取到明确命令语法"));
    }
    for item in &mut output {
        if item.brief.is_empty() {
            item.brief = derive_brief(&item.description);
        }
    }
    output
}

fn syntax_signature(syntax: &str) -> String {
    let mut literals = String::new();
    let mut delimiters = Vec::new();
    for character in syntax.chars() {
        match character {
            '<' | '[' => delimiters.push(character),
            '>' if delimiters.last() == Some(&'<') => {
                delimiters.pop();
            }
            ']' if delimiters.last() == Some(&'[') => {
                delimiters.pop();
            }
            _ if delimiters.is_empty() => literals.push(character),
            _ => {}
        }
    }
    normalize_space(&literals)
}

fn extract_parameters(
    lines: &[&str],
    command: &str,
    max_brief_chars: usize,
    warnings: &mut Vec<String>,
) -> Vec<WikiParameter> {
    let (region_start, region_end) = command_region(lines, command).unwrap_or((0, lines.len()));
    let Some((start, section_level)) =
        find_section_heading(lines, region_start, region_end, "参数")
    else {
        if command != "execute" {
            warnings.push(format!("{command}: 未找到“参数”段落"));
        }
        return Vec::new();
    };
    let end = next_heading_at_or_above(lines, start + 1, section_level).unwrap_or(region_end);
    let mut output = Vec::<WikiParameter>::new();
    let mut context_stack = Vec::<(usize, String)>::new();
    let mut current = Vec::<WikiParameter>::new();
    let mut section_edition = None::<String>;
    let mut last_argument_line = None::<usize>;
    let mut table_end = 0;

    for (index, line) in lines.iter().enumerate().take(end).skip(start + 1) {
        if index < table_end {
            continue;
        }
        let line_no = index + 1;
        let trimmed = line.trim();
        let definition = trimmed.trim_start_matches(';').trim();
        let literal_markup = definition
            .strip_prefix("关键字 ")
            .or_else(|| definition.strip_prefix("固定值 "));
        if trimmed.starts_with(';') && literal_markup.is_some() {
            let markup = literal_markup.unwrap();
            let labels = code_values(markup);
            let rendered = render_wikitext(markup);
            if !labels.is_empty()
                && labels
                    .iter()
                    .all(|label| !label.contains(char::is_whitespace))
                && rendered == labels.join("、")
            {
                for parameter in current.drain(..) {
                    finalize_parameter(parameter, command, max_brief_chars, warnings, &mut output);
                }
                current.push(WikiParameter {
                    names: labels.clone(),
                    primary_names: labels,
                    literal: true,
                    edition: section_edition.clone(),
                    context: context_stack
                        .iter()
                        .map(|(_, heading)| heading.as_str())
                        .collect::<Vec<_>>()
                        .join(" / "),
                    line: line_no,
                    ..WikiParameter::default()
                });
                continue;
            }
        }
        if is_heading(trimmed, 3) || is_heading(trimmed, 4) {
            let level = heading_level(trimmed);
            while context_stack
                .last()
                .is_some_and(|(current_level, _)| *current_level >= level)
            {
                context_stack.pop();
            }
            let heading = clean_heading(trimmed);
            if let Some(edition) = edition_label(&heading) {
                section_edition = Some(edition);
            }
            context_stack.push((level, heading));
            continue;
        }
        if trimmed.starts_with(';')
            && find_named_templates(trimmed, "argument").is_empty()
            && parse_inline_parameter_header(trimmed).is_none()
        {
            for parameter in current.drain(..) {
                finalize_parameter(parameter, command, max_brief_chars, warnings, &mut output);
            }
            while context_stack.last().is_some_and(|(level, _)| *level >= 3) {
                context_stack.pop();
            }
            let heading = render_wikitext(trimmed.trim_start_matches(';').trim());
            if let Some(edition) = edition_label(&heading) {
                section_edition = Some(edition);
            }
            context_stack.push((3, heading));
            continue;
        }

        let mut arguments = find_named_template_spans(trimmed, "argument");
        if let Some((_, start)) = arguments.first() {
            let prefix = render_wikitext(strip_leading_colons(&trimmed[..*start]));
            let prefix = prefix.trim_matches([';', ':', '：', ' ']);
            let heading_prefix =
                prefix.is_empty() || matches!(prefix, "Java版" | "基岩版" | "Bedrock版");
            if leading_colons(trimmed) > 1 || !heading_prefix {
                // Argument references in prose are markup, not new definitions.
                arguments.clear();
            }
        }
        if !arguments.is_empty() {
            let editions: Vec<_> = arguments
                .iter()
                .map(|(template, start)| {
                    argument_template_edition(template)
                        .or_else(|| latest_edition_marker(&trimmed[..*start]))
                        .or_else(|| section_edition.clone())
                })
                .collect();
            // Consecutive JE/BE headings can share the prose that follows them.
            // Do not merge unrelated or already documented parameter blocks.
            let adjacent_variants = last_argument_line.is_some_and(|previous| {
                previous < index
                    && lines[previous + 1..index]
                        .iter()
                        .all(|line| line.trim().is_empty())
            }) && !current.is_empty()
                && current.iter().all(|parameter| {
                    parameter.edition.is_some()
                        && parameter.description.is_empty()
                        && parameter.options.is_empty()
                        && parameter.constraints.is_empty()
                        && parameter.brief.is_empty()
                })
                && editions.iter().all(|edition| {
                    edition.is_some()
                        && current
                            .iter()
                            .all(|parameter| &parameter.edition != edition)
                });
            if !adjacent_variants {
                for parameter in current.drain(..) {
                    finalize_parameter(parameter, command, max_brief_chars, warnings, &mut output);
                }
            }
            for (template, template_start) in arguments {
                let edition = argument_template_edition(&template)
                    .or_else(|| latest_edition_marker(&trimmed[..template_start]))
                    .or_else(|| section_edition.clone());
                let parameter_index = current
                    .iter()
                    .position(|parameter| parameter.edition == edition);
                let parameter_index = parameter_index.unwrap_or_else(|| {
                    current.push(WikiParameter {
                        edition,
                        context: context_stack
                            .iter()
                            .map(|(_, heading)| heading.as_str())
                            .collect::<Vec<_>>()
                            .join(" / "),
                        line: line_no,
                        ..WikiParameter::default()
                    });
                    current.len() - 1
                });
                let parameter = &mut current[parameter_index];
                let (names, types, ambiguous) = parse_argument_template(&template);
                if let Some(primary) = names.first() {
                    parameter.primary_names.push(primary.clone());
                }
                parameter.names.extend(names);
                parameter.types.extend(types);
                if ambiguous {
                    warnings.push(format!(
                        "{command}: 参数模板结构未能无歧义解析，行 {line_no}: {}",
                        template.replace('\n', " ")
                    ));
                }
            }
            for parameter in &mut current {
                deduplicate(&mut parameter.names);
                deduplicate(&mut parameter.primary_names);
                deduplicate(&mut parameter.types);
                parameter
                    .types
                    .extend(arg_desc_types(trimmed, parameter.edition.as_deref()));
                deduplicate(&mut parameter.types);
                if parameter.names.is_empty() {
                    warnings.push(format!(
                        "{command}: 行 {line_no} 的 argument 模板没有可识别的参数名"
                    ));
                }
            }
            last_argument_line = Some(index);
            continue;
        }

        if let Some((names, types, header_context)) = parse_inline_parameter_header(trimmed) {
            for parameter in current.drain(..) {
                finalize_parameter(parameter, command, max_brief_chars, warnings, &mut output);
            }
            current.push(WikiParameter {
                primary_names: names
                    .iter()
                    .filter(|name| name.contains(':'))
                    .cloned()
                    .collect(),
                names,
                types,
                edition: latest_edition_marker(trimmed).or_else(|| section_edition.clone()),
                context: context_stack
                    .iter()
                    .map(|(_, heading)| heading.as_str())
                    .chain((!header_context.is_empty()).then_some(header_context.as_str()))
                    .collect::<Vec<_>>()
                    .join(" / "),
                line: line_no,
                ..WikiParameter::default()
            });
            continue;
        }

        if current.is_empty() {
            continue;
        }
        let source_body = strip_leading_colons(trimmed);
        if render_wikitext(source_body).starts_with("本命令不支持的值：") {
            let values = code_values(source_body);
            for parameter in &mut current {
                parameter.unavailable_values.extend(values.clone());
                deduplicate(&mut parameter.unavailable_values);
            }
            continue;
        }
        if source_body.starts_with("{|") {
            let close = (index + 1..end)
                .find(|&line| strip_leading_colons(lines[line].trim()).starts_with("|}"));
            if let Some(close) = close {
                table_end = close + 1;
                match parse_parameter_table(&lines[index + 1..close], index + 2) {
                    Some(options) => {
                        for parameter in &mut current {
                            parameter.options.extend(options.clone());
                        }
                    }
                    None => warnings.push(format!(
                        "{command}: 参数表格不能无歧义提取为选项，行 {line_no}"
                    )),
                }
            } else {
                warnings.push(format!("{command}: 参数表格没有结束标记，行 {line_no}"));
            }
            continue;
        }
        if source_body.starts_with('|') || source_body.starts_with("! ") {
            warnings.push(format!(
                "{command}: 参数表格尚未提取为结构化对象，行 {line_no}"
            ));
            continue;
        }
        if trimmed.is_empty()
            || trimmed.starts_with("{{see also")
            || trimmed.starts_with("{{info needed")
        {
            continue;
        }
        if source_body.starts_with('*') {
            let list_text =
                render_wikitext(strip_leading_colons(trimmed).trim_start_matches('*').trim());
            let context_label = list_text
                .strip_prefix('在')
                .and_then(|text| text.split_once('中'))
                .map(|(label, _)| label)
                .filter(|label| {
                    !label.is_empty()
                        && label.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
                });
            if let Some(label) = context_label {
                for parameter in &current {
                    let mut variant = WikiParameter {
                        names: parameter.names.clone(),
                        types: parameter.types.clone(),
                        edition: parameter.edition.clone(),
                        context: if parameter.context.is_empty() {
                            label.to_string()
                        } else {
                            format!("{} / {label}", parameter.context)
                        },
                        line: line_no,
                        ..WikiParameter::default()
                    };
                    extract_parameter_text(&mut variant, &list_text, line_no, command, warnings);
                    finalize_parameter(variant, command, max_brief_chars, warnings, &mut output);
                }
                continue;
            }
            if ["如果未指定", "若未指定", "默认为", "默认值为"]
                .iter()
                .any(|prefix| list_text.starts_with(prefix))
            {
                for parameter in &mut current {
                    parameter.constraints.push(list_text.clone());
                }
                continue;
            }
            for parameter in &mut current {
                if let Some((depth, option)) = parse_option_line_for_edition(
                    trimmed,
                    line_no,
                    Some(parameter.edition.as_deref().unwrap_or("be")),
                ) {
                    push_nested_option(&mut parameter.options, depth, option);
                } else {
                    extract_parameter_text(parameter, &list_text, line_no, command, warnings);
                }
            }
            continue;
        }
        if leading_colons(trimmed) > 0 {
            let rendered = normalize_space(&render_wikitext(strip_leading_colons(trimmed)));
            if rendered.is_empty() {
                continue;
            }
            warn_unhandled_templates(trimmed, command, line_no, warnings);
            let fragments = render_edition_fragments(strip_leading_colons(trimmed));
            if fragments.iter().any(|(edition, _)| edition.is_some()) {
                // Shared headings may be followed by edition-specific prose. Fork
                // the shared source block, retaining its exact common text in both
                // variants, so a Bedrock note is not lost behind an edition=None
                // candidate and a Java note cannot enter Bedrock resources.
                let mut variants = Vec::new();
                for parameter in current.drain(..) {
                    if parameter.edition.is_none() {
                        for label in ["je", "be"] {
                            let mut variant = parameter.clone();
                            variant.edition = Some(label.to_string());
                            variants.push(variant);
                        }
                    } else {
                        variants.push(parameter);
                    }
                }
                current = variants;
            }
            for (line_edition, fragment) in fragments {
                for parameter in &mut current {
                    if line_edition
                        .as_deref()
                        .is_some_and(|edition| parameter.edition.as_deref() != Some(edition))
                    {
                        continue;
                    }
                    extract_parameter_text(parameter, &fragment, line_no, command, warnings);
                    parameter.types.extend(arg_desc_types(
                        trimmed,
                        parameter.edition.as_deref().or(line_edition.as_deref()),
                    ));
                    deduplicate(&mut parameter.types);
                }
            }
            continue;
        }

        // Tables and unindented markup are not reliably attached to an argument.
        if trimmed.starts_with("{|") || trimmed.starts_with("|-") || trimmed.starts_with("|") {
            warnings.push(format!(
                "{command}: 参数块包含无法关联的表格/标记，行 {line_no}"
            ));
        }
    }
    for parameter in current {
        finalize_parameter(parameter, command, max_brief_chars, warnings, &mut output);
    }
    output
}

/// Read simple option tables with vertical cells and rowspans. Unsupported layouts
/// are reported instead of guessing which constraint belongs to which option.
fn parse_parameter_table(lines: &[&str], first_line: usize) -> Option<Vec<WikiOption>> {
    #[derive(Clone)]
    struct Cell {
        text: String,
        rows: usize,
        line: usize,
    }
    let mut headers = Vec::new();
    let mut rows = Vec::<Vec<Cell>>::new();
    let mut row = Vec::new();
    let rowspan = Regex::new(r#"rowspan\s*=\s*["']?(\d+)"#).unwrap();
    for (index, line) in lines.iter().enumerate() {
        let body = strip_leading_colons(line.trim());
        if let Some(header) = body.strip_prefix('!') {
            if header.contains("!!") || !rows.is_empty() || !row.is_empty() {
                return None;
            }
            headers.push(render_wikitext(header.trim()));
        } else if body.starts_with("|-") {
            if !row.is_empty() {
                rows.push(std::mem::take(&mut row));
            }
        } else if let Some(cell) = body.strip_prefix('|') {
            if cell.contains("||") || cell.contains("colspan") {
                return None;
            }
            let fields = split_top_level(cell);
            let attributes = fields.len() > 1
                && ["rowspan", "style", "class", "align"]
                    .iter()
                    .any(|key| fields[0].contains(key));
            let span = if attributes {
                rowspan
                    .captures(&fields[0])
                    .and_then(|m| m[1].parse().ok())
                    .unwrap_or(1)
            } else {
                1
            };
            if span == 0 {
                return None;
            }
            row.push(Cell {
                text: if attributes {
                    fields[1..].join("|")
                } else {
                    cell.to_string()
                },
                rows: span,
                line: first_line + index,
            });
        } else if !body.is_empty() {
            return None;
        }
    }
    if !row.is_empty() {
        rows.push(row);
    }
    if headers.len() < 2 || rows.is_empty() {
        return None;
    }
    let mut carried = vec![None::<Cell>; headers.len()];
    let mut options = Vec::new();
    for row in rows {
        let mut fresh = row.into_iter();
        let mut cells = Vec::new();
        for prior in &mut carried {
            let cell = prior.take().or_else(|| fresh.next())?;
            if cell.rows > 1 {
                let mut remaining = cell.clone();
                remaining.rows -= 1;
                *prior = Some(remaining);
            }
            cells.push(cell);
        }
        if fresh.next().is_some() {
            return None;
        }
        let codes = code_values(&cells[0].text);
        let values: Vec<_> = codes
            .iter()
            .filter_map(|code| {
                let rendered = render_wikitext(code);
                rendered.split_whitespace().next().map(str::to_string)
            })
            .collect();
        if values.is_empty() {
            return None;
        }
        let mut label = render_wikitext(&cells[0].text);
        for code in &codes {
            label = label.replacen(&render_wikitext(code), "", 1);
        }
        let description = label
            .trim()
            .trim_matches(['（', '）', '(', ')'])
            .trim()
            .to_string();
        let constraints = cells
            .iter()
            .zip(&headers)
            .skip(1)
            .map(|(cell, header)| format!("{header}：{}", render_wikitext(&cell.text)))
            .collect::<Vec<_>>();
        options.push(WikiOption {
            values,
            description,
            constraints,
            options: Vec::new(),
            line: cells[0].line,
        });
    }
    if carried.iter().any(Option::is_some) {
        return None;
    }
    Some(options)
}

fn extract_parameter_text(
    parameter: &mut WikiParameter,
    text: &str,
    line: usize,
    command: &str,
    warnings: &mut Vec<String>,
) {
    let previous = parameter.description.len();
    extract_parameter_text_inner(parameter, text, line, command, warnings);
    if parameter.description.len() > previous {
        parameter.paragraphs.push(WikiParagraph {
            text: parameter.description[previous..].trim().to_string(),
            line,
        });
    }
}

fn extract_parameter_text_inner(
    parameter: &mut WikiParameter,
    text: &str,
    line: usize,
    command: &str,
    warnings: &mut Vec<String>,
) {
    let (brief, text) = take_explicit_brief(text);
    if let Some(brief) = brief {
        if parameter.brief.is_empty() {
            parameter.brief = brief;
            parameter.brief_explicit = true;
        } else {
            warnings.push(format!(
                "{command}: 参数 {} 有多个 brief 覆盖，行 {line}",
                display_names(parameter)
            ));
        }
    }

    let exact_intro = "必须为以下其中之一：";
    if let Some(index) = text.find(exact_intro) {
        for prefix in split_chinese_sentences(text[..index].trim()) {
            if let Some(prefix) = description_fragment(parameter, &prefix) {
                append_description(parameter, prefix.trim_end_matches(['，', '、', ':', '：']));
            }
        }
        parameter.enumeration_intro = Some(exact_intro.to_string());
        let suffix = text[index + exact_intro.len()..].trim();
        if !suffix.is_empty() {
            parameter.constraints.push(suffix.to_string());
        }
        return;
    }
    let enum_parameter = parameter
        .types
        .iter()
        .any(|ty| ty.eq_ignore_ascii_case("enum"));
    if let Some(index) = noncanonical_enum_lead_in(&text, enum_parameter) {
        append_description(parameter, text[..index].trim());
        warnings.push(format!(
            "{command}: 非规范枚举引导待 patch，参数 {}，行 {line}: {text}",
            display_names(parameter)
        ));
        parameter.constraints.push(text[index..].trim().to_string());
        return;
    }

    for fragment in split_chinese_sentences(&text) {
        if is_constraint_clause(&fragment) {
            parameter.constraints.push(fragment.clone());
        }
        if let Some(description) = description_fragment(parameter, &fragment) {
            append_description(parameter, description);
        }
    }
}

fn finalize_parameter(
    mut parameter: WikiParameter,
    command: &str,
    max_brief_chars: usize,
    warnings: &mut Vec<String>,
    output: &mut Vec<WikiParameter>,
) {
    parameter.description = normalize_space(&parameter.description);
    parameter.brief = normalize_space(&parameter.brief);
    if parameter.brief.is_empty() {
        parameter.brief = derive_brief(&parameter.description);
    }
    if parameter.brief.is_empty() {
        warnings.push(format!(
            "{command}: 参数 {} 行 {} 没有用途短语可作brief，需来源brief指令",
            display_names(&parameter),
            parameter.line
        ));
    }
    let length = parameter.brief.chars().count();
    if length > max_brief_chars {
        warnings.push(format!(
            "{command}: 参数 {} brief 超长，行 {}，长度 {}，上限 {}：{}",
            display_names(&parameter),
            parameter.line,
            length,
            max_brief_chars,
            parameter.brief
        ));
    }
    if parameter.names.is_empty() {
        warnings.push(format!(
            "{command}: 参数块行 {} 缺少名称，保留原始块",
            parameter.line
        ));
    }
    if parameter.description.is_empty() && parameter.options.is_empty() {
        warnings.push(format!(
            "{command}: 参数 {} 行 {} 没有可提取说明",
            display_names(&parameter),
            parameter.line
        ));
    }
    output.push(parameter);
}

fn parse_argument_template(template: &str) -> (Vec<String>, Vec<String>, bool) {
    let fields = split_top_level(template);
    let mut names = Vec::new();
    let mut types = Vec::new();
    let mut ambiguous = false;
    let mut saw_name = false;
    for field in fields.into_iter().skip(1) {
        // A pipe escaped for a template argument still separates alternatives
        // in the displayed parameter name, not additional template fields.
        let displayed = field.replace("{{!}}", "|");
        let value = displayed.trim();
        if value.is_empty() || value.contains('=') {
            continue;
        }
        if let Some((name, ty)) = split_typed_name(value) {
            names.push(value.to_string());
            names.push(name);
            types.push(ty);
            saw_name = true;
        } else if !saw_name {
            names.push(value.to_string());
            saw_name = true;
        } else if is_type_name(value) {
            types.push(value.to_string());
        } else if is_argument_name(value) {
            names.push(value.to_string());
        } else {
            ambiguous = true;
            names.push(value.to_string());
        }
    }
    (names, types, ambiguous)
}

fn parse_inline_parameter_header(line: &str) -> Option<(Vec<String>, Vec<String>, String)> {
    if leading_colons(line) > 1 || strip_leading_colons(line).starts_with('*') {
        return None;
    }
    let values = code_values(line);
    if values.is_empty() {
        return None;
    }
    let mut remainder = inline_code_regex().replace_all(line, "").into_owned();
    for name in ["cd", "code", "samp"] {
        for (body, _) in find_named_template_spans(&remainder.clone(), name) {
            remainder = remainder.replace(&format!("{{{{{body}}}}}"), "");
        }
    }
    let header_context = render_wikitext(
        strip_leading_colons(&remainder)
            .trim_start_matches(';')
            .trim(),
    );
    static PAREN: OnceLock<Regex> = OnceLock::new();
    let parenthetical = PAREN.get_or_init(|| Regex::new(r"（[^（）]*）|\([^()]*\)").unwrap());
    let connectors = parenthetical.replace_all(&header_context, "");
    if !connectors
        .trim_matches([
            ' ', '和', '与', '及', '或', '、', ',', '，', ';', '；', ':', '：',
        ])
        .is_empty()
    {
        return None;
    }
    // Only standalone code headings qualify. Code references within prose do not.
    let mut names = Vec::new();
    let mut types = Vec::new();
    for value in values {
        let value = normalize_space(&value);
        let value = value.trim_matches(['<', '>']).to_string();
        let Some((name, ty)) = split_typed_name(&value) else {
            if value.contains('|') {
                names.extend(value.split('|').map(str::to_string));
                names.push(value);
                types.push("enum".to_string());
            } else if value
                .chars()
                .all(|c| c.is_alphanumeric() || "_-.".contains(c))
            {
                names.push(value);
            }
            continue;
        };
        let coordinates = matches!(ty.as_str(), "x y z" | "x z" | "yRot xRot");
        if name != "minecraft"
            && is_argument_name(&name)
            && (is_type_name(&ty) || coordinates)
            && !value.contains('|')
        {
            names.push(value);
            names.push(name);
            types.push(ty);
        }
    }
    deduplicate(&mut names);
    deduplicate(&mut types);
    (!names.is_empty()).then_some((names, types, header_context))
}

fn argument_template_edition(template: &str) -> Option<String> {
    split_top_level(template)
        .iter()
        .skip(1)
        .find_map(|field| match field.trim() {
            "be=1" | "bedrock=1" => Some("be".to_string()),
            "je=1" | "java=1" => Some("je".to_string()),
            _ => None,
        })
}

#[cfg(test)]
fn parse_option_line(line: &str, line_no: usize) -> Option<(usize, WikiOption)> {
    parse_option_line_for_edition(line, line_no, None)
}

fn parse_option_line_for_edition(
    line: &str,
    line_no: usize,
    edition: Option<&str>,
) -> Option<(usize, WikiOption)> {
    let colon_count = line
        .chars()
        .take_while(|character| *character == ':')
        .count();
    let after_colons = &line[colon_count..];
    let depth = after_colons
        .chars()
        .take_while(|character| *character == '*')
        .count()
        .max(1);
    let body = after_colons.trim_start_matches('*').trim();
    if body.is_empty() {
        return None;
    }
    let description_tail = split_option_description(body);
    let option_head = description_tail
        .and_then(|tail| body.strip_suffix(tail))
        .unwrap_or(body);
    let mut values = code_values(option_head);
    if values.is_empty() {
        return None;
    }
    // Aliases explicitly documented in a parenthetical abbreviation clause
    // are the same option, not additional descriptions or new resource nodes.
    let aliases = Regex::new(r"[（(][^（）()]*可简写为[^（）()]*[）)]").unwrap();
    for clause in aliases.find_iter(body) {
        values.extend(code_values(clause.as_str()));
    }
    deduplicate(&mut values);
    let description = description_tail
        .map(|value| match edition {
            Some(edition) => render_edition_fragments(value)
                .into_iter()
                .filter(|(scope, _)| scope.as_deref().is_none_or(|scope| scope == edition))
                .map(|(_, text)| text)
                .collect::<Vec<_>>()
                .join(" "),
            None => normalize_space(&render_wikitext(value)),
        })
        .filter(|value| !value.is_empty())
        .or_else(|| {
            let start = first_code_start(body)?;
            let prefix = body[..start].trim();
            if !prefix.ends_with([':', '：']) {
                return None;
            }
            let label = render_wikitext(prefix.trim_end_matches([':', '：']).trim());
            (!label.is_empty()).then_some(label)
        })
        .unwrap_or_default();
    Some((
        depth,
        WikiOption {
            values,
            description,
            constraints: Vec::new(),
            options: Vec::new(),
            line: line_no,
        },
    ))
}

fn push_nested_option(options: &mut Vec<WikiOption>, depth: usize, option: WikiOption) {
    if depth <= 1 || options.is_empty() {
        options.push(option);
    } else {
        push_nested_option(&mut options.last_mut().unwrap().options, depth - 1, option);
    }
}

fn split_option_description(text: &str) -> Option<&str> {
    for delimiter in [" —— ", " — ", " – ", " - ", "——", "—", "–"] {
        if let Some((_, tail)) = text.split_once(delimiter) {
            return Some(tail);
        }
    }
    for closing in ["</code>", "</samp>", "</kbd>"] {
        if let Some(index) = text.to_ascii_lowercase().find(closing) {
            let tail = text[index + closing.len()..].trim();
            if !tail.is_empty() {
                return Some(tail);
            }
        }
    }
    if text.starts_with("{{") {
        if let Some(length) = find_template_end(text) {
            let name = text[2..length - 2].split('|').next().unwrap_or_default();
            if matches!(name.to_ascii_lowercase().as_str(), "cd" | "code" | "samp") {
                let tail = text[length..].trim();
                if !tail.is_empty() {
                    return Some(tail);
                }
            }
        }
    }
    None
}

fn arg_desc_types(line: &str, edition: Option<&str>) -> Vec<String> {
    let mut bedrock_types = Vec::new();
    let mut java_types = Vec::new();
    for body in find_named_templates(line, "arg desc") {
        for field in split_top_level(&body).into_iter().skip(1) {
            let Some((key, value)) = field.split_once('=') else {
                continue;
            };
            let key = key.trim().to_ascii_lowercase();
            if matches!(key.as_str(), "be" | "bedrock" | "je" | "java") {
                let value = value.trim().to_string();
                if !value.is_empty() {
                    if matches!(key.as_str(), "be" | "bedrock") {
                        bedrock_types.push(value);
                    } else {
                        java_types.push(value);
                    }
                }
            }
        }
    }
    match edition {
        Some("be") => bedrock_types,
        Some("je") => java_types,
        _ if !bedrock_types.is_empty() => bedrock_types,
        _ => java_types,
    }
}

fn code_values(text: &str) -> Vec<String> {
    let mut values = Vec::new();
    for (template, contents) in named_templates_with_content(text) {
        if matches!(template.as_str(), "cd" | "code" | "samp") {
            for content in positional_code_fields(&contents) {
                if !content.trim().is_empty() {
                    values.push(render_wikitext(&content));
                }
            }
        }
    }
    for capture in inline_code_regex().captures_iter(text) {
        if let Some(value) = (1..=4).find_map(|index| capture.get(index)) {
            values.push(render_wikitext(value.as_str()));
        }
    }
    deduplicate(&mut values);
    values
}

fn append_description(parameter: &mut WikiParameter, text: &str) {
    if !text.trim().is_empty() {
        if !parameter.description.is_empty() {
            parameter.description.push(' ');
        }
        parameter.description.push_str(text.trim());
    }
}

fn derive_brief(description: &str) -> String {
    let description = description.trim();
    let first_sentence = description
        .split(['。', '；', '\n'])
        .next()
        .unwrap_or_default();
    let constraint = [
        "默认为",
        "默认值为",
        "若未指定",
        "如果未指定",
        "若指定",
        "如果指定",
        "必须",
        "应当",
        "应在",
        "必须含有",
        "只能填写",
        "由于漏洞",
    ]
    .iter()
    .filter_map(|marker| first_sentence.find(marker))
    .min()
    .unwrap_or(first_sentence.len());
    let mut brief = first_sentence[..constraint]
        .trim()
        .trim_end_matches(['，', '：', ':', '。'])
        .to_string();
    for prefix in ["指定", "定义", "选择", "输入", "填写", "设置"] {
        if let Some(rest) = brief.strip_prefix(prefix) {
            brief = rest.trim().to_string();
            break;
        }
    }
    brief
}

fn take_explicit_brief(text: &str) -> (Option<String>, String) {
    static BRIEF: OnceLock<Regex> = OnceLock::new();
    let re = BRIEF
        .get_or_init(|| Regex::new(r"(?:^|[，。；\s])brief\s*[:：]\s*([^，。；]+)\s*$").unwrap());
    let Some(captures) = re.captures(text) else {
        return (None, text.to_string());
    };
    let brief = captures.get(1).map(|m| m.as_str().trim().to_string());
    let matched = captures.get(0).unwrap();
    let mut remaining = text.to_string();
    let start = match text[matched.start()..].chars().next() {
        Some(character @ ('。' | '；')) => matched.start() + character.len_utf8(),
        _ => matched.start(),
    };
    remaining.replace_range(start..matched.end(), "");
    (brief, remaining.trim().to_string())
}

fn noncanonical_enum_lead_in(text: &str, enum_parameter: bool) -> Option<usize> {
    if !enum_parameter {
        return None;
    }
    let candidates = [
        "必须为以下之一",
        "必须是以下之一",
        "必须为下列之一",
        "必须是下列之一",
        "必须为其中之一",
        "必须是其中之一",
        "必须为",
        "必须是",
    ];
    candidates
        .iter()
        .filter_map(|candidate| text.find(candidate))
        .filter(|index| {
            let tail = &text[*index..];
            tail.contains("之一") || tail.contains("或") || tail.contains("、")
        })
        .min()
}

fn is_constraint_clause(text: &str) -> bool {
    let markers = [
        "默认为",
        "默认值为",
        "若未指定",
        "如果未指定",
        "若指定",
        "如果指定",
        "必须含有",
        "必须包含",
        "必须在",
        "必须是",
        "必须为",
        "应当",
        "应在",
        "应为",
        "不得",
        "不能",
        "不可以",
        "不应",
        "小于",
        "大于",
        "不低于",
        "不超过",
        "介于",
        "至少",
        "至多",
        "只能填写",
        "只能为",
        "只能是",
        "可用端口范围为",
        "范围为",
        "取值范围为",
        "值域为",
        "由于漏洞",
        "可以为空",
        "可为空",
    ];
    markers.iter().any(|marker| text.contains(marker))
}

fn description_fragment<'a>(parameter: &WikiParameter, text: &'a str) -> Option<&'a str> {
    let lower = text.to_ascii_lowercase();
    let boolean_parameter = parameter
        .types
        .iter()
        .any(|ty| ty.eq_ignore_ascii_case("boolean") || ty.eq_ignore_ascii_case("bool"));
    if text.contains("由于漏洞")
        || text.contains("实际可以为空")
        || lower.contains("only because of a bug")
    {
        return None;
    }
    if boolean_parameter
        && text.contains("必须为")
        && text.contains("true")
        && text.contains("false")
    {
        return None;
    }
    if boolean_parameter {
        if text.starts_with("若未指定") || text.starts_with("如果未指定") {
            return None;
        }
        if matches!(
            text.trim_end_matches(['，', '。', '：', ':', ' ']),
            "如果指定，则" | "若指定，则" | "如果指定" | "若指定"
        ) {
            return None;
        }
        for marker in ["默认为", "默认值为"] {
            if let Some(index) = text.find(marker) {
                let prefix = text[..index].trim_end_matches(['，', '、', ' ']).trim();
                return (!prefix.is_empty()).then_some(prefix);
            }
        }
    }
    Some(text)
}

fn split_chinese_sentences(text: &str) -> Vec<String> {
    let mut output = Vec::new();
    let mut start = 0;
    for (index, character) in text.char_indices() {
        if matches!(character, '。' | '；') {
            let end = index + character.len_utf8();
            output.push(text[start..end].to_string());
            start = end;
        }
    }
    if start < text.len() {
        output.push(text[start..].to_string());
    }
    output
}

fn split_typed_name(value: &str) -> Option<(String, String)> {
    // A C++ type such as Json::Value contains colons that are not name separators.
    let bytes = value.as_bytes();
    let index = value.char_indices().find_map(|(index, character)| {
        (character == ':'
            && (index == 0 || bytes[index - 1] != b':')
            && bytes.get(index + 1) != Some(&b':'))
        .then_some(index)
    })?;
    let left = value[..index].trim();
    let right = value[index + 1..].trim();
    if left.is_empty() || right.is_empty() || !left.chars().any(char::is_alphabetic) {
        return None;
    }
    Some((left.to_string(), right.to_string()))
}

fn is_type_name(value: &str) -> bool {
    let known = [
        "enum",
        "int",
        "integer",
        "float",
        "string",
        "basic_string",
        "boolean",
        "bool",
        "entity",
        "target",
        "resource",
        "item_stack",
        "item_predicate",
        "block_pos",
        "block_predicate",
        "dimension",
        "vec3",
        "vec2",
        "position",
        "game_profile",
        "nbt_compound_tag",
        "component",
        "Component",
        "time",
        "text",
        "json",
        "Json::Value",
        "CommandPosition",
        "CommandPositionFloat",
        "CommandSelector<Player>",
        "CommandSelector<Actor>",
        "CommandItem",
        "Block",
        "BlockStateCommandParam",
        "CommandRawText",
        "CommandMessage",
        "EntityType",
        "DamageCause",
        "Easing",
        "StructureSaveMode",
        "StructureAnimationMode",
        "Rotation",
        "Mirror",
        "Float",
        "RelativeFloat",
        "TileRawSet",
        "NORMAL_ID",
        "NAMESPACE_ID",
    ];
    known
        .iter()
        .any(|candidate| value.eq_ignore_ascii_case(candidate))
        || value.contains("::")
        || value.contains('<') && value.contains('>')
        || value.chars().next().is_some_and(char::is_uppercase)
}

fn is_argument_name(value: &str) -> bool {
    value.chars().any(char::is_alphabetic)
        && value.chars().all(|character| {
            character.is_alphanumeric() || character == '_' || character == '-' || character == ' '
        })
}

fn display_names(parameter: &WikiParameter) -> String {
    if parameter.names.is_empty() {
        "<unknown>".into()
    } else {
        parameter.names.join(" | ")
    }
}

fn latest_edition_marker(line: &str) -> Option<String> {
    let markers = [
        ("{{el|be", "be"),
        ("{{el|bedrock", "be"),
        ("{{in|be", "be"),
        ("{{el|je", "je"),
        ("{{el|java", "je"),
        ("{{in|je", "je"),
    ];
    markers
        .iter()
        .filter_map(|(marker, edition)| line.rfind(marker).map(|index| (index, *edition)))
        .max_by_key(|(index, _)| *index)
        .map(|(_, edition)| edition.to_string())
}

/// Edition markers affect the prose after them, never the earlier shared sentence.
fn render_edition_fragments(text: &str) -> Vec<(Option<String>, String)> {
    let scoped = find_named_templates(text, "only").iter().any(|template| {
        let fields = split_top_level(template);
        fields.iter().any(|field| field.trim().starts_with("for="))
            && fields.iter().skip(1).any(|field| {
                matches!(
                    field.trim().to_ascii_lowercase().as_str(),
                    "be" | "bedrock" | "je" | "java"
                )
            })
    });
    if scoped {
        return ["je", "be"]
            .into_iter()
            .filter_map(|edition| {
                let text = select_inline_edition_content(text, edition);
                let text = render_edition_fragments_base(&text)
                    .into_iter()
                    .filter(|(scope, _)| scope.as_deref().is_none_or(|scope| scope == edition))
                    .map(|(_, text)| text)
                    .collect::<Vec<_>>()
                    .join(" ");
                (!text.is_empty()).then(|| (Some(edition.to_string()), text))
            })
            .collect();
    }
    render_edition_fragments_base(text)
}

fn select_inline_edition_content(input: &str, edition: &str) -> String {
    let mut output = String::new();
    let mut cursor = 0;
    while let Some(relative) = input[cursor..].find("{{") {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        let Some(length) = find_template_end(&input[start..]) else {
            output.push_str(&input[start..]);
            return output;
        };
        let body = &input[start + 2..start + length - 2];
        let mut next = start + length;
        let fields = split_top_level(body);
        let scope = fields.iter().skip(1).find_map(|field| {
            match field.trim().to_ascii_lowercase().as_str() {
                "be" | "bedrock" => Some("be"),
                "je" | "java" => Some("je"),
                _ => None,
            }
        });
        let content = fields
            .iter()
            .find_map(|field| field.trim().strip_prefix("for="));
        if fields[0].trim().eq_ignore_ascii_case("only") && scope.is_some() && content.is_some() {
            if scope == Some(edition) {
                output.push_str(&select_inline_edition_content(content.unwrap(), edition));
            } else {
                for conjunction in ["，以及", "以及", "或", "、", "和", "及"] {
                    if output.ends_with(conjunction) {
                        output.truncate(output.len() - conjunction.len());
                        break;
                    }
                }
                // “JE range 或 BE range” is an edition alternative, not an
                // incomplete disjunction in either edition's independent text.
                if let Some(following) = input[next..].strip_prefix('或') {
                    if let Some(length) = find_template_end(following) {
                        let fields = split_top_level(&following[2..length - 2]);
                        if fields[0].trim().eq_ignore_ascii_case("only")
                            && fields.iter().any(|field| {
                                match field.trim().to_ascii_lowercase().as_str() {
                                    "be" | "bedrock" => edition == "be",
                                    "je" | "java" => edition == "je",
                                    _ => false,
                                }
                            })
                        {
                            next += '或'.len_utf8();
                        }
                    }
                }
            }
        } else {
            output.push_str("{{");
            output.push_str(&select_inline_edition_content(body, edition));
            output.push_str("}}");
        }
        cursor = next;
    }
    output.push_str(&input[cursor..]);
    output
}

fn render_edition_fragments_base(text: &str) -> Vec<(Option<String>, String)> {
    static MARKER: OnceLock<Regex> = OnceLock::new();
    let marker = MARKER.get_or_init(|| {
        Regex::new(r"(?i)\{\{(?:in|el)\|(be|bedrock|je|java)(?:\|[^{}]*)?\}\}").unwrap()
    });
    let mut fragments = Vec::new();
    let mut cursor = 0;
    let mut edition = None;
    for capture in marker.captures_iter(text) {
        let span = capture.get(0).unwrap();
        let rendered = render_wikitext(&text[cursor..span.start()]);
        if !rendered.is_empty() {
            fragments.push((edition.clone(), rendered));
        }
        edition = Some(match capture[1].to_ascii_lowercase().as_str() {
            "be" | "bedrock" => "be".to_string(),
            _ => "je".to_string(),
        });
        cursor = span.end();
    }
    let rendered = render_wikitext(&text[cursor..]);
    if !rendered.is_empty() {
        fragments.push((edition, rendered));
    }
    fragments
}

fn edition_label(line: &str) -> Option<String> {
    if line.contains("Java版") {
        Some("je".into())
    } else if line.contains("基岩版") || line.contains("Bedrock版") {
        Some("be".into())
    } else {
        None
    }
}

fn page_command_names(lines: &[&str], command: &str) -> Vec<String> {
    let mut names = vec![command.to_ascii_lowercase()];
    for line in lines
        .iter()
        .take_while(|line| !line.trim().starts_with("=="))
    {
        let trimmed = line.trim();
        if trimmed.starts_with("|name=") || trimmed.starts_with("|name =") {
            if let Some((_, name)) = trimmed.split_once('=') {
                let name = name.trim();
                if !name.is_empty() {
                    names.push(name.to_ascii_lowercase());
                }
            }
        }
        if trimmed.starts_with("|alias=") || trimmed.starts_with("|alias =") {
            for body in find_named_templates(trimmed, "cmd") {
                if let Some(alias) = split_top_level(&body)
                    .iter()
                    .skip(1)
                    .find(|field| !field.trim().contains('='))
                {
                    names.push(alias.trim().to_ascii_lowercase());
                }
            }
        }
    }
    deduplicate(&mut names);
    names
}

fn looks_like_command_syntax(text: &str, commands: &[String]) -> bool {
    let first = text
        .trim_start_matches('/')
        .split_whitespace()
        .next()
        .unwrap_or_default()
        .trim_matches(['<', '>', '[', ']', '|', '(', ')']);
    commands
        .iter()
        .any(|command| first.eq_ignore_ascii_case(command))
        || (first.starts_with('<')
            && commands
                .iter()
                .any(|command| text.to_ascii_lowercase().contains(command)))
}

fn looks_like_subsyntax(text: &str) -> bool {
    let first = text.split_whitespace().next().unwrap_or_default();
    !first.is_empty()
        && !first.starts_with('<')
        && first
            .chars()
            .next()
            .is_some_and(|character| character.is_ascii_alphabetic() || character == '_')
}

fn inline_code_regex() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| {
        Regex::new(r"(?is)<code(?:\s+[^>]*)?>(.*?)</code>|<samp(?:\s+[^>]*)?>(.*?)</samp>|<kbd(?:\s+[^>]*)?>(.*?)</kbd>|<nowiki(?:\s+[^>]*)?>(.*?)</nowiki>")
            .unwrap()
    })
}

fn first_code_start(text: &str) -> Option<usize> {
    let mut starts = Vec::new();
    if let Some(span) = inline_code_regex().find(text) {
        starts.push(span.start());
    }
    for name in ["cd", "code", "samp"] {
        starts.extend(
            find_named_template_spans(text, name)
                .into_iter()
                .map(|(_, start)| start),
        );
    }
    starts.into_iter().min()
}

fn command_region(lines: &[&str], command: &str) -> Option<(usize, usize)> {
    let start = lines.iter().position(|line| {
        let trimmed = line.trim();
        is_heading(trimmed, 2) && clean_heading(trimmed).eq_ignore_ascii_case(command)
    })?;
    let end = next_heading_at_or_above(lines, start + 1, 2).unwrap_or(lines.len());
    Some((start + 1, end))
}

fn find_section_heading(
    lines: &[&str],
    region_start: usize,
    region_end: usize,
    title: &str,
) -> Option<(usize, usize)> {
    (region_start..region_end).find_map(|index| {
        let trimmed = lines[index].trim();
        let level = heading_level(trimmed);
        (level >= 2 && is_heading(trimmed, level) && clean_heading(trimmed) == title)
            .then_some((index, level))
    })
}

fn next_heading_at_or_above(lines: &[&str], start: usize, level: usize) -> Option<usize> {
    lines
        .iter()
        .enumerate()
        .skip(start)
        .find_map(|(index, line)| {
            let trimmed = line.trim();
            let candidate_level = heading_level(trimmed);
            (candidate_level >= 2
                && candidate_level <= level
                && is_heading(trimmed, candidate_level))
            .then_some(index)
        })
}

fn is_heading(line: &str, level: usize) -> bool {
    let marker = "=".repeat(level);
    line.starts_with(&marker) && line.ends_with(&marker) && line.len() > level * 2
}

fn heading_level(line: &str) -> usize {
    line.chars()
        .take_while(|character| *character == '=')
        .count()
}

fn clean_heading(line: &str) -> String {
    line.trim_matches('=')
        .trim()
        .to_string()
        .chars()
        .take(180)
        .collect()
}

fn leading_colons(line: &str) -> usize {
    line.chars()
        .take_while(|character| *character == ':')
        .count()
}

fn strip_leading_colons(line: &str) -> &str {
    line.trim_start_matches(':').trim()
}

fn brace_delta(line: &str) -> i32 {
    (line.matches("{{").count() as i32) - (line.matches("}}").count() as i32)
}

fn find_named_templates(line: &str, name: &str) -> Vec<String> {
    named_templates_with_content(line)
        .into_iter()
        .filter_map(|(template_name, content)| {
            template_name
                .eq_ignore_ascii_case(name)
                .then_some(content.to_string())
        })
        .collect()
}

fn find_named_template_spans(line: &str, name: &str) -> Vec<(String, usize)> {
    let mut output = Vec::new();
    let mut cursor = 0;
    while let Some(relative) = line[cursor..].find("{{") {
        let start = cursor + relative;
        let Some(length) = find_template_end(&line[start..]) else {
            break;
        };
        let body = &line[start + 2..start + length - 2];
        let template_name = body.split('|').next().unwrap_or_default().trim();
        if template_name.eq_ignore_ascii_case(name) {
            output.push((body.to_string(), start));
        }
        cursor = start + length;
        if cursor >= line.len() {
            break;
        }
    }
    output
}

fn named_templates_with_content(line: &str) -> Vec<(String, String)> {
    let mut output = Vec::new();
    let mut cursor = 0;
    while let Some(relative) = line[cursor..].find("{{") {
        let start = cursor + relative;
        let Some(length) = find_template_end(&line[start..]) else {
            break;
        };
        let body = &line[start + 2..start + length - 2];
        let name = body
            .split('|')
            .next()
            .unwrap_or_default()
            .trim()
            .to_string();
        output.push((name, body.to_string()));
        cursor = start + length;
        if cursor >= line.len() {
            break;
        }
    }
    output
}

fn find_template_end(text: &str) -> Option<usize> {
    let mut depth = 0_i32;
    let mut index = 0;
    while index + 1 < text.len() {
        if text[index..].starts_with("{{") {
            depth += 1;
            index += 2;
            continue;
        }
        if text[index..].starts_with("}}") {
            depth -= 1;
            index += 2;
            if depth == 0 {
                return Some(index);
            }
            continue;
        }
        index += text[index..].chars().next()?.len_utf8();
    }
    None
}

fn split_top_level(template_body: &str) -> Vec<String> {
    let mut fields = Vec::new();
    let mut start = 0;
    let mut braces = 0_i32;
    let mut links = 0_i32;
    let bytes = template_body.as_bytes();
    let mut index = 0;
    while index < bytes.len() {
        if index + 1 < bytes.len() && &bytes[index..index + 2] == b"{{" {
            braces += 1;
            index += 2;
        } else if index + 1 < bytes.len() && &bytes[index..index + 2] == b"}}" {
            braces -= 1;
            index += 2;
        } else if index + 1 < bytes.len() && &bytes[index..index + 2] == b"[[" {
            links += 1;
            index += 2;
        } else if index + 1 < bytes.len() && &bytes[index..index + 2] == b"]]" {
            links -= 1;
            index += 2;
        } else if bytes[index] == b'|' && braces == 0 && links == 0 {
            fields.push(template_body[start..index].to_string());
            index += 1;
            start = index;
        } else {
            index += 1;
        }
    }
    fields.push(template_body[start..].to_string());
    fields
}

fn positional_code_fields(body: &str) -> Vec<String> {
    split_top_level(body)
        .into_iter()
        .skip(1)
        .filter(|field| !is_named_template_field(field))
        .collect()
}

fn warn_unhandled_templates(text: &str, command: &str, line: usize, warnings: &mut Vec<String>) {
    for (name, body) in named_templates_with_content(text) {
        let normalized = name.to_ascii_lowercase();
        if !matches!(
            normalized.as_str(),
            "cd" | "code"
                | "samp"
                | "nowrap"
                | "mono"
                | "only"
                | "onlybe"
                | "onlyje"
                | "exclusive"
                | "in"
                | "el"
                | "in exp"
                | "experiment"
                | "range"
                | "cmd"
                | "tr"
                | "nbt"
                | "filepath"
                | "filename"
                | "fn"
                | "bug"
                | "slink"
                | "argument"
                | "arg desc"
                | "info needed"
                | "see also"
                | "lcfirst"
                | "about"
                | "redirect"
                | "infobox command"
                | "!"
        ) {
            let location = if line == 0 {
                "介绍".to_string()
            } else {
                format!("行 {line}")
            };
            warnings.push(format!(
                "{command}: {location} 的模板 {name} 尚未渲染，需复核是否缺少原文内容"
            ));
        }
        for field in split_top_level(&body).iter().skip(1) {
            warn_unhandled_templates(field, command, line, warnings);
        }
    }
}

fn is_named_template_field(field: &str) -> bool {
    field.split_once('=').is_some_and(|(key, _)| {
        !key.trim().is_empty()
            && key
                .trim()
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || c == '_')
    })
}

fn render_wikitext(input: &str) -> String {
    // Resolve balanced outer templates before splitting wikilink labels. A link
    // label can itself contain a template whose pipes are not link separators.
    let mut value = replace_templates(input);
    value = replace_links(&value);
    value = replace_html_code(&value);
    value = value.replace("'''", "").replace("''", "");
    value = value.replace("-{}-", "");
    value = strip_html_tags(&value);
    value = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&times;", "×")
        .replace("&nbsp;", " ");
    normalize_space(&value)
}

fn replace_links(input: &str) -> String {
    static LINK: OnceLock<Regex> = OnceLock::new();
    let regex = LINK.get_or_init(|| Regex::new(r"\[\[([^\]]+)\]\]").unwrap());
    let internal = regex
        .replace_all(input, |captures: &regex::Captures<'_>| {
            let target = captures.get(1).map_or("", |capture| capture.as_str());
            target.split('|').last().unwrap_or(target).to_string()
        })
        .into_owned();
    static EXTERNAL: OnceLock<Regex> = OnceLock::new();
    EXTERNAL
        .get_or_init(|| Regex::new(r"\[https?://[^\s\]]+(?:\s+([^\]]+))?\]").unwrap())
        .replace_all(&internal, |captures: &regex::Captures<'_>| {
            captures
                .get(1)
                .map_or("", |label| label.as_str())
                .to_string()
        })
        .into_owned()
}

fn replace_html_code(input: &str) -> String {
    let mut value = input.to_string();
    for tag in ["code", "samp", "nowiki", "tt", "math", "small"] {
        let start = format!("<{tag}>");
        let end = format!("</{tag}>");
        value = value.replace(&start, "").replace(&end, "");
    }
    value
}

fn replace_templates(input: &str) -> String {
    let mut output = String::new();
    let mut cursor = 0;
    while let Some(relative) = input[cursor..].find("{{") {
        let start = cursor + relative;
        output.push_str(&input[cursor..start]);
        let Some(length) = find_template_end(&input[start..]) else {
            output.push_str(&input[start..]);
            return output;
        };
        let body = &input[start + 2..start + length - 2];
        output.push_str(&render_template(body));
        cursor = start + length;
        if cursor >= input.len() {
            return output;
        }
    }
    output.push_str(&input[cursor..]);
    output
}

fn render_template(body: &str) -> String {
    let fields = split_top_level(body);
    let name = fields.first().map_or("", |field| field.trim());
    match name.to_ascii_lowercase().as_str() {
        "cd" | "code" | "samp" => positional_code_fields(body)
            .iter()
            .map(|field| render_wikitext(field))
            .collect::<Vec<_>>()
            .join("、"),
        "nowrap" | "mono" | "filename" => fields
            .iter()
            .skip(1)
            .find(|field| !field.trim().contains('='))
            .map_or_else(String::new, |field| render_wikitext(field)),
        "only" | "onlybe" | "onlyje" | "exclusive" => fields
            .iter()
            .skip(1)
            .find_map(|field| field.trim().strip_prefix("for=").map(render_wikitext))
            .unwrap_or_default(),
        "el" if fields
            .get(1)
            .is_some_and(|value| matches!(value.trim(), "edu" | "education")) =>
        {
            "教育版".to_string()
        }
        "in exp" => fields
            .iter()
            .skip(1)
            .find_map(|field| field.trim().strip_prefix("for=").map(render_wikitext))
            .unwrap_or_default(),
        "experiment" => positional_code_fields(body)
            .first()
            .map_or_else(String::new, |value| {
                format!("`{}`", render_wikitext(value.trim()))
            }),
        "in" | "el" | "fn" | "bug" => String::new(),
        "range" => fields
            .iter()
            .skip(1)
            .filter(|field| !field.trim().contains('='))
            .map(|field| render_wikitext(field))
            .collect::<Vec<_>>()
            .join("–"),
        "cmd" | "tr" => fields
            .iter()
            .skip(1)
            .find(|field| !field.trim().contains('='))
            .map_or_else(String::new, |field| render_wikitext(field)),
        "nbt" => fields
            .get(2)
            .map_or_else(String::new, |field| render_wikitext(field)),
        "filepath" => positional_code_fields(body)
            .iter()
            .map(|field| render_wikitext(field))
            .collect::<Vec<_>>()
            .join("/"),
        "slink" => fields
            .iter()
            .skip(1)
            .filter(|field| !is_named_template_field(field))
            .next_back()
            .map_or_else(String::new, |field| render_wikitext(field)),
        "argument" => fields
            .iter()
            .skip(1)
            .find(|field| !field.trim().contains('='))
            .map_or_else(String::new, |field| {
                format!("<{}>", render_wikitext(field).trim_matches(['<', '>']))
            }),
        "arg desc" | "info needed" | "see also" | "lcfirst" | "about" | "redirect"
        | "infobox command" => String::new(),
        "!" => "|".into(),
        _ => String::new(),
    }
}

fn edition_display_name(edition: &str) -> &'static str {
    match edition.to_ascii_lowercase().as_str() {
        "be" | "bedrock" => "基岩版",
        "je" | "java" => "Java版",
        "edu" | "education" => "教育版",
        _ => "",
    }
}

fn strip_html_tags(input: &str) -> String {
    static TAG: OnceLock<Regex> = OnceLock::new();
    let regex =
        TAG.get_or_init(|| Regex::new(r"(?is)</?([a-z][a-z0-9]*)(?:\s+[^<>]*)?/?>").unwrap());
    regex
        .replace_all(input, |captures: &regex::Captures<'_>| {
            let tag = captures.get(1).map_or("", |value| value.as_str());
            if matches!(
                tag.to_ascii_lowercase().as_str(),
                "code"
                    | "samp"
                    | "kbd"
                    | "nowiki"
                    | "tt"
                    | "math"
                    | "small"
                    | "span"
                    | "div"
                    | "br"
                    | "ref"
                    | "sub"
                    | "sup"
                    | "b"
                    | "strong"
                    | "i"
                    | "em"
                    | "u"
                    | "s"
                    | "hr"
                    | "ul"
                    | "ol"
                    | "li"
                    | "table"
                    | "tr"
                    | "td"
                    | "th"
                    | "blockquote"
            ) {
                String::new()
            } else {
                captures.get(0).unwrap().as_str().to_string()
            }
        })
        .into_owned()
}

fn normalize_space(input: &str) -> String {
    input.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn deduplicate(values: &mut Vec<String>) {
    let mut unique = Vec::new();
    for value in values.drain(..) {
        if !unique.contains(&value) {
            unique.push(value);
        }
    }
    *values = unique;
}

#[cfg(test)]
mod tests {
    #[test]
    fn draft_status_is_preserved_without_hiding_the_command_purpose() {
        let source = "{{upcoming in draft}}\n清除物品。\n==参数==\n";
        let doc = super::parse_document("demo", "wiki", source, 12).unwrap();
        assert_eq!(doc.intro, "清除物品。");
        assert_eq!(doc.page_metadata[0].template, "upcoming in draft");
        let inline = super::parse_document(
            "demo",
            "wiki",
            "用途{{exclusive|bedrock|for=补充原句}}。\n==参数==\n",
            12,
        )
        .unwrap();
        assert_eq!(inline.intro, "用途补充原句。");
        assert!(inline.page_metadata.is_empty());
    }
    #[test]
    fn availability_banners_are_metadata_and_tutorials_are_navigation() {
        let source = "{{exclusive|bedrock|education}}{{education feature}}\n{{relevant tutorial|教程}}\n调整玩家的能力。\n==参数==\n";
        let doc = super::parse_document("demo", "wiki", source, 12).unwrap();
        assert_eq!(doc.intro, "调整玩家的能力。");
        assert_eq!(doc.page_metadata.len(), 2);
        assert_eq!(doc.page_metadata[0].template, "exclusive");
        assert_eq!(doc.page_metadata[0].arguments, vec!["bedrock", "education"]);
        assert_eq!(doc.page_metadata[1].template, "education feature");
        assert!(doc
            .warnings
            .iter()
            .all(|warning| !warning.contains("尚未渲染")));
    }
    #[test]
    fn unresolved_feature_template_cannot_produce_a_complete_intro() {
        let source =
            "普通说明。\n{{in exp|exg=feature|for=需启用“{{unknown_feature_label|feature}}”。}}\n==参数==\n";
        let doc = super::parse_document("demo", "wiki", source, 12).unwrap();
        assert_eq!(doc.intro, "普通说明。");
        assert_eq!(doc.experimental_intros["feature"], "");
        assert!(doc
            .warnings
            .iter()
            .any(|warning| warning.contains("unknown_feature_label")));
    }
    #[test]
    fn experiment_id_is_preserved_without_inventing_a_localized_name() {
        let source = "普通说明。\n{{in exp|exg=feature|for=在{{el|be}}中需要启用“{{experiment|feature|exg=1}}”。}}\n==参数==\n";
        let doc = super::parse_document("demo", "wiki", source, 12).unwrap();
        assert_eq!(doc.intro, "普通说明。");
        assert_eq!(
            doc.experimental_intros["feature"],
            "普通说明。 在基岩版中需要启用“`feature`”。"
        );
        assert!(doc
            .warnings
            .iter()
            .any(|warning| warning.contains("中文显示名未核实")));
    }
    #[test]
    fn java_only_outdated_notice_does_not_block_bedrock_intro() {
        let source =
            "{{outdated|edition=java|在Java版中已被另一命令替代。}}\n基岩版命令用途。\n==参数==\n";
        let doc = super::parse_document("demo", "wiki", source, 12).unwrap();
        assert_eq!(doc.intro, "基岩版命令用途。");
        assert!(doc
            .warnings
            .iter()
            .all(|warning| !warning.contains("outdated")));
        let unscoped = super::parse_document(
            "demo",
            "wiki",
            "{{outdated|行为已改变。}}\n命令用途。\n==参数==\n",
            12,
        )
        .unwrap();
        assert!(unscoped.intro.is_empty());
        assert!(unscoped
            .warnings
            .iter()
            .any(|warning| warning.contains("outdated")));
    }
    #[test]
    fn intro_edition_link_is_a_visible_noun_and_bug_is_reference_metadata() {
        assert_eq!(
            super::render_intro("在{{el|be}}中可用。"),
            "在基岩版中可用。"
        );
        let mut warnings = Vec::new();
        super::warn_unhandled_templates("{{bug|MC-12345}}", "demo", 1, &mut warnings);
        assert!(warnings.is_empty());
        assert_eq!(super::render_wikitext("说明{{bug|MC-12345}}。"), "说明。");
    }
    #[test]
    fn flat_syntax_alternatives_share_their_common_behavior_note() {
        let source = "示例。\n==语法==\n: <code>/demo of <clock: string> add <amount: int></code>\n: <code>/demo add <amount: int></code>\n:: 增加世界时钟的时间。\n: <code>/demo setdisplay <list|sidebar> [objective: string]</code>\n: <code>/demo setdisplay belowname [objective: string]</code>\n:: 在指定位置显示分数。\n==参数==\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(
            document.syntax_descriptions[0].description,
            "增加世界时钟的时间。"
        );
        assert_eq!(
            document.syntax_descriptions[1].description,
            "增加世界时钟的时间。"
        );
        assert_eq!(
            document.syntax_descriptions[2].description,
            "在指定位置显示分数。"
        );
        assert_eq!(
            document.syntax_descriptions[3].description,
            "在指定位置显示分数。"
        );
    }
    #[test]
    fn usage_passages_keep_the_parameter_behavior_and_example_source_lines() {
        let source = "示例。\n==语法==\n: <code>/demo <player: target></code>\n==参数==\n==用法==\n若<code>player: target</code>指定多个目标，每个目标都会收到物品。\n==示例==\n* 清除所有玩家的效果：\n: <code>/demo @a clear</code>\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(document.passages[0].line, 6);
        assert_eq!(document.passages[0].section, "用法");
        assert_eq!(
            document.passages[0].text,
            "若player: target指定多个目标，每个目标都会收到物品。"
        );
        assert_eq!(document.passages[2].text, "/demo @a clear");
    }
    #[test]
    fn historical_prose_is_an_explicit_passage_not_current_command_intro() {
        let source = "当前命令说明。\n==语法==\n: <code>/demo current</code>\n:: 当前操作。\n==历史==\n加入了命令<code>/demo old</code>，用于导出统计文件。\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(document.intro, "当前命令说明。");
        assert_eq!(document.syntax_descriptions.len(), 1);
        assert_eq!(document.syntax_descriptions[0].description, "当前操作。");
        assert_eq!(document.passages.len(), 1);
        assert_eq!(document.passages[0].section, "历史");
        assert_eq!(document.passages[0].line, 6);
        assert_eq!(
            document.passages[0].text,
            "加入了命令/demo old，用于导出统计文件。"
        );
    }
    #[test]
    fn different_adjacent_actions_do_not_share_the_last_actions_note() {
        let source = "示例。\n==语法==\n: <code>/demo ...</code>\n:* <code>... clear</code>\n:* <code>... detach</code>\n:: 取消固定。\n:* <code>... fade ...</code>\n:** <code>... color <red: int></code>\n:* <code>... fov_clear</code>\n:: 控制视场角。\n==参数==\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        for action in ["... clear", "... fade ...", "... color <red: int>"] {
            assert!(document
                .syntax_descriptions
                .iter()
                .find(|s| s.syntax == action)
                .unwrap()
                .description
                .is_empty());
        }
        assert_eq!(
            document
                .syntax_descriptions
                .iter()
                .find(|s| s.syntax == "... detach")
                .unwrap()
                .description,
            "取消固定。"
        );
    }

    #[test]
    fn edition_alternative_ranges_and_option_suffixes_remain_self_contained() {
        let text = "必须在{{only|java|for=1至100}}或{{only|bedrock|for=0至200}}之间。";
        let fragments = super::render_edition_fragments(text);
        assert_eq!(fragments[1].1, "必须在0至200之间。");
        assert_eq!(fragments[0].1, "必须在1至100之间。");
        let (_, option) = super::parse_option_line_for_edition(
            ":* <code>true</code> — 隐藏粒子。{{only|Java|for=也隐藏图标。}}",
            1,
            Some("be"),
        )
        .unwrap();
        assert_eq!(option.description, "隐藏粒子。");
    }
    #[test]
    fn intro_maintenance_notice_is_metadata_and_brief_keeps_original_period() {
        let source = "{{Missing information|缺少版别信息（{{el|be}}）}}\n{{Infobox command|name=demo}}\n示例命令。\n==语法==\n: <code>/demo</code>\n==参数==\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(document.intro, "示例命令。");
        let (brief, description) = super::take_explicit_brief("指定目标。 brief：目标");
        assert_eq!(brief.as_deref(), Some("目标"));
        assert_eq!(description, "指定目标。");
    }
    #[test]
    fn boolean_enum_lead_in_does_not_leave_an_orphan_condition() {
        let source = "示例。\n==语法==\n: <code>/demo [flag: bool]</code>\n==参数==\n{{argument|flag|bool|be=1}}\n: 指定是否检查方块。\n: 如果指定，则必须为以下其中之一：\n:* <code>true</code> — 检查方块。\n:* <code>false</code> — 不检查方块。\n: 如果未指定，则不检查方块。\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        let parameter = &document.parameters[0];
        assert_eq!(parameter.description, "指定是否检查方块。");
        assert!(parameter
            .constraints
            .iter()
            .any(|text| text.contains("如果未指定")));
        assert_eq!(parameter.options.len(), 2);
    }
    #[test]
    fn only_for_templates_keep_inline_edition_scope_and_file_names() {
        let fragments = super::render_edition_fragments(
            "指定结构{{only|for=或Java版标签|JE}}。使用{{filename|[[sounds.json]]}}。",
        );
        assert_eq!(
            fragments[1],
            (
                Some("be".to_string()),
                "指定结构。使用sounds.json。".to_string()
            )
        );
        assert_eq!(
            fragments[0],
            (
                Some("je".to_string()),
                "指定结构或Java版标签。使用sounds.json。".to_string()
            )
        );
    }
    #[test]
    fn abbreviation_clause_preserves_documented_option_aliases() {
        let (_, option) = super::parse_option_line(
            ":* <code>survival</code>（{{IN|be}}可简写为{{cd|s}}或{{cd|0}}）代表[[生存模式]]",
            9,
        )
        .unwrap();
        assert_eq!(option.values, ["survival", "s", "0"]);
        assert_eq!(option.description, "（可简写为s或0）代表生存模式");
    }
    #[test]
    fn command_context_bullets_are_parameter_purposes_not_enum_ids() {
        let source = "示例。\n==语法==\n: <code>/demo open <player: target></code>\n==参数==\n{{argument|be=1|player: target|CommandSelector<Player>}}\n: 指定目标玩家。\n:* 在<code>open</code>中为必选参数，用于打开界面；\n:* 在<code>change</code>中为可选参数，用于改变界面。\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        let change = document
            .parameters
            .iter()
            .find(|p| p.context == "change")
            .unwrap();
        assert_eq!(change.description, "在change中为可选参数，用于改变界面。");
        assert!(change.options.is_empty());
        assert_eq!(change.paragraphs[0].line, 8);
        let common = document
            .parameters
            .iter()
            .find(|p| p.context.is_empty())
            .unwrap();
        assert_eq!(common.description, "指定目标玩家。");
    }
    #[test]
    fn parameter_tables_preserve_shared_rowspan_constraints() {
        let lines = [
            "! 槽位",
            "! 槽位编号",
            "! 限制",
            "|-",
            "| {{cd|slot.main ''槽位编号''}}（主手）",
            "| rowspan=\"2\" style=\"text-align: center;\" | 任意值",
            "| rowspan=\"2\" | 仅[[玩家]]",
            "|-",
            "| {{cd|slot.off ''槽位编号''}}",
        ];
        let options = super::parse_parameter_table(&lines, 10).unwrap();
        assert_eq!(options.len(), 2);
        assert_eq!(options[0].values, ["slot.main"]);
        assert_eq!(options[1].values, ["slot.off"]);
        assert_eq!(options[0].description, "主手");
        assert!(options[1].description.is_empty());
        assert_eq!(options[1].constraints, ["槽位编号：任意值", "限制：仅玩家"]);
        assert_eq!(options[0].constraints, options[1].constraints);
        assert!(
            super::parse_parameter_table(&["! ID", "! 含义", "|-", "| colspan=2 | 无"], 1)
                .is_none()
        );
    }
    #[test]
    fn experimental_intro_preserves_feature_scope() {
        let source = "显示[[结构]]、[[生物群系]]或{{in exp|exg=poi|for=[[兴趣点]]{{only|JE}}}}的坐标。\n==语法==\n: <code>/demo</code>\n==参数==\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(document.intro, "显示结构或生物群系的坐标。");
        assert_eq!(
            document.experimental_intros["poi"],
            "显示结构、生物群系或兴趣点的坐标。"
        );
    }
    #[test]
    fn starred_child_syntax_owns_its_note_and_skips_display_tables() {
        let source = "示例。\n==语法==\n: <code>/demo <players: target> ...</code>\n:* <code>... attach <entity: target></code>\n:: 固定到实体。\n:* <code>... clear</code>\n:: 清除设置。\n::; 以多种方式展示语法\n:::{| class=collapsible\n| <code>/demo fake</code>\n|}\n==参数==\n";
        let document = parse_document("demo", "test", source, 12).unwrap();
        assert_eq!(document.syntax_descriptions.len(), 3);
        assert!(document.syntax_descriptions[0].description.is_empty());
        assert_eq!(document.syntax_descriptions[1].description, "固定到实体。");
        assert_eq!(document.syntax_descriptions[2].description, "清除设置。");
        assert!(document.syntax_descriptions[2].context.contains("/demo"));
    }

    #[test]
    fn option_label_before_code_is_a_source_description() {
        let (_, option) =
            super::parse_option_line(":* 林地：<code>example:woodland</code>", 9).unwrap();
        assert_eq!(option.values, ["example:woodland"]);
        assert_eq!(option.description, "林地");
        let (_, option) = super::parse_option_line(":* <code>example:empty</code>", 10).unwrap();
        assert!(option.description.is_empty());
    }
    use super::*;

    fn document(text: &str) -> WikiCommandDocument {
        parse_document("example", "https://example.invalid/wiki", text, 12).unwrap()
    }

    #[test]
    fn code_headers_support_unindented_and_shared_coordinate_names() {
        let result = document(
            "示例命令。\n==参数==\n<code>position: x y z</code>（仅block模式）\n: 指定位置。\n: <code>begin: x y z</code>与<code>end: x y z</code>\n:: 指定区域。\n:: brief：区域坐标\n",
        );
        assert_eq!(result.parameters.len(), 2);
        assert!(result.parameters[0].names.contains(&"position".into()));
        assert!(result.parameters[0].context.contains("仅block模式"));
        let region = &result.parameters[1];
        assert!(region.names.contains(&"begin".into()));
        assert!(region.names.contains(&"end".into()));
        assert_eq!(region.description, "指定区域。");
        assert_eq!(region.brief, "区域坐标");
        assert!(region.brief_explicit);
    }

    #[test]
    fn inline_edition_notes_preserve_common_text_without_mixing_rules() {
        let result = document(
            "示例命令。\n==参数==\n{{argument|operation: Operator|enum}}\n: 指定操作。{{in|je}}命令失败。{{in|be}}命令成功。\n",
        );
        let bedrock = result
            .parameters
            .iter()
            .find(|p| p.edition.as_deref() == Some("be"))
            .unwrap();
        let java = result
            .parameters
            .iter()
            .find(|p| p.edition.as_deref() == Some("je"))
            .unwrap();
        assert_eq!(bedrock.description, "指定操作。 命令成功。");
        assert_eq!(java.description, "指定操作。 命令失败。");
    }

    #[test]
    fn omitted_root_syntax_details_keep_edition_specific_notes() {
        let result = document(
            "示例命令。\n==语法==\n===详细信息===\n: <code>... reset</code>\n:: 恢复默认时间。{{in|je}}清除标题。\n",
        );
        let bedrock = result
            .syntax_descriptions
            .iter()
            .find(|s| s.edition.as_deref() == Some("be"))
            .unwrap();
        let java = result
            .syntax_descriptions
            .iter()
            .find(|s| s.edition.as_deref() == Some("je"))
            .unwrap();
        assert_eq!(bedrock.description, "恢复默认时间。");
        assert_eq!(java.description, "恢复默认时间。 清除标题。");
        assert_eq!(bedrock.context, "详细信息");
    }

    #[test]
    fn code_template_retains_each_positional_value_and_operator() {
        assert_eq!(
            code_values("{{cd|d=pretty|first|second|=}}"),
            ["first", "second", "="]
        );
    }

    #[test]
    fn nested_nowiki_code_keeps_complete_syntax() {
        let result = document(
            "示例命令。\n==语法==\n: <code><nowiki>example add <time: int>[D/S/T]</nowiki></code>\n:: 延时执行。\n",
        );
        assert_eq!(result.syntax_descriptions.len(), 1);
        assert_eq!(
            result.syntax_descriptions[0].syntax,
            "example add <time: int>[D/S/T]"
        );
        assert_eq!(result.syntax_descriptions[0].description, "延时执行。");
    }

    #[test]
    fn code_template_operator_bullets_keep_their_explanation() {
        let (_, option) = parse_option_line(":* {{cd|><}}交换：交换两个值。", 1).unwrap();
        assert_eq!(option.values, ["><"]);
        assert_eq!(option.description, "交换：交换两个值。");
    }

    #[test]
    fn default_bullets_do_not_create_duplicate_enum_options() {
        let result = document(
            "示例命令。\n==参数==\n{{argument|mode: Mode|enum}}\n: 指定模式。必须为以下其中之一：\n:* <code>one</code> — 第一种模式。\n:* 如果未指定，默认为<code>one</code>。\n",
        );
        let parameter = &result.parameters[0];
        assert_eq!(parameter.options.len(), 1);
        assert_eq!(parameter.options[0].description, "第一种模式。");
        assert_eq!(parameter.constraints, ["如果未指定，默认为one。"]);
        assert_eq!(parameter.description, "指定模式。");
    }

    #[test]
    fn multiline_intro_metadata_does_not_leak_template_closers() {
        let result = document("{{redirect|别名\n|详情}}\n示例命令。\n==语法==\n");
        assert_eq!(result.intro, "示例命令。");
    }

    #[test]
    fn intro_hatnote_nested_link_templates_do_not_leak_closing_braces() {
        let result = document("{{Redirect|别名|参见[[页面]]|[[目标|{{tr|文本|另一个文本}}]]}}\n示例命令。\n==语法==\n");
        assert_eq!(result.intro, "示例命令。");
    }
}
