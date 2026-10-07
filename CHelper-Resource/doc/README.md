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

# 强制刷新缓存（重新解析分支指向并重新下载）
cargo run --release -- generate --refresh

# 单独缓存全部命令 Wiki 原文，供子 agent 逐命令审阅
cargo run --release -- cache-command-wiki

# 清空缓存
cargo run --release -- clean-cache

# 对比资源包命令与 caidlist 的 mcpews 语法
cargo run -- check-commands --mcpews E:/project/CHelper/caidlist/version/release/autocompletion/vanilla/mcpews.json

# 将已有命令文件中的 syntax 更新为 mcpews 版本，并查看节点描述复核提醒
cargo run -- check-commands --mcpews E:/project/CHelper/caidlist/version/release/autocompletion/vanilla/mcpews.json --apply
```

命令检查默认读取项目目录下的 `resources/release/vanilla/command`；
可用 `--resources` 指定其他资源目录，用 `--edition`、`--branch` 选择版本和分支。
检查结果会列出新增/过期语法、缺失或过期的命令文件。`--apply` 只更新已有命令文件
的主命令 `syntax` 数组，别名保留在 `name` 中。`execute` 会应用
`data/command_patches/execute.json`，保留原有 REPEAT 入口和节点说明。
报告会提醒 reviewer 逐命令核对根级 `description`、`node` 树和参数语义：复用或重命名
已有等价节点，并依据 Wiki 编写清晰的作用说明；节点与枚举 ID 的说明不能留空，
也不能填入类型限制、默认值或枚举引导句。工具不会自动推导语义或编写描述。

生成结束时，若某类别同时存在已翻译和未翻译 ID，程序会输出汇总警告。未翻译 ID
列表写入 `output/untranslated/<版本>/<分支>/<类别>.json`；每个文件是 ID 数组，
没有漏译的类别会删除旧清单，不产生空文件。该目录可通过 `untranslated_dir` 配置。

## 产物

```
output/chelper/{release,beta,netease}/{vanilla,experiment}/
├─ manifest.json
├─ id/*.json
├─ command/*.json
├─ grammer/*.json
├─ json/*.json
└─ repeat/*.json
```

生成时读取已复核命令结构，`command` 中的说明和 brief 根据原始Wiki及少量原文
patch自动提取。`grammer`、`json`、`repeat` 的规范数据源为
`data/resource_definitions`，生成器直接读取并刷新对应`resources`快照，不能靠修改快照维护。
`execute`命令入口仍应用既有固定patch。ID和版本清单来自本次数据生成。
CHelper-Core 可直接打包这份完整产物：

```powershell
CHelperResourceGenerator.exe CHelper-Resource/output/chelper CHelper-Resource/generated
```

`output/command_wiki/<命令>/page.wiki` 保存原始页面 wikitext，`source.json` 保存来源链接、
重定向链和抓取状态。`index.json` 列出所有命令，`overview` 缓存命令总览。
可用 `command_wiki_dir` 配置此目录。缺失页面或下载失败会明确记录，保留已有快照但
不会伪造页面文本；缓存器只下载来源，逐命令的语义与原文审阅由 reviewer 完成。

release / beta 每分支生成一个 `manifest.json` 和 29 个 ID 文件，包括
`block.json`、`item.json`、`entity.json`、`gameRuleInteger/Boolean.json` 等，
以及本工具新增的 `gameMode.json`（手工数据）与 `particleEmitter.json`（wiki 抓取）。

netease 数据取自 1.21.50.07 时代（`config.toml` 的 `[netease] commit` 指定），
常规生成受国际版特性门控影响为 26 个 ID 文件，另外从已复核的中国版资源保留
`feature.json` 和 `featureRule.json` 两个扩展目录。国际版旧快照不包含中国版既有
`place` 扩展，保留这些目录可确保其命令依赖完整；不会伪造新 ID。`controlScheme`
仍按旧版本门控处理。

`output/chelper/old2new` 同时导出旧方块转换数据，供 Core 生成 `old2new.dat`。

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
| [command-review.md](command-review.md) | Wiki 原文、子 agent 逐命令审阅、节点复用和打包流程 |
| [wiki-extraction.md](wiki-extraction.md) | 原文 patch、参数和枚举提取、brief 覆盖与来源追踪 |
| [command-audit-2026-10-29.md](command-audit-2026-10-29.md) | 实际资源包全部命令逐字段审阅、交叉复核和打包验证 |
| [omission-cleanup-2026-10-29.md](omission-cleanup-2026-10-29.md) | 省略参数说明清理及原文补丁、提取和打包验证 |
| [constraints-cleanup-2026-10-29.md](constraints-cleanup-2026-10-29.md) | 限制范围说明清理、逐条审阅及打包验证 |
| [defaults-cleanup-2026-10-29.md](defaults-cleanup-2026-10-29.md) | 默认值注释清理、独立复核及打包验证 |
| [purpose-cleanup-2026-10-29.md](purpose-cleanup-2026-10-29.md) | 全部介绍用途精简、静态源修正及完整验证 |
| [terminology-review-2026-10-30.md](terminology-review-2026-10-30.md) | Wiki术语优先的全量复核、实际语义错配修正及打包验证 |

## 与旧流程（caidlist JS）的关系

| 事项 | 旧流程 | 本工具 |
| --- | --- | --- |
| release/beta ID | 本地 APK 静态分析 + 真机 OCR + Script API | 直接读取 caidlist 仓库 `version/` 提交数据 |
| netease ID | netease_dev 启动器 APK（已停更） | 指定 caidlist master 历史上的 commit（release 数据停留在 1.21.50.07 的最后提交）+ 手工维护多出内容 |
| 翻译 | translation 目录 + ST/lang 自动匹配回写 | 四级优先级（已复核 > wiki > caidlist translation > 未复核 AI），引用资源自行抓取 |
| 外部下载 | `cachedOutput` JSONC 缓存 | tarball + 内容寻址 HTTP 缓存（TTL，失败回退旧缓存） |
| gameMode / particleEmitter | 手工维护 | 工具直接生成 |
