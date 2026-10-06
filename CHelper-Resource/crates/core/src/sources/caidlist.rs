//! caidlist 仓库快照数据访问：`version/` 下的枚举数据与 `translation/` 目录。

use crate::parse_jsonc;
use anyhow::{bail, Context, Result};
use indexmap::IndexMap;
use serde_json::Value;
use std::path::{Path, PathBuf};

/// 一个已解压（或本地）的 caidlist 仓库快照。
#[derive(Clone)]
pub struct RepoSnapshot {
    root: PathBuf,
}

impl RepoSnapshot {
    pub fn new(root: PathBuf) -> Self {
        Self { root }
    }

    pub fn root(&self) -> &Path {
        &self.root
    }

    /// 读取仓库内 JSONC 文件（相对仓库根）。
    pub fn read_jsonc(&self, relative: &str) -> Result<Value> {
        let path = self.root.join(relative);
        let text = std::fs::read_to_string(&path)
            .with_context(|| format!("读取失败: {}", path.display()))?;
        parse_jsonc(&text).with_context(|| format!("解析 JSONC 失败: {}", path.display()))
    }

    pub fn read_text(&self, relative: &str) -> Result<String> {
        let path = self.root.join(relative);
        std::fs::read_to_string(&path).with_context(|| format!("读取失败: {}", path.display()))
    }

    /// 某版本数据目录是否存在某文件。
    pub fn exists(&self, relative: &str) -> bool {
        self.root.join(relative).exists()
    }
}

/// 单个分支合并后的枚举数据（package 静态分析 + autocompletion）。
///
/// 复刻 caidlist `generate.js` 的 `{...packageDataEnums.data[branch.id], ...autocompletedEnums}`：
/// package 数据在前、autocompletion 在后（同名类别覆盖），插入顺序保留。
pub struct Enums {
    map: IndexMap<String, Value>,
}

impl Enums {
    pub fn load(snapshot: &RepoSnapshot, version_dir: &str, branch: &str) -> Result<Self> {
        let mut map = IndexMap::new();
        let package_path = format!("{version_dir}/package/data.json");
        if snapshot.exists(&package_path) {
            let data = snapshot.read_jsonc(&package_path)?;
            let branch_data = data
                .get(branch)
                .ok_or_else(|| anyhow::anyhow!("package/data.json 缺少分支 {branch}"))?;
            if let Value::Object(obj) = branch_data {
                for (k, v) in obj {
                    map.insert(k.clone(), v.clone());
                }
            } else {
                bail!("{package_path} 的分支 {branch} 不是对象");
            }
        }
        let auto_path = format!("{version_dir}/autocompletion/{branch}.json");
        if snapshot.exists(&auto_path) {
            let data = snapshot.read_jsonc(&auto_path)?;
            if let Value::Object(obj) = data {
                for (k, v) in obj {
                    if k == "packageVersion" {
                        continue;
                    }
                    map.insert(k.clone(), v.clone());
                }
            } else {
                bail!("{auto_path} 不是对象");
            }
        } else {
            bail!("缺少 autocompletion 数据: {auto_path}");
        }
        Ok(Self { map })
    }

    pub fn package_version(snapshot: &RepoSnapshot, version_dir: &str) -> Result<String> {
        let info = snapshot.read_jsonc(&format!("{version_dir}/autocompletion/vanilla.json"))?;
        Ok(info
            .get("packageVersion")
            .and_then(|v| v.as_str())
            .context("缺少 packageVersion")?
            .to_string())
    }

    /// 取字符串数组类别（缺失时为空）。
    pub fn array(&self, name: &str) -> Vec<String> {
        match self.map.get(name) {
            Some(Value::Array(items)) => items
                .iter()
                .filter_map(|v| v.as_str().map(|s| s.to_string()))
                .collect(),
            _ => Vec::new(),
        }
    }

    pub fn has(&self, name: &str) -> bool {
        self.map.contains_key(name)
    }

    /// 取对象类别（键的插入顺序保留）。
    pub fn object_keys(&self, name: &str) -> Vec<String> {
        match self.map.get(name) {
            Some(Value::Object(obj)) => obj.keys().cloned().collect(),
            _ => Vec::new(),
        }
    }

    pub fn object(&self, name: &str) -> Option<&serde_json::Map<String, Value>> {
        self.map.get(name).and_then(|v| v.as_object())
    }
}

/// 复刻 `removeMinecraftNamespace`：若裸名与 `minecraft:裸名` 同时存在，丢弃裸名。
pub fn remove_minecraft_namespace(array: &[String]) -> Vec<String> {
    array
        .iter()
        .filter(|item| {
            if !item.contains(':') {
                let namespaced = format!("minecraft:{item}");
                if array.contains(&namespaced) {
                    return false;
                }
            }
            true
        })
        .cloned()
        .collect()
}
