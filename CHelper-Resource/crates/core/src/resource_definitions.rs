//! Canonical static resource definitions from data, independent of generated snapshots.

use crate::parse_jsonc;
use anyhow::{bail, Context, Result};
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

const KINDS: [&str; 3] = ["grammer", "json", "repeat"];
const REQUIRED: [&str; 4] = [
    "grammer/target_selector.json",
    "json/components.json",
    "json/rawtext.json",
    "repeat/execute.json",
];

/// A validated, immutable view of one edition/branch's static definitions.
pub struct ResourceDefinitions {
    files: BTreeMap<PathBuf, String>,
}

impl ResourceDefinitions {
    /// Whole-file overrides are applied from common definitions, edition, then branch.
    /// Missing or malformed data is an error; generated resources are never a fallback.
    pub fn load(root: &Path, edition: &str, branch: &str) -> Result<Self> {
        let mut files = BTreeMap::new();
        for kind in KINDS {
            let source = root.join(kind);
            if !source.is_dir() {
                bail!("缺少静态资源数据源目录: {}", source.display());
            }
            let before = files.len();
            collect(&source, Path::new(kind), &mut files)?;
            if files.len() == before {
                bail!("静态资源数据源目录没有JSON定义: {}", source.display());
            }
        }
        for required in REQUIRED {
            if !files.contains_key(Path::new(required)) {
                bail!("缺少必需静态资源数据源: {}", root.join(required).display());
            }
        }
        if edition == "netease" && !root.join("netease/repeat/execute.json").is_file() {
            bail!(
                "缺少网易专用execute数据源: {}",
                root.join("netease/repeat/execute.json").display()
            );
        }
        for layer in [root.join(edition), root.join(edition).join(branch)] {
            for kind in KINDS {
                let source = layer.join(kind);
                if source.is_dir() {
                    collect(&source, Path::new(kind), &mut files)?;
                }
            }
        }
        Ok(Self { files })
    }

    /// Materialize either package output or its generated resources snapshot.
    pub fn write_to(&self, output: &Path) -> Result<usize> {
        for (relative, text) in &self.files {
            let destination = output.join(relative);
            std::fs::create_dir_all(destination.parent().unwrap())?;
            std::fs::write(&destination, text)
                .with_context(|| format!("写出静态资源失败: {}", destination.display()))?;
        }
        for kind in KINDS {
            remove_stale(&output.join(kind), Path::new(kind), &self.files)?;
        }
        Ok(self.files.len())
    }
}

fn collect(root: &Path, relative: &Path, files: &mut BTreeMap<PathBuf, String>) -> Result<()> {
    for entry in std::fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        let name = relative.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            collect(&path, &name, files)?;
        } else if entry.file_type()?.is_file()
            && path.extension().and_then(|extension| extension.to_str()) == Some("json")
        {
            let text = std::fs::read_to_string(&path)?;
            let value = parse_jsonc(&text)
                .with_context(|| format!("解析静态资源数据源失败: {}", path.display()))?;
            validate(&name, &value)
                .with_context(|| format!("静态资源数据源结构无效: {}", path.display()))?;
            files.insert(name, text);
        } else {
            bail!("静态资源数据源包含非JSON普通文件: {}", path.display());
        }
    }
    Ok(())
}

fn validate(relative: &Path, value: &serde_json::Value) -> Result<()> {
    let expected_id = relative.file_stem().and_then(|name| name.to_str());
    if value.get("id").and_then(|id| id.as_str()) != expected_id {
        bail!("id必须与定义文件名一致");
    }
    match relative
        .components()
        .next()
        .and_then(|part| part.as_os_str().to_str())
    {
        Some("grammer") => {
            if value.get("type").and_then(|kind| kind.as_str()) != Some("grammar") {
                bail!("grammar定义缺少有效type");
            }
            validate_graph(&value["content"])?;
        }
        Some("json") => validate_graph(value)?,
        Some("repeat") => {
            let branches = value["repeatNodes"]
                .as_array()
                .filter(|nodes| !nodes.is_empty())
                .context("repeat定义缺少非空repeatNodes")?;
            let ends = value["isEnd"].as_array().context("repeat定义缺少isEnd")?;
            if ends.len() != branches.len() || !ends.iter().all(|end| end.is_boolean()) {
                bail!("isEnd必须与repeatNodes逐项对应且为Boolean");
            }
            let breaks = value["breakNodes"]
                .as_array()
                .filter(|nodes| !nodes.is_empty())
                .context("repeat定义缺少非空breakNodes")?;
            validate_repeat_nodes(breaks)?;
            for branch in branches {
                validate_repeat_nodes(branch.as_array().context("repeat分支必须为节点数组")?)?;
            }
        }
        _ => bail!("不支持的静态资源目录"),
    }
    Ok(())
}

fn validate_repeat_nodes(nodes: &[serde_json::Value]) -> Result<()> {
    if nodes.is_empty()
        || !nodes.iter().all(|node| {
            node.get("type")
                .and_then(|kind| kind.as_str())
                .is_some_and(|kind| !kind.is_empty())
        })
    {
        bail!("repeat分支必须包含有效节点");
    }
    Ok(())
}

fn validate_graph(value: &serde_json::Value) -> Result<()> {
    let start = value["start"]
        .as_str()
        .filter(|name| !name.is_empty())
        .context("图定义缺少start")?;
    let nodes = value["node"]
        .as_array()
        .filter(|nodes| !nodes.is_empty())
        .context("图定义缺少非空node数组")?;
    let mut ids = BTreeSet::new();
    for node in nodes {
        let id = node["id"]
            .as_str()
            .filter(|name| !name.is_empty())
            .context("图节点缺少id")?;
        if !ids.insert(id) || !node["type"].as_str().is_some_and(|kind| !kind.is_empty()) {
            bail!("图节点id重复或缺少type");
        }
    }
    if !ids.contains(start) {
        bail!("start引用不存在的节点");
    }
    Ok(())
}

fn remove_stale(root: &Path, relative: &Path, files: &BTreeMap<PathBuf, String>) -> Result<()> {
    for entry in std::fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        let name = relative.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            remove_stale(&path, &name, files)?;
        } else if entry.file_type()?.is_file() {
            if !files.contains_key(&name) {
                std::fs::remove_file(&path)
                    .with_context(|| format!("清理过期静态资源快照失败: {}", path.display()))?;
            }
        } else {
            bail!("静态资源输出包含非普通文件: {}", path.display());
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!(
                "chelper-definitions-test-{}-{}",
                std::process::id(),
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_nanos()
            ));
            for (name, text) in [
                (
                    "grammer/target_selector.json",
                    include_str!("../../../data/resource_definitions/grammer/target_selector.json"),
                ),
                (
                    "json/components.json",
                    include_str!("../../../data/resource_definitions/json/components.json"),
                ),
                (
                    "json/rawtext.json",
                    include_str!("../../../data/resource_definitions/json/rawtext.json"),
                ),
                (
                    "repeat/execute.json",
                    include_str!("../../../data/resource_definitions/repeat/execute.json"),
                ),
                (
                    "netease/repeat/execute.json",
                    include_str!("../../../data/resource_definitions/netease/repeat/execute.json"),
                ),
            ] {
                let path = root.join("data").join(name);
                std::fs::create_dir_all(path.parent().unwrap()).unwrap();
                std::fs::write(path, text).unwrap();
            }
            Self(root)
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            assert!(self.0.starts_with(std::env::temp_dir()));
            assert!(self
                .0
                .file_name()
                .unwrap()
                .to_string_lossy()
                .starts_with("chelper-definitions-test-"));
            std::fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn stale_generated_snapshots_are_rebuilt_from_data() {
        let fixture = Fixture::new();
        let snapshots = fixture.0.join("resources");
        std::fs::create_dir_all(snapshots.join("repeat")).unwrap();
        std::fs::write(
            snapshots.join("repeat/execute.json"),
            r#"{"description":"stale"}"#,
        )
        .unwrap();
        std::fs::write(snapshots.join("repeat/obsolete.json"), "{}").unwrap();
        let definitions =
            ResourceDefinitions::load(&fixture.0.join("data"), "release", "vanilla").unwrap();
        definitions.write_to(&snapshots).unwrap();
        assert_eq!(
            std::fs::read(snapshots.join("repeat/execute.json")).unwrap(),
            std::fs::read(fixture.0.join("data/repeat/execute.json")).unwrap()
        );
        assert!(!snapshots.join("repeat/obsolete.json").exists());
    }

    #[test]
    fn edition_and_branch_overrides_do_not_remove_other_common_files() {
        let fixture = Fixture::new();
        let data = fixture.0.join("data");
        let mut repeat = parse_jsonc(
            &std::fs::read_to_string(data.join("netease/repeat/execute.json")).unwrap(),
        )
        .unwrap();
        repeat["breakNodes"][0]["description"] = "edition override".into();
        std::fs::write(
            data.join("netease/repeat/execute.json"),
            serde_json::to_vec(&repeat).unwrap(),
        )
        .unwrap();
        std::fs::create_dir_all(data.join("netease/experiment/json")).unwrap();
        let mut graph =
            parse_jsonc(&std::fs::read_to_string(data.join("json/rawtext.json")).unwrap()).unwrap();
        graph["node"][0]["description"] = "branch override".into();
        std::fs::write(
            data.join("netease/experiment/json/rawtext.json"),
            serde_json::to_vec(&graph).unwrap(),
        )
        .unwrap();
        let selected = ResourceDefinitions::load(&data, "netease", "experiment").unwrap();
        let output = fixture.0.join("output");
        assert_eq!(selected.write_to(&output).unwrap(), 4);
        assert_eq!(
            parse_jsonc(&std::fs::read_to_string(output.join("repeat/execute.json")).unwrap())
                .unwrap()["breakNodes"][0]["description"],
            "edition override"
        );
        assert_eq!(
            parse_jsonc(&std::fs::read_to_string(output.join("json/rawtext.json")).unwrap())
                .unwrap()["node"][0]["description"],
            "branch override"
        );
        assert_eq!(
            std::fs::read(output.join("grammer/target_selector.json")).unwrap(),
            std::fs::read(data.join("grammer/target_selector.json")).unwrap()
        );
        let common = ResourceDefinitions::load(&data, "release", "vanilla").unwrap();
        common.write_to(&output).unwrap();
        assert_eq!(
            std::fs::read(output.join("repeat/execute.json")).unwrap(),
            std::fs::read(data.join("repeat/execute.json")).unwrap()
        );
    }

    #[test]
    fn missing_data_cannot_fall_back_to_valid_generated_resources() {
        let fixture = Fixture::new();
        std::fs::create_dir_all(fixture.0.join("resources/json")).unwrap();
        std::fs::copy(
            fixture.0.join("data/json/rawtext.json"),
            fixture.0.join("resources/json/rawtext.json"),
        )
        .unwrap();
        std::fs::remove_file(fixture.0.join("data/json/rawtext.json")).unwrap();
        let error = ResourceDefinitions::load(&fixture.0.join("data"), "release", "vanilla")
            .err()
            .unwrap()
            .to_string();
        assert!(error.contains("rawtext.json"));
    }

    #[test]
    fn missing_netease_override_cannot_drop_its_dimensions() {
        let fixture = Fixture::new();
        std::fs::remove_file(fixture.0.join("data/netease/repeat/execute.json")).unwrap();
        assert!(ResourceDefinitions::load(&fixture.0.join("data"), "netease", "vanilla").is_err());
        assert!(ResourceDefinitions::load(&fixture.0.join("data"), "release", "vanilla").is_ok());
    }

    #[test]
    fn malformed_override_is_rejected_before_existing_output_is_changed() {
        let fixture = Fixture::new();
        let previous = fixture.0.join("output/repeat/execute.json");
        std::fs::create_dir_all(previous.parent().unwrap()).unwrap();
        std::fs::write(&previous, "previous valid package").unwrap();
        let broken = fixture.0.join("data/netease/repeat/execute.json");
        std::fs::create_dir_all(broken.parent().unwrap()).unwrap();
        let valid = std::fs::read(&broken).unwrap();
        for invalid in [
            "{",
            "{}",
            "[]",
            r#"{"id":"execute","repeatNodes":[],"breakNodes":[],"isEnd":[]}"#,
        ] {
            std::fs::write(&broken, invalid).unwrap();
            assert!(
                ResourceDefinitions::load(&fixture.0.join("data"), "netease", "vanilla").is_err()
            );
        }
        std::fs::write(&broken, valid).unwrap();
        let graph_path = fixture.0.join("data/json/rawtext.json");
        let mut graph = parse_jsonc(&std::fs::read_to_string(&graph_path).unwrap()).unwrap();
        graph["node"][0]["type"] = "".into();
        std::fs::write(graph_path, serde_json::to_vec(&graph).unwrap()).unwrap();
        let error = ResourceDefinitions::load(&fixture.0.join("data"), "netease", "vanilla")
            .err()
            .unwrap();
        assert!(format!("{error:#}").contains("rawtext.json"));
        assert_eq!(
            std::fs::read_to_string(previous).unwrap(),
            "previous valid package"
        );
    }
}
