//! CHelper 资源包生成器 CLI。

use anyhow::{Context, Result};
use chelper_core::{command_sync, config::Config, pipeline};
use clap::{Parser, Subcommand};
use std::path::PathBuf;

#[derive(Parser)]
#[command(
    name = "chelper",
    version,
    about = "CHelper 资源包（output/chelper）生成器"
)]
struct Cli {
    /// 项目根目录（默认当前目录）
    #[arg(long, global = true)]
    project: Option<PathBuf>,
    /// 配置文件路径（默认 <project>/config.toml）
    #[arg(long, global = true)]
    config: Option<PathBuf>,
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// 生成 chelper 资源包
    Generate {
        /// 版本列表（release / beta / netease，缺省为全部）
        #[arg(value_delimiter = ',')]
        editions: Vec<String>,
        /// 强制刷新缓存（重新解析引用并下载）
        #[arg(long)]
        refresh: bool,
    },
    /// 清空缓存目录
    CleanCache,
    /// 对比资源包命令语法与 caidlist mcpews.json
    CheckCommands {
        /// 资源包根目录（包含 <edition>/<branch>/command）
        #[arg(long, default_value = "./resources")]
        resources: PathBuf,
        /// caidlist mcpews.json 文件
        #[arg(long)]
        mcpews: PathBuf,
        /// 版本目录
        #[arg(long, default_value = "release")]
        edition: String,
        /// 分支目录
        #[arg(long, default_value = "vanilla")]
        branch: String,
        /// 将已存在命令文件的 syntax 替换为 mcpews 当前语法
        #[arg(long)]
        apply: bool,
    },
}

fn init_logging() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();
    init_logging();
    let project_root = cli
        .project
        .unwrap_or_else(|| std::env::current_dir().unwrap_or_else(|_| PathBuf::from(".")));
    let cfg = Config::load(&project_root, cli.config.as_deref())?;
    match cli.command {
        Command::Generate { editions, refresh } => {
            let editions = if editions.is_empty() {
                vec!["release".into(), "beta".into(), "netease".into()]
            } else {
                editions
            };
            let report = pipeline::generate(&cfg, &editions, refresh).await?;
            println!("{}", serde_json::to_string_pretty(&report)?);
        }
        Command::CleanCache => {
            let dir = &cfg.cache_dir;
            if dir.exists() {
                std::fs::remove_dir_all(dir)
                    .with_context(|| format!("清理缓存失败: {}", dir.display()))?;
            }
            println!("已清理 {}", dir.display());
        }
        Command::CheckCommands {
            resources,
            mcpews,
            edition,
            branch,
            apply,
        } => {
            let resources = if resources.is_absolute() {
                resources
            } else {
                project_root.join(resources)
            };
            let mcpews = if mcpews.is_absolute() {
                mcpews
            } else {
                project_root.join(mcpews)
            };
            let report =
                command_sync::check_commands(&resources, &mcpews, &edition, &branch, apply)?;
            println!("{}", serde_json::to_string_pretty(&report)?);
        }
    }
    Ok(())
}
