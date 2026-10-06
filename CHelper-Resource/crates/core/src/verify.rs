//! 输出验证：与 caidlist `new_chelper` 生成的参考输出对比格式与内容。

use anyhow::{Context, Result};
use serde_json::Value;
use std::path::{Path, PathBuf};

#[derive(Debug, serde::Serialize)]
pub struct FileDiff {
    pub file: String,
    /// identical：字节级一致；format_only：格式一致、内容有差异；missing。
    pub status: String,
    /// 内容差异路径数。
    pub content_diffs: usize,
    /// 首部差异示例。
    pub samples: Vec<String>,
}

#[derive(Debug, serde::Serialize)]
pub struct VerifyReport {
    pub reference: String,
    pub generated: String,
    pub files: Vec<FileDiff>,
    pub identical: usize,
    pub format_only: usize,
    pub missing: usize,
}

/// caidlist 生成、本工具也生成的 27 个公共文件（每个分支）。
pub const COMMON_FILES: &[&str] = &[
    "animation.json",
    "animationController.json",
    "biome.json",
    "block.json",
    "cameraEasing.json",
    "cameraPreset.json",
    "controlScheme.json",
    "damageCause.json",
    "effect.json",
    "enchantType.json",
    "entity.json",
    "entityEvent.json",
    "entityFamily.json",
    "entitySlot.json",
    "feature.json",
    "featureRule.json",
    "fog.json",
    "gameRuleBoolean.json",
    "gameRuleInteger.json",
    "hudElement.json",
    "item.json",
    "lootTable.json",
    "music.json",
    "recipe.json",
    "sound.json",
    "structure.json",
    // 注意：gameRule 不单独成文件（拆分为 Integer/Boolean），entity 特殊类型
];

/// 对指定分支（如 release/vanilla）执行对比。
pub fn verify_branch(generated_root: &Path, reference_root: &Path, edition: &str, branch: &str) -> Result<VerifyReport> {
    let gen_dir = generated_root.join(edition).join(branch).join("id");
    let ref_dir = reference_root.join(edition).join(branch).join("id");
    let mut report = VerifyReport {
        reference: ref_dir.display().to_string(),
        generated: gen_dir.display().to_string(),
        files: Vec::new(),
        identical: 0,
        format_only: 0,
        missing: 0,
    };
    for file in COMMON_FILES {
        let gen_path = gen_dir.join(file);
        let ref_path = ref_dir.join(file);
        if !ref_path.exists() {
            report.files.push(FileDiff {
                file: file.to_string(),
                status: "reference_missing".into(),
                content_diffs: 0,
                samples: vec![],
            });
            continue;
        }
        if !gen_path.exists() {
            report.missing += 1;
            report.files.push(FileDiff {
                file: file.to_string(),
                status: "missing".into(),
                content_diffs: 0,
                samples: vec![],
            });
            continue;
        }
        let gen_bytes = std::fs::read(&gen_path)?;
        let ref_bytes = std::fs::read(&ref_path)?;
        if gen_bytes == ref_bytes {
            report.identical += 1;
            report.files.push(FileDiff {
                file: file.to_string(),
                status: "identical".into(),
                content_diffs: 0,
                samples: vec![],
            });
            continue;
        }
        // 格式检查（生成端）
        check_format(&gen_path, &gen_bytes)?;
        // 结构化对比（保序）
        let gen_json: Value = serde_json::from_slice(&gen_bytes)?;
        let ref_json: Value = serde_json::from_slice(&ref_bytes)?;
        let mut diffs = 0usize;
        let mut samples = Vec::new();
        diff_json(&ref_json, &gen_json, "$", &mut diffs, &mut samples);
        report.format_only += 1;
        report.files.push(FileDiff {
            file: file.to_string(),
            status: "content_diff".into(),
            content_diffs: diffs,
            samples,
        });
    }
    Ok(report)
}

/// 格式契约检查：UTF-8、CRLF 行尾、4 空格缩进、末尾无换行、合法 JSON。
pub fn check_format(path: &Path, bytes: &[u8]) -> Result<()> {
    let text = std::str::from_utf8(bytes)
        .with_context(|| format!("{} 不是合法 UTF-8", path.display()))?;
    anyhow::ensure!(
        !bytes.ends_with(b"\n") && !bytes.ends_with(b"\r"),
        "{} 末尾不应有换行符",
        path.display()
    );
    anyhow::ensure!(
        !text.contains('\n') || text.contains("\r\n"),
        "{} 应使用 CRLF 行尾",
        path.display()
    );
    anyhow::ensure!(
        !text.replace("\r\n", "").contains(|c| c == '\n'),
        "{} 存在孤立 LF（应统一 CRLF）",
        path.display()
    );
    for (i, line) in text.split("\r\n").enumerate() {
        let indent = line.len() - line.trim_start().len();
        anyhow::ensure!(
            indent % 4 == 0,
            "{} 第 {} 行缩进不是 4 的倍数",
            path.display(),
            i + 1
        );
    }
    serde_json::from_str::<Value>(text).with_context(|| format!("{} 不是合法 JSON", path.display()))?;
    Ok(())
}

/// 保序 JSON 结构对比：对象键序不同视为差异。
fn diff_json(a: &Value, b: &Value, path: &str, diffs: &mut usize, samples: &mut Vec<String>) {
    let record = |msg: String, diffs: &mut usize, samples: &mut Vec<String>| {
        *diffs += 1;
        if samples.len() < 8 {
            samples.push(msg);
        }
    };
    match (a, b) {
        (Value::Object(ao), Value::Object(bo)) => {
            let akeys: Vec<&String> = ao.keys().collect();
            let bkeys: Vec<&String> = bo.keys().collect();
            if akeys != bkeys {
                record(
                    format!("{path}: 键序或键集不同（参考 {} 个 vs 生成 {} 个）", akeys.len(), bkeys.len()),
                    diffs,
                    samples,
                );
                return;
            }
            for (k, av) in ao {
                let bv = bo.get(k).unwrap();
                diff_json(av, bv, &format!("{path}.{k}"), diffs, samples);
            }
        }
        (Value::Array(ai), Value::Array(bi)) => {
            if ai.len() != bi.len() {
                record(format!("{path}: 数组长度不同（{} vs {}）", ai.len(), bi.len()), diffs, samples);
                return;
            }
            for (i, (av, bv)) in ai.iter().zip(bi.iter()).enumerate() {
                diff_json(av, bv, &format!("{path}[{i}]"), diffs, samples);
            }
        }
        _ => {
            if a != b {
                let a_str = summarize(a);
                let b_str = summarize(b);
                record(format!("{path}: {a_str} != {b_str}"), diffs, samples);
            }
        }
    }
}

fn summarize(v: &Value) -> String {
    match v {
        Value::String(s) => {
            let mut s = s.clone();
            if s.chars().count() > 24 {
                s = s.chars().take(24).collect::<String>() + "…";
            }
            format!("\"{s}\"")
        }
        other => other.to_string(),
    }
}

/// 找参考输出根目录的默认候选。
pub fn default_reference_roots() -> Vec<PathBuf> {
    vec![
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(2)
            .map(|p| p.join("../caidlist/output/chelper"))
            .unwrap_or_default(),
        PathBuf::from("E:/project/CHelper/caidlist/output/chelper"),
    ]
}
