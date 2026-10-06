//! Archive Wiki sources for agent review. This module never derives command nodes or descriptions.

use crate::cache::{now_unix, HttpCache};
use anyhow::{bail, Context, Result};
use regex::Regex;
use serde::Serialize;
use std::collections::BTreeSet;
use std::path::Path;
use std::sync::{Arc, OnceLock};
use std::time::Duration;
use tokio::task::JoinSet;

const WIKI_PAGE: &str = "https://zh.minecraft.wiki/w/";
const TTL: Duration = Duration::from_secs(3600);
const CONCURRENCY: usize = 4;

#[derive(Debug, Serialize)]
pub struct CommandWikiReport {
    pub archived: usize,
    pub unavailable: usize,
    pub output: String,
    pub pages: Vec<CommandWikiPage>,
}

#[derive(Debug, Serialize)]
pub struct CommandWikiPage {
    pub command: String,
    pub requested_title: String,
    pub resolved_title: Option<String>,
    pub source_url: String,
    pub raw_url: String,
    pub redirects: Vec<String>,
    pub status: String,
    pub exported_at_unix: u64,
    pub error: Option<String>,
}

fn page_url(title: &str) -> String {
    let encoded: String = title
        .bytes()
        .map(|byte| match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' | b'/' => {
                (byte as char).to_string()
            }
            other => format!("%{other:02X}"),
        })
        .collect();
    format!("{WIKI_PAGE}{encoded}")
}

/// Download each distinct reviewed command's original Wiki page, following Wiki redirects.
/// Missing pages are recorded explicitly. They are never replaced with invented prose.
pub async fn archive_command_pages(
    http: Arc<HttpCache>,
    resources_root: &Path,
    editions: &[String],
    output: &Path,
    refresh: bool,
) -> Result<CommandWikiReport> {
    std::fs::create_dir_all(output)?;
    let mut commands = BTreeSet::new();
    for edition in editions {
        for branch in ["vanilla", "experiment"] {
            let directory = resources_root.join(edition).join(branch).join("command");
            for entry in std::fs::read_dir(&directory)
                .with_context(|| format!("读取命令清单失败: {}", directory.display()))?
            {
                let entry = entry?;
                let path = entry.path();
                if entry.file_type()?.is_file()
                    && path.extension().and_then(|s| s.to_str()) == Some("json")
                {
                    if let Some(command) = path.file_stem().and_then(|s| s.to_str()) {
                        commands.insert(command.to_string());
                    }
                }
            }
        }
    }

    // The overview is also archived for aliases and commands without their own article.
    let overview = archive_page(&http, "overview", "命令", output, refresh).await;
    if overview.status != "archived" {
        tracing::warn!(error = ?overview.error, "Wiki 命令总览抓取失败");
    }
    let mut tasks = JoinSet::new();
    let mut pages = vec![overview];
    for command in commands {
        while tasks.len() >= CONCURRENCY {
            pages.push(tasks.join_next().await.unwrap()??);
        }
        let http = Arc::clone(&http);
        let output = output.to_path_buf();
        tasks.spawn(async move {
            let title = format!("命令/{command}");
            Ok::<_, anyhow::Error>(archive_page(&http, &command, &title, &output, refresh).await)
        });
    }
    while let Some(page) = tasks.join_next().await {
        pages.push(page??);
    }
    pages.sort_by(|a, b| a.command.cmp(&b.command));
    let archived = pages
        .iter()
        .filter(|page| page.status == "archived")
        .count();
    let report = CommandWikiReport {
        unavailable: pages.len() - archived,
        archived,
        output: output.display().to_string(),
        pages,
    };
    std::fs::write(
        output.join("index.json"),
        serde_json::to_string_pretty(&report)?,
    )?;
    std::fs::write(
        output.join("README.md"),
        "# 命令 Wiki 原文\n\n每个命令目录中的 `page.wiki` 是 Wiki 返回的原始 wikitext，未改写。`source.json` 保存页面链接、重定向、导出时间和抓取状态。\n\n`index.json` 列出全部命令；`overview` 保存命令总览。缺失页面会明确记录，不提供臆造的原文。网络失败时保留已有快照供复核，但状态标为 `retained`；请查看错误原因。\n\n引用说明时应注明对应来源链接，并遵守中文 Minecraft Wiki 的内容许可与署名要求。下载程序不生成节点说明，节点复用和参数语义由 reviewer 逐命令核对。\n",
    )?;
    Ok(report)
}

async fn archive_page(
    http: &HttpCache,
    command: &str,
    title: &str,
    output: &Path,
    refresh: bool,
) -> CommandWikiPage {
    let mut page = CommandWikiPage {
        command: command.to_string(),
        requested_title: title.to_string(),
        resolved_title: None,
        source_url: page_url(title),
        raw_url: format!("{}?action=raw", page_url(title)),
        redirects: Vec::new(),
        status: "unavailable".into(),
        exported_at_unix: now_unix(),
        error: None,
    };
    let directory = output.join(command);
    let result = fetch_page(http, title, refresh).await;
    match result {
        Ok((resolved, redirects, text)) => {
            page.source_url = page_url(&resolved);
            page.raw_url = format!("{}?action=raw", page.source_url);
            page.resolved_title = Some(resolved);
            page.redirects = redirects;
            let write = std::fs::create_dir_all(&directory)
                .and_then(|()| std::fs::write(directory.join("page.wiki"), text));
            match write {
                Ok(()) => page.status = "archived".into(),
                Err(error) => page.error = Some(error.to_string()),
            }
        }
        Err(error) => {
            page.error = Some(format!("{error:#}"));
            if directory.join("page.wiki").exists() {
                page.status = "retained".into();
            }
            tracing::warn!(command, error = ?page.error, "命令 Wiki 页面不可用");
        }
    }
    let metadata = (|| -> Result<()> {
        std::fs::create_dir_all(&directory)?;
        std::fs::write(
            directory.join("source.json"),
            serde_json::to_string_pretty(&page)?,
        )?;
        Ok(())
    })();
    if let Err(error) = metadata {
        tracing::warn!(command, %error, "Wiki 来源元数据写入失败");
    }
    page
}

async fn fetch_page(
    http: &HttpCache,
    requested: &str,
    refresh: bool,
) -> Result<(String, Vec<String>, String)> {
    static REDIRECT: OnceLock<Regex> = OnceLock::new();
    let redirect = REDIRECT.get_or_init(|| {
        Regex::new(r"(?im)^\s*#(?:redirect|重定向|重新導向)\s*\[\[([^\]|#]+)").unwrap()
    });
    let mut title = requested.to_string();
    let mut redirects = Vec::new();
    let ttl = if refresh { Duration::ZERO } else { TTL };
    for _ in 0..8 {
        let url = format!("{}?action=raw", page_url(&title));
        let text = http.get_text_without_stale(&url, ttl).await?;
        if text.trim().is_empty()
            || text
                .trim_start()
                .to_ascii_lowercase()
                .starts_with("<!doctype html")
        {
            bail!("Wiki 返回空页面或 HTML 验证页: {url}");
        }
        if let Some(target) = redirect.captures(&text).and_then(|c| c.get(1)) {
            let target = target.as_str().trim().to_string();
            if target == title || redirects.contains(&target) {
                bail!("Wiki 重定向循环: {requested}");
            }
            redirects.push(title);
            title = target;
        } else {
            return Ok((title, redirects, text));
        }
    }
    bail!("Wiki 重定向层数过多: {requested}")
}
