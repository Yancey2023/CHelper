# CHelper 资源包生成器

用 Rust 重写的 CHelper 内置资源包（`output/chelper`）生成器，替代
[caidlist](https://github.com/XeroAlpha/caidlist) 中基于 Node.js 的
`src/generators/chelper.js` 流程。数据源、翻译管线与输出格式均与
caidlist `new_chelper` 分支的生成结果对齐。

## 快速开始

```bash
# 生成全部版本（release / beta / netease）
cargo run --release -- generate

# 只生成指定版本
cargo run --release -- generate release beta

# 与 caidlist 的参考输出对比（格式 + 内容）
cargo run --release -- verify --reference E:/project/CHelper/caidlist/output/chelper

# 强制刷新缓存（重新解析分支指向并重新下载）
cargo run --release -- generate --refresh

# 清空缓存
cargo run --release -- clean-cache

# 对比资源包命令与 caidlist 的 mcpews 语法
cargo run -- check-commands --mcpews E:/project/CHelper/caidlist/version/release/autocompletion/vanilla/mcpews.json

# 将已有命令文件中的 syntax 更新为 mcpews 版本，并查看节点描述复核提醒
cargo run -- check-commands --mcpews E:/project/CHelper/caidlist/version/release/autocompletion/vanilla/mcpews.json --apply
```

命令检查默认读取 `../CHelper/CHelper-Resource/resources/release/vanilla/command`；
可用 `--resources` 指定其他资源目录，用 `--edition`、`--branch` 选择版本和分支。
检查结果会列出新增/过期语法、缺失或过期的命令文件。`--apply` 只更新已有命令文件
的 `syntax` 数组；报告会提醒逐项核对根级 `description` 和 `node` 树，因为节点结构及
说明文字需要按新语法人工维护。

生成结束时，若某类别同时存在已翻译和未翻译 ID，程序会输出汇总警告。未翻译 ID
列表写入 `output/untranslated/<版本>/<分支>/<类别>.json`；每个文件是 ID 数组，
没有漏译的类别会删除旧清单，不产生空文件。该目录可通过 `untranslated_dir` 配置。

## 产物

```
output/chelper/{release,beta,netease}/{vanilla,experiment}/id/*.json
```

release / beta 每分支 29 个文件：caidlist 原有 26 个（`block.json`、`item.json`、
`entity.json`、`gameRuleInteger/Boolean.json`……），外加本工具新增的
`gameMode.json`（手工数据）与 `particleEmitter.json`（wiki 抓取）。

netease 数据取自 1.21.50.07 时代（`config.toml` 的 `[netease] commit` 指定），
受版本特性门控影响为 26 个文件（1.21.50 时代尚无 controlScheme、feature、
featureRule 三类）。

## 环境要求

- Rust 1.85+（开发时使用 1.99；依赖以 edition 2021 构建未设 MSRV 下限）
- 网络访问：GitHub（公开仓库 tarball、bedrock-samples）、zh.minecraft.wiki、Mojang piston
- git CLI（仅用于解析分支指向的 commit）

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [architecture.md](architecture.md) | workspace 结构与模块职责 |
| [data-sources.md](data-sources.md) | 数据源、tarball 缓存、HTTP 缓存机制 |
| [translation-priority.md](translation-priority.md) | 翻译来源、复核状态与引用 DSL |
| [output-format.md](output-format.md) | chelper 输出格式契约 |
| [verification.md](verification.md) | 与 caidlist 参考输出的对比验证报告 |

## 与旧流程（caidlist JS）的关系

| 事项 | 旧流程 | 本工具 |
| --- | --- | --- |
| release/beta ID | 本地 APK 静态分析 + 真机 OCR + Script API | 直接读取 caidlist 仓库 `version/` 提交数据 |
| netease ID | netease_dev 启动器 APK（已停更） | 指定 caidlist master 历史上的 commit（release 数据停留在 1.21.50.07 的最后提交）+ 手工维护多出内容 |
| 翻译 | translation 目录 + ST/lang 自动匹配回写 | 四级优先级（已复核 > wiki > caidlist translation > 未复核 AI），引用资源自行抓取 |
| 外部下载 | `cachedOutput` JSONC 缓存 | tarball + 内容寻址 HTTP 缓存（TTL，失败回退旧缓存） |
| gameMode / particleEmitter | 手工维护 | 工具直接生成 |
