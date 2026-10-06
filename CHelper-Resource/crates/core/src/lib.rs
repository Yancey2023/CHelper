//! CHelper 资源包生成器核心库。
//!
//! 数据源为 [caidlist](https://github.com/XeroAlpha/caidlist) 仓库（GitHub tarball 拉取并缓存），
//! 输出 `output/chelper/{release,beta,netease}/{vanilla,experiment}/id/*.json`，
//! 格式与 caidlist `new_chelper` 分支的 `src/generators/chelper.js` 保持一致。

pub mod cache;
pub mod chelper;
pub mod command_sync;
pub mod config;
pub mod jfmt;
pub mod pipeline;
pub mod sources;
pub mod support;
pub mod translate;

use anyhow::Result;

/// 解析 JSONC（JSON with comments / 尾逗号，caidlist 仓库数据文件的格式）。
pub fn parse_jsonc(text: &str) -> Result<serde_json::Value> {
    Ok(serde_json5::from_str(text)?)
}

/// 将（可能含 `\n` 的）JSON 文本以 CRLF 行尾写入文件，文件末尾不带换行符。
pub fn write_crlf(path: &std::path::Path, content: &str) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(path, content.replace('\n', "\r\n"))?;
    Ok(())
}
