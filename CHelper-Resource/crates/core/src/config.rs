//! 运行配置：数据源、缓存目录、输出目录、各外部资源的引用与 TTL。
//!
//! 默认值内置在 [`Config::load`] 中，项目根目录下的 `config.toml` 可以覆盖任意字段。

use anyhow::{Context, Result};
use serde::Deserialize;
use std::path::{Path, PathBuf};

/// 单个 caidlist 数据源（GitHub 仓库 + 分支，或本地仓库路径 + 分支）。
#[derive(Debug, Clone, Deserialize)]
pub struct Source {
    /// GitHub `owner/repo`（公开仓库）。
    #[serde(default)]
    pub repo: String,
    /// 分支 / 标签（直接写 40 位 commit sha 时跳过引用解析）。
    #[serde(rename = "ref", default)]
    pub ref_: String,
    /// 直接指定 commit（优先于 `ref`）。适用于"分支不存在、只有固定 commit"的数据源，
    /// 例如 netease 对应的历史 release 数据（caidlist master 历史上的某个提交）。
    #[serde(default)]
    pub commit: Option<String>,
}

impl Source {
    pub fn github(repo: &str, ref_: &str) -> Self {
        Self {
            repo: repo.to_string(),
            ref_: ref_.to_string(),
            commit: None,
        }
    }

    /// 实际使用的数据锚点：commit 优先，其次 ref（若 ref 本身是 sha 也直接使用）。
    pub fn anchor(&self) -> &str {
        if let Some(commit) = &self.commit {
            return commit;
        }
        self.ref_.as_str()
    }

    /// 锚点是否本身就是 commit sha（7~40 位十六进制）。
    pub fn anchor_is_commit(&self) -> bool {
        self.commit.is_some() || is_sha(&self.ref_)
    }
}

/// 判断字符串是否形如 commit sha（7~40 位十六进制）。
pub fn is_sha(s: &str) -> bool {
    (7..=40).contains(&s.len()) && s.chars().all(|c| c.is_ascii_hexdigit())
}

#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct BedrockLangConfig {
    /// release 版使用的 bedrock-samples 引用。
    pub release_ref: String,
    /// beta 版使用的 bedrock-samples 引用。
    pub beta_ref: String,
    /// netease 版使用的 bedrock-samples 引用（留空则回退 `release_ref`）。
    pub netease_ref: String,
    /// netease 版按包版本号自动尝试的标签前缀匹配开关。
    pub try_version_tag: bool,
}

impl Default for BedrockLangConfig {
    fn default() -> Self {
        Self {
            release_ref: "main".into(),
            beta_ref: "preview".into(),
            netease_ref: String::new(),
            try_version_tag: true,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct Config {
    /// 项目根目录（由 CLI 注入，不来自 toml）。
    #[serde(skip)]
    pub project_root: PathBuf,
    /// 缓存目录（相对项目根）。
    pub cache_dir: PathBuf,
    /// 输出目录（相对项目根）。
    pub output_dir: PathBuf,
    /// 未翻译 ID 清单目录（相对项目根）。
    pub untranslated_dir: PathBuf,
    /// 已复核翻译目录（第 1 优先级；可含经人工复核的 AI 起草译文）。
    pub translations_dir: PathBuf,
    /// 未复核 AI 翻译目录（第 4 优先级，相对项目根）。
    pub ai_translations_dir: PathBuf,
    /// 手工数据目录（gameMode、药水描述等，相对项目根）。
    pub data_dir: PathBuf,
    /// netease 多出内容目录（相对项目根）。
    pub netease_extra_dir: PathBuf,
    /// HTTP 缓存过期时间（小时）。
    pub http_ttl_hours: u64,
    /// 数据源引用解析缓存时间（分钟），过期后重新 `git ls-remote` 解析分支指向。
    pub ref_ttl_minutes: u64,
    /// caidlist master 数据源（release / beta 的 ID 与游戏版本，以及 translation 目录）。
    pub master: Source,
    /// netease 数据源：其 `version/release` 作为 netease 的 ID 数据。
    pub netease: Source,
    pub bedrock_lang: BedrockLangConfig,
    /// Java 版语言文件使用的版本清单地址。
    pub java_manifest_url: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            project_root: PathBuf::from("."),
            cache_dir: PathBuf::from("cache"),
            output_dir: PathBuf::from("output/chelper"),
            untranslated_dir: PathBuf::from("output/untranslated"),
            translations_dir: PathBuf::from("translations"),
            ai_translations_dir: PathBuf::from("ai_translations"),
            data_dir: PathBuf::from("data"),
            netease_extra_dir: PathBuf::from("netease_extra"),
            http_ttl_hours: 24,
            ref_ttl_minutes: 60,
            master: Source::github("XeroAlpha/caidlist", "master"),
            netease: Source {
                repo: "XeroAlpha/caidlist".into(),
                ref_: String::new(),
                // master 历史上 release 数据停留在 1.21.50.07（对应网易 1.21.50.07）的最后一个提交
                commit: Some("5c780d1d048779067e5dc60b8a3345ad578baf9e".into()),
            },
            bedrock_lang: BedrockLangConfig::default(),
            java_manifest_url: "https://piston-meta.mojang.com/mc/game/version_manifest.json".into(),
        }
    }
}

fn join_root(root: &Path, p: &Path) -> PathBuf {
    if p.is_absolute() {
        p.to_path_buf()
    } else {
        root.join(p)
    }
}

impl Config {
    /// 读取 `config.toml`（可选）并解析所有相对路径。
    pub fn load(project_root: &Path, config_path: Option<&Path>) -> Result<Self> {
        let mut cfg = Self {
            project_root: project_root.to_path_buf(),
            ..Default::default()
        };
        let path = match config_path {
            Some(p) => Some(p.to_path_buf()),
            None => {
                let default_path = project_root.join("config.toml");
                default_path.exists().then_some(default_path)
            }
        };
        if let Some(path) = path {
            let text = std::fs::read_to_string(&path)
                .with_context(|| format!("读取配置失败: {}", path.display()))?;
            let override_cfg: Config = toml::from_str(&text)
                .with_context(|| format!("解析配置失败: {}", path.display()))?;
            // toml 里未写的字段保持默认值；写了的覆盖（project_root 除外）。
            let project_root = cfg.project_root.clone();
            let mut merged = override_cfg;
            merged.project_root = project_root;
            if merged.master.repo.is_empty() && merged.master.commit.is_none() {
                merged.master = cfg.master.clone();
            }
            if merged.netease.repo.is_empty() && merged.netease.commit.is_none() {
                merged.netease = cfg.netease.clone();
            }
            cfg = merged;
        }
        cfg.cache_dir = join_root(&cfg.project_root, &cfg.cache_dir);
        cfg.output_dir = join_root(&cfg.project_root, &cfg.output_dir);
        cfg.untranslated_dir = join_root(&cfg.project_root, &cfg.untranslated_dir);
        cfg.translations_dir = join_root(&cfg.project_root, &cfg.translations_dir);
        cfg.ai_translations_dir = join_root(&cfg.project_root, &cfg.ai_translations_dir);
        cfg.data_dir = join_root(&cfg.project_root, &cfg.data_dir);
        cfg.netease_extra_dir = join_root(&cfg.project_root, &cfg.netease_extra_dir);
        Ok(cfg)
    }
}
