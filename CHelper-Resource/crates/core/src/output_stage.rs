//! Build a complete resource directory before replacing the previous output.

use anyhow::{bail, Context, Result};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

pub(crate) struct OutputStage {
    target: PathBuf,
    stage: PathBuf,
    backup: PathBuf,
}

impl OutputStage {
    pub(crate) fn new(target: &Path) -> Result<Self> {
        let name = target
            .file_name()
            .and_then(|name| name.to_str())
            .context("资源输出目录必须有有效目录名")?;
        let parent = target.parent().context("资源输出目录没有父目录")?;
        std::fs::create_dir_all(parent)?;
        let parent = parent.canonicalize()?;
        let target = parent.join(name);
        Self::check_target(&target)?;
        let nonce = format!(
            "{}-{}",
            std::process::id(),
            SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos()
        );
        let stage = parent.join(format!(".{name}.staging-{nonce}"));
        let backup = parent.join(format!(".{name}.backup-{nonce}"));
        // Exclusive creation means Drop only ever removes the directory owned
        // by this run. Both temporary paths are siblings of the resolved target.
        std::fs::create_dir(&stage)?;
        Ok(Self {
            target,
            stage,
            backup,
        })
    }

    fn check_target(target: &Path) -> Result<()> {
        if let Ok(metadata) = target.symlink_metadata() {
            if metadata.file_type().is_symlink() || !metadata.is_dir() {
                bail!("资源输出目标必须是普通目录: {}", target.display());
            }
            // Only replace the generated pack tree, never an arbitrary project,
            // cache, or document directory accidentally selected in config.
            for entry in std::fs::read_dir(&target)? {
                let entry = entry?;
                if !entry.file_type()?.is_dir()
                    || !matches!(
                        entry.file_name().to_str(),
                        Some("release" | "beta" | "netease" | "old2new")
                    )
                {
                    bail!(
                        "输出目录包含非资源包内容，拒绝整目录替换: {}",
                        entry.path().display()
                    );
                }
            }
        }
        Ok(())
    }

    pub(crate) fn path(&self) -> &Path {
        &self.stage
    }
    pub(crate) fn target(&self) -> &Path {
        &self.target
    }

    pub(crate) fn preserve_unselected(&self, editions: &[String]) -> Result<()> {
        for edition in ["release", "beta", "netease"] {
            let source = self.target.join(edition);
            if source.is_dir() && !editions.iter().any(|selected| selected == edition) {
                copy_directory(&source, &self.stage.join(edition))?;
            }
        }
        Ok(())
    }

    pub(crate) fn publish(self) -> Result<()> {
        Self::check_target(&self.target)?;
        if self.backup.try_exists()? {
            bail!("输出备份目录已存在: {}", self.backup.display());
        }
        let had_previous = self.target.try_exists()?;
        if had_previous {
            std::fs::rename(&self.target, &self.backup)
                .with_context(|| format!("保留旧资源输出失败: {}", self.target.display()))?;
        }
        if let Err(error) = std::fs::rename(&self.stage, &self.target) {
            if had_previous {
                std::fs::rename(&self.backup, &self.target).with_context(|| {
                    format!(
                        "资源替换失败({error})，旧输出保留在{}但回滚失败",
                        self.backup.display()
                    )
                })?;
            }
            return Err(error).context("发布已验证资源输出失败，旧输出已恢复");
        }
        if had_previous {
            // This path is the old generated tree renamed above, never an
            // independently supplied recursive deletion target.
            if let Err(error) = std::fs::remove_dir_all(&self.backup) {
                tracing::warn!(
                    "新资源输出已完成，旧备份清理失败，保留于{}: {error}",
                    self.backup.display()
                );
            }
        }
        Ok(())
    }
}

fn copy_directory(source: &Path, target: &Path) -> Result<()> {
    std::fs::create_dir(target)?;
    for entry in std::fs::read_dir(source)? {
        let entry = entry?;
        let output = target.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            copy_directory(&entry.path(), &output)?;
        } else if entry.file_type()?.is_file() {
            std::fs::copy(entry.path(), output)?;
        } else {
            bail!("旧资源输出包含非普通文件或目录: {}", entry.path().display());
        }
    }
    Ok(())
}

impl Drop for OutputStage {
    fn drop(&mut self) {
        if self.stage.exists() {
            if let Err(error) = std::fs::remove_dir_all(&self.stage) {
                tracing::warn!(
                    "临时资源输出清理失败，保留于{}: {error}",
                    self.stage.display()
                );
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!(
                "chelper-output-test-{}-{}",
                std::process::id(),
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_nanos()
            ));
            std::fs::create_dir(&root).unwrap();
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
                .starts_with("chelper-output-test-"));
            std::fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn failed_generation_preserves_previous_output() {
        let fixture = Fixture::new();
        let target = fixture.0.join("pack");
        std::fs::create_dir_all(target.join("release")).unwrap();
        std::fs::write(target.join("release/previous"), "old").unwrap();
        let stage_path;
        {
            let stage = OutputStage::new(&target).unwrap();
            stage_path = stage.path().to_path_buf();
            std::fs::write(stage.path().join("partial"), "new").unwrap();
            // An error before publish drops only the private staged files.
        }
        assert_eq!(
            std::fs::read_to_string(target.join("release/previous")).unwrap(),
            "old"
        );
        assert!(!stage_path.exists());
    }

    #[test]
    fn successful_generation_replaces_only_a_managed_output() {
        let fixture = Fixture::new();
        let target = fixture.0.join("pack");
        std::fs::create_dir_all(target.join("release")).unwrap();
        std::fs::write(target.join("release/previous"), "old").unwrap();
        let stage = OutputStage::new(&target).unwrap();
        std::fs::create_dir(stage.path().join("beta")).unwrap();
        std::fs::write(stage.path().join("beta/current"), "new").unwrap();
        stage.publish().unwrap();
        assert!(!target.join("release").exists());
        assert_eq!(
            std::fs::read_to_string(target.join("beta/current")).unwrap(),
            "new"
        );
        std::fs::write(target.join("notes.md"), "user notes").unwrap();
        assert!(OutputStage::new(&target).is_err());
        assert!(target.join("notes.md").exists());
    }

    #[test]
    fn partial_generation_preserves_unselected_editions() {
        let fixture = Fixture::new();
        let target = fixture.0.join("pack");
        std::fs::create_dir_all(target.join("release")).unwrap();
        std::fs::write(target.join("release/previous"), "unchanged").unwrap();
        let stage = OutputStage::new(&target).unwrap();
        stage.preserve_unselected(&["beta".into()]).unwrap();
        std::fs::create_dir(stage.path().join("beta")).unwrap();
        std::fs::write(stage.path().join("beta/current"), "new").unwrap();
        stage.publish().unwrap();
        assert_eq!(
            std::fs::read_to_string(target.join("release/previous")).unwrap(),
            "unchanged"
        );
        assert_eq!(
            std::fs::read_to_string(target.join("beta/current")).unwrap(),
            "new"
        );
    }

    #[test]
    fn new_id_source_failure_cannot_overwrite_the_previous_package() {
        let fixture = Fixture::new();
        let target = fixture.0.join("pack");
        std::fs::create_dir_all(target.join("release")).unwrap();
        std::fs::write(target.join("release/previous"), "valid previous package").unwrap();
        let cfg = crate::config::Config {
            project_root: fixture.0.clone(),
            command_wiki_dir: fixture.0.join("wiki"),
            command_docs_dir: fixture.0.join("docs"),
            ..Default::default()
        };
        let owner = cfg
            .project_root
            .join("resources/release/vanilla/command/demo.json");
        std::fs::create_dir_all(owner.parent().unwrap()).unwrap();
        std::fs::write(
            owner,
            r#"{"node":{"<slot: Slot>":{"type":"NORMAL","key":"slots"}}}"#,
        )
        .unwrap();
        std::fs::create_dir_all(cfg.command_wiki_dir.join("demo")).unwrap();
        let document = crate::wiki_extract::parse_document("demo", "wiki", "示例。\n==参数==\n{{argument|slot|enum|be=1}}\n: 必须为以下其中之一：\n:* <code>known</code> — 有来源的槽位\n", 12).unwrap();
        std::fs::write(
            cfg.command_wiki_dir.join("demo/extracted.json"),
            serde_json::to_vec(&document).unwrap(),
        )
        .unwrap();
        let stage_path;
        {
            let stage = OutputStage::new(&target).unwrap();
            stage_path = stage.path().to_path_buf();
            let ids = stage.path().join("id");
            std::fs::create_dir(&ids).unwrap();
            std::fs::write(ids.join("slots.json"), r#"{"id":"slots","type":"normal","content":[{"name":"known"},{"name":"new","description":"上游新条目的旧猜测"}]}"#).unwrap();
            let error =
                crate::wiki_catalog::refresh_generated_catalogs(&cfg, &ids, "release", "vanilla")
                    .unwrap_err();
            assert!(error.to_string().contains("release/vanilla/slots"));
            assert!(error.to_string().contains("new"));
            let proposal: serde_json::Value = serde_json::from_str(
                &std::fs::read_to_string(
                    cfg.command_docs_dir
                        .join("generated_catalogs/release/vanilla/slots.json"),
                )
                .unwrap(),
            )
            .unwrap();
            assert!(proposal["content"][1].get("description").is_none());
        }
        assert!(!stage_path.exists());
        assert_eq!(
            std::fs::read_to_string(target.join("release/previous")).unwrap(),
            "valid previous package"
        );
    }
}
