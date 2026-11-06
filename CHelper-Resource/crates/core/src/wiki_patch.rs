//! Exact, guarded edits to cached command Wiki source pages.

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::path::Path;

/// The result of applying a command-specific Wiki source patch.
#[derive(Debug, Clone, Serialize)]
pub struct PatchedWikiPage {
    pub text: String,
    pub applied: bool,
    /// Number of matched occurrences replaced across all patch rules.
    pub replacements: usize,
    pub patch_file: Option<String>,
    /// Keep editorial intent visible, including explicitly authored supplements.
    pub patch_reason: Option<String>,
    /// Reasons in the same order as the exact replacement rules in the patch file.
    pub replacement_reasons: Vec<Option<String>>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct WikiPatch {
    version: u32,
    reason: String,
    replacements: Vec<Replacement>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct Replacement {
    old: String,
    new: String,
    #[serde(default = "default_expected_matches")]
    expected_matches: usize,
    #[serde(default)]
    reason: Option<String>,
}

fn default_expected_matches() -> usize {
    1
}

/// Apply exact, ordered edits to a Wiki page without changing its cached source file.
///
/// Patch text and the in-memory page are normalized to LF before matching. With no
/// patch file, the original page is returned byte-for-byte as a Rust string.
pub fn apply_page_patch(
    command: &str,
    original: &str,
    patches_dir: &Path,
) -> Result<PatchedWikiPage> {
    apply_page_patch_for_title(command, None, original, patches_dir)
}

/// An alias can reuse its resolved command page's patch. An explicit alias patch
/// takes precedence; exact matching still checks the actual cached page content.
pub fn apply_page_patch_for_title(
    command: &str,
    resolved_title: Option<&str>,
    original: &str,
    patches_dir: &Path,
) -> Result<PatchedWikiPage> {
    let mut selected = None;
    for filename in patch_candidates(command, resolved_title)? {
        let path = patches_dir.join(&filename);
        match std::fs::read_to_string(&path) {
            Ok(text) => {
                selected = Some((filename, text));
                break;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => {
                return Err(error).with_context(|| {
                    format!(
                        "failed to read Wiki patch for command '{command}': {}",
                        path.display()
                    )
                });
            }
        }
    }
    let Some((filename, patch_text)) = selected else {
        return Ok(PatchedWikiPage {
            text: original.to_owned(),
            applied: false,
            replacements: 0,
            patch_file: None,
            patch_reason: None,
            replacement_reasons: Vec::new(),
        });
    };
    let patch_path = patches_dir.join(&filename);
    let patch: WikiPatch = serde_json::from_str(&patch_text).with_context(|| {
        format!(
            "invalid Wiki patch for command '{command}' in {}",
            patch_path.display()
        )
    })?;
    if patch.version != 1 {
        bail!(
            "unsupported Wiki patch version {} for command '{command}' in {} (supported: 1)",
            patch.version,
            patch_path.display()
        );
    }
    if patch.reason.trim().is_empty() {
        bail!(
            "Wiki patch reason is empty for command '{command}' in {}",
            patch_path.display()
        );
    }

    let mut text = normalize_lf(original);
    let mut replacement_count = 0;
    for (index, replacement) in patch.replacements.iter().enumerate() {
        let old = normalize_lf(&replacement.old);
        let new = normalize_lf(&replacement.new);
        if old.is_empty() {
            bail!(
                "empty old text for command '{command}', patch {}, replacement {}",
                patch_path.display(),
                index + 1
            );
        }
        if replacement.expected_matches == 0 {
            bail!(
                "expected_matches must be greater than zero for command '{command}', patch {}, replacement {}",
                patch_path.display(),
                index + 1
            );
        }
        let actual_matches = text.match_indices(&old).count();
        if actual_matches != replacement.expected_matches {
            let reason = replacement
                .reason
                .as_deref()
                .filter(|value| !value.trim().is_empty())
                .unwrap_or("no replacement-specific reason provided");
            bail!(
                "Wiki patch drift for command '{command}', patch {}, replacement {}: expected {} exact match(es), found {} ({reason})",
                patch_path.display(),
                index + 1,
                replacement.expected_matches,
                actual_matches
            );
        }
        text = text.replace(&old, &new);
        replacement_count += actual_matches;
    }

    Ok(PatchedWikiPage {
        text,
        applied: !patch.replacements.is_empty(),
        replacements: replacement_count,
        patch_file: Some(filename),
        patch_reason: Some(patch.reason),
        replacement_reasons: patch
            .replacements
            .into_iter()
            .map(|replacement| replacement.reason)
            .collect(),
    })
}

fn normalize_lf(text: &str) -> String {
    text.replace("\r\n", "\n").replace('\r', "\n")
}

fn patch_candidates(command: &str, resolved_title: Option<&str>) -> Result<Vec<String>> {
    let valid_name = |name: &str| {
        !name.is_empty()
            && name
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-')
    };
    if !valid_name(command) {
        bail!(
            "Wiki patch command must contain only ASCII letters, digits, '_' or '-': {command:?}"
        );
    }
    let mut candidates = vec![format!("{command}.json")];
    if let Some(canonical) = resolved_title.and_then(|title| title.strip_prefix("命令/")) {
        if canonical != command && valid_name(canonical) {
            candidates.push(format!("{canonical}.json"));
        }
    }
    Ok(candidates)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn patch_reasons_preserve_authored_supplement_attribution() {
        let directory = std::env::temp_dir().join(format!(
            "chelper-patch-attribution-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir(&directory).unwrap();
        let patch_path = directory.join("demo.json");
        std::fs::write(
            &patch_path,
            r#"{"version":1,"reason":"人工补充：由维护者编写用途说明","replacements":[{"old":"参数","new":"参数说明","reason":"人工补充：补足用途"}]}"#,
        )
        .unwrap();
        let patched = apply_page_patch("demo", "参数", &directory).unwrap();
        assert_eq!(patched.text, "参数说明");
        assert_eq!(
            patched.patch_reason.as_deref(),
            Some("人工补充：由维护者编写用途说明")
        );
        assert_eq!(
            patched.replacement_reasons,
            vec![Some("人工补充：补足用途".to_string())]
        );
        let original = apply_page_patch("other", "参数", &directory).unwrap();
        assert!(original.patch_reason.is_none());
        assert!(original.replacement_reasons.is_empty());
        std::fs::remove_file(patch_path).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }

    #[test]
    fn canonical_patch_fallback_preserves_alias_priority_and_safe_paths() {
        assert_eq!(
            patch_candidates("alias", Some("命令/main")).unwrap(),
            vec!["alias.json", "main.json"]
        );
        assert_eq!(
            patch_candidates("main", Some("命令/main")).unwrap(),
            vec!["main.json"]
        );
        assert_eq!(
            patch_candidates("alias", Some("命令/../outside")).unwrap(),
            vec!["alias.json"]
        );
        assert_eq!(
            patch_candidates("alias", Some("普通页面/main")).unwrap(),
            vec!["alias.json"]
        );
        assert!(patch_candidates("../outside", None).is_err());
    }
}
