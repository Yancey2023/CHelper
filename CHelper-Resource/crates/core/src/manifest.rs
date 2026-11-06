//! 生成 CHelper 资源包清单。

use crate::{jfmt::to_string_pretty4, write_crlf};
use anyhow::Result;
use serde_json::json;
use std::path::Path;

/// 在资源包根目录写出 manifest.json。
pub fn write_manifest(root: &Path, edition: &str, branch: &str, version: &str) -> Result<()> {
    let edition_name = match edition {
        "release" => "[内置] 正式版",
        "beta" => "[内置] 测试版",
        "netease" => "[内置] 中国版",
        _ => unreachable!("未知版本 {edition}"),
    };
    let branch_name = match branch {
        "vanilla" => "原版",
        "experiment" => "实验性玩法",
        _ => unreachable!("未知分支 {branch}"),
    };
    let manifest = json!({
        "name": format!("{edition_name}{branch_name}"),
        "description": "CHelper内置资源包",
        "version": version,
        "versionType": edition,
        "branch": branch,
        "author": "Yancey",
        "packId": format!("BasicPack-{version}-{branch}"),
        "versionCode": 1,
        "isBasicPack": true,
        "isDefault": true,
    });
    write_crlf(&root.join("manifest.json"), &to_string_pretty4(&manifest))
}
