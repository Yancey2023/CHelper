# 架构

## Workspace 结构

```
CHelper-Resource/
├─ Cargo.toml              # workspace：crates/core + crates/cli，dev profile 限制 debuginfo
├─ config.toml             # 运行配置（全部字段可选，见下文）
├─ data/                   # 手工数据（不写死在代码里的内容）
│  ├─ game_mode.json       #   gameMode.json 的 content 数组
│  ├─ wiki_patches/        #   需规范化的少量 Wiki 原文改动
│  ├─ resource_definitions/ #   静态grammar/JSON/repeat的规范源；版本/分支按整文件覆盖
│  └─ potion_descriptions.json  # 三种药水的 47 条中文描述
├─ translations/           # ① 级已复核翻译（可由 AI 起草后经人工复核）
├─ ai_translations/        # ④ 级 AI 生成但未复核的候选译文
├─ netease_extra/          # netease 多出的 ID（vanilla/、experiment/ 按类别命名）
├─ cache/                  # 缓存（gitignore）
│  ├─ tarballs/            #   GitHub tarball / git archive 产物
│  ├─ extract/             #   按 commit sha 解压的仓库快照
│  ├─ refs/                #   分支 → commit 解析缓存
│  └─ http/                #   URL 内容寻址 HTTP 缓存
├─ output/chelper/         # 资源包生成产物
├─ output/untranslated/    # 各版本/分支按类别整理的未翻译 ID 清单
└─ crates/
   ├─ core/                # 全部业务逻辑（库）
   └─ cli/                 # chhelper 命令行入口
```

## crates/core 模块

| 模块 | 职责 | 对应 caidlist 源码 |
| --- | --- | --- |
| `config` | 配置与默认值 | `data/config.js`（仅保留生成所需部分） |
| `cache::tarball` | 仓库快照：`git ls-remote` 解析分支（指定 commit 则跳过）→ 下载 codeload tarball → 只解压 `version/`、`translation/` | 直接读仓库文件（取代 clone/checkout） |
| `cache::http` | HTTP 缓存：URL sha256 为键、TTL、失败回退旧缓存；POST 直通 | `util/common.js cachedOutput` + `util/network.js` |
| `sources::caidlist` | 快照数据访问：package/autocompletion 枚举合并、JSONC 读取 | `sources/applicationPackage.js` + `sources/autocompletion.js` 的缓存读取部分 |
| `sources::wiki` | wiki 抓取：Scribunto 控制台（标准译名表 / gamerule 类型 / 方块属性描述）、wikitext 解析（游戏规则 / 基岩版粒子） | `sources/wiki.js` + `update_translation.py` |
| `sources::lson` | Lua 表字面量解析器 | `util/lson.js`（chevrotain → 手写递归下降） |
| `sources::java_lang` | Java 版 zh_cn 语言文件：piston 清单 → 资源索引 | `sources/javaEdition.js` |
| `sources::bedrock_lang` | 基岩版语言文件：bedrock-samples `resource_pack/texts/zh_CN.lang` | `sources/applicationPackage.js`（APK 内语言文件） |
| `support` | 版本特性开关（决定类别是否存在） | `sources/support.js`（chelper 相关子集） |
| `translate` | 四级翻译优先级、引用 DSL、`{{...}}` 拼接模板、级联标准译名表 | `util/templateMatch.js` |
| `pipeline` | 编排：快照 → 共享资源 → 逐类别匹配 → netease 叠加 → 资源包与未翻译 ID 清单 | `generate.js generateBranchedOutputFiles` |
| `chelper` | ID 资源文件写出与方块属性值域补全 | `generators/chelper.js` |
| `jfmt` | 与 `JSON.stringify(v, null, 4)` 兼容的格式化器 | `JSON.stringify` |
| `command_wiki` | 逐命令保存 Wiki 原始 wikitext 和来源元数据，供 agent 阅读 | 原始页面 `action=raw` |
| `wiki_patch` | 原文精确替换及命中数检查，检测上游漂移 | 少量项目原文 patch |
| `wiki_extract` | 简介、参数、枚举对象、brief 及版别上下文提取 | patched Wiki 原文 |
| `command_descriptions` | 根据实际语法、参数名与自然上下文匹配既有节点说明，报告歧义/缺失 | Wiki 原文与既有结构 |
| `wiki_catalog` | 根据命令 ID 引用与参数封闭枚举自动提取目录说明，无独立注册配置 | 同一份 patched Wiki 原文 |
| `command_documentation` | 提案、逐字段来源、哈希与严格导出关口 | 本项目维护 |
| `command_patch` | 自动应用 execute 固定 patch，导出已复核资源与中国版扩展目录 | 本项目维护 |
| `resource_definitions` | 从data加载静态定义及版本差异，导出并刷新resources生成快照；无快照回退 | 本项目维护 |

## 依赖版本策略

所有依赖均取 crates.io 当前最新稳定版（2026-10 核查）：

| 依赖 | 版本 | 备注 |
| --- | --- | --- |
| reqwest | 0.13.5 | 0.13 起 `form` 不再是默认特性（需显式开启）；默认 TLS 改为 rustls（aws-lc-sys），Windows debug 构建的 PDB 会超过 link.exe 4GB 上限，因此 workspace 的 `[profile.dev]` 设了 `debug = "line-tables-only"` |
| sha1 / sha2 | 0.11.0 | digest 0.11，`new()/update()/finalize()` API 不变 |
| toml | 1.1.6 | 0.9 → 1.0，`from_str` 不变 |
| serde_json5 | 0.2.1 | `from_str` 不变 |
| serde / serde_json / indexmap / tokio / anyhow / thiserror / clap / tar / flate2 / regex / hex / tracing 系列 | 各自最新 | 在既有主版本内取最新 |

无法再往上升级的原因只有一种：已是 crates.io 的最新稳定版。

## crates/cli

子命令：

- `generate [editions…] [--refresh]`：生成；`editions` 为 `release`、`beta`、`netease` 的子集，缺省全部。
- `clean-cache`：清空缓存目录。
- `cache-command-wiki [--refresh]`：单独下载所有命令的 Wiki 原文。
- `extract-command-docs [editions…] [--commands <逗号列表>] [--apply]`：本地原文 patch 与自动提取、可审阅提案；来源完整时才允许写回。
- `check-commands`：同步主命令语法与 execute patch，其余节点由 agent 人工 review。

## 生成一张 chelper 资源包的流程

1. 缓存命令 Wiki 原文，应用原文 patch，再提取并匹配全部所选版本的命令说明；
   缺少明确来源时写出报告并停止，尚不导出资源包。
2. `RepoSource::checkout`：解析 ref → commit sha（缓存 `ref_ttl_minutes`）→ 下载 tarball（按 sha 缓存）→ 解压 `version/`、`translation/`。
3. 抓取共享外部资源（一次运行只抓一次，全部走 HTTP 缓存）：
   标准译名表（Scribunto）、gamerule 类型表、方块属性描述、游戏规则 wikitext、
   基岩版粒子 wikitext、Java 版 zh_cn 语言文件。
4. 对每个版本（edition）：
   - 读取 `version/<dir>/autocompletion/<branch>.json` 的 `packageVersion` 作为包版本
     与特性开关用的 `coreVersion`；
   - 抓取对应 bedrock-samples 引用的 `zh_CN.lang`（BE 语言文件）；
   - block.json 的属性数据固定取 `version/beta/gametest/all.json`
     （与 chelper.js 一致；netease 数据源自带 beta 数据时优先用自带）。
5. 对每个分支（vanilla / experiment）：
   - 枚举合并：`package/data.json` 的分支数据在前，`autocompletion/<branch>.json` 在后（键序保留）；
   - 按特性开关逐类别跑翻译匹配（类别与顺序对齐 `generate.js`）；
   - 派生 `music`（sound 的 `music.` / `record.` 子集）与 `lootTableWrapped`（键 JSON 引号化）；
   - item 的结果按 `enums.items` 顺序与 block 合并；entity/location/biome 按原始键序重建；
   - netease：叠加 `netease_extra/<branch>/<类别>.json` 中缺失的 ID（只多不少）；
   - 写出 `manifest.json` 与 ID 文件（4 空格缩进、CRLF、无末尾换行）；
   - 导出已复核命令结构，说明和 brief 从 patched Wiki 自动提取并匹配；缺少来源时阻止导出。
   - 静态grammar/JSON/repeat直接来自`data/resource_definitions`；成功生成后刷新对应`resources`快照，旧快照不参与选择或回退。
   - execute 应用固定 patch，网易分支补齐既有扩展目录。

## 版本特性开关

与 caidlist `support.js` 一致，以 `coreVersion`（= 数据文件中的 `packageVersion`）
做区间判断，决定以下类别是否生成：

| 类别 | 开关 |
| --- | --- |
| biome | `newLocateCommand` |
| lootTable | `lootTable` |
| damageCause | `damageCommand` |
| inputPermission | `inputpermissionCommand` |
| cameraPreset / cameraEasing | `cameraCommand`（experiment 分支阈值更低） |
| recipe | `recipeNewCommand` |
| hudElement | `hudCommand` |
| feature / featureRule | `placeCommandFeatureSubCommand` |
| controlScheme | `controlSchemeCommand` |

开关之外的类别（block、item、entity、sound 等）不受版本限制，数据缺失即为空。
