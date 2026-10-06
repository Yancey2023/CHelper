# 数据源与缓存

## 数据源一览

| 用途 | 来源 | 说明 |
| --- | --- | --- |
| release / beta 的 ID 与包版本 | `XeroAlpha/caidlist@master` 的 `version/release`、`version/beta` | `autocompletion/<branch>.json`（OCR 枚举）、`package/data.json`（APK 静态分析枚举）、`beta/gametest/all.json`（方块属性） |
| netease 的 ID | `XeroAlpha/caidlist` 上**指定 commit** 的 `version/release` | 默认 `5c780d1d`：master 历史上 release 数据停留在 1.21.50.07（对应网易 1.21.50.07）的最后一个提交；在 `config.toml` 的 `[netease] commit` 中可改为任意 commit |
| caidlist 翻译目录（第 3 级） | 同 master 快照的 `translation/*.json` | 引用的 ST / JE / BE 资源由本工具自行抓取，不使用 caidlist 已抓好的缓存文件 |
| 标准译名表（ST） | `zh.minecraft.wiki` 的 `Module:Autolink/{Block,Item,Entity,Biome,Effect,Enchantment,Environment,Other,Exclusive}` | Scribunto 控制台执行 `mw.text.jsonEncode(p)` + `Module:ZhConversion` 简繁转换 |
| gamerule 类型（int/bool） | `Module:Gamerule_type_values_BE` | LSON 解析 |
| 方块属性描述 | `Module:Block_property_descriptions_BE` | LSON 解析 |
| gamerule 描述（第 2 级） | wiki 页面《游戏规则》`?action=raw` | wikitext 解析（移植 `update_translation.py`） |
| particle 描述（第 2 级） | wiki 页面《基岩版粒子》`?action=raw` | 同上 |
| Java 版语言文件（JE） | `piston-meta.mojang.com` 版本清单 → 最新快照（跳过愚人节版本）→ `client.jar` 内 `en_us.json` + 资源索引定位 `zh_cn.json` | client.jar 按 sha1 校验缓存 |
| 基岩版语言文件（BE） | `Mojang/bedrock-samples` 的 `resource_pack/texts/zh_CN.lang` | release→`main`、beta→`preview`；netease 先按 `v<包版本>` 尝试标签，失败回退 `main` |

> 注意：`resource_pack` 是单数——bedrock-samples 仓库顶层目录名即如此。

## 仓库快照缓存（tarball）

```
cache/refs/<repo>/<ref>.json          # 分支 → commit 解析缓存（ref_ttl_minutes）
cache/tarballs/<repo>/<sha>.tar.gz    # codeload tarball（按 sha 缓存，永不重复下载）
cache/extract/<repo>/<sha>/…          # 只解压 version/ 与 translation/ 两个子树
```

- 分支指向通过 `git ls-remote` 解析（不走 GitHub API，避开匿名 60 次/小时的限流）；
  指定 commit 的数据源跳过解析（commit 永不变更）。
- sha 未变时直接复用解压结果，**不重新下载 tarball**。
- `--refresh` 跳过 ref 缓存并强制重新下载。
- 只支持公开仓库（codeload 匿名下载）；所有用到的仓库均为公开。

## HTTP 缓存

```
cache/http/data/<sha256(url)>   # 响应体
cache/http/meta/<sha256(url)>.json  # { fetched_at, extra }
```

- TTL 默认 24 小时（`http_ttl_hours`）；wiki 模块页为 1 小时（对齐 caidlist）；
  tarball、Mojang 语言资源为长期（内容不可变或按 sha 校验）。
- 下载失败时自动回退旧缓存并警告（对齐 caidlist wiki.js 的行为）；
  无旧缓存时才报错终止。
- `client.jar` 额外做 sha1 校验，不匹配则重新下载。

## 配置项（config.toml）

```toml
[master]            # 主数据源：repo + ref（或直接 commit）
repo = "XeroAlpha/caidlist"
ref = "master"

[netease]           # netease 数据源：直接指定 commit
repo = "XeroAlpha/caidlist"
commit = "5c780d1d048779067e5dc60b8a3345ad578baf9e"

[bedrock_lang]
release_ref = "main"
beta_ref = "preview"
netease_ref = ""    # 留空 = release_ref + 按版本号尝试标签
try_version_tag = true

http_ttl_hours = 24
ref_ttl_minutes = 60
```

路径类配置（`cache_dir`、`output_dir`、`untranslated_dir`、`translations_dir`、
`ai_translations_dir`、`data_dir`、`netease_extra_dir`）默认固定在项目根下，可覆盖；
相对路径相对项目根解析。

## netease 多出内容（netease_extra）

netease 相对指定的 release 版本**只多不少**。多出的 ID 放在
`netease_extra/<branch>/<输出类别名>.json`（JSONC 对象：`{ "ID": "中文描述" }`，
描述可为空字符串）。规则：

- **只叠加不覆盖**：已存在于正常管线结果中的 ID 会被忽略；
- 只能叠加到本版本生成结果中已存在的类别（版本特性门控之外的类别会被跳过并警告）；
- 文件按输出名命名（`entity.json`、`block.json`、`music.json`……）。

## 已知限制

- 只支持公开仓库：caidlist 与 bedrock-samples 均为公开仓库，无需任何令牌。
- netease 的数据锚点是一个固定 commit（1.21.50.07 时代的历史快照），不会随
  master 更新；如需更换对应版本，改 `[netease] commit` 后运行 `--refresh` 即可。
- 基岩版语言文件取自 bedrock-samples（官方示例包），与旧流程从 APK 中提取的
  语言文件在个别键上可能存在差异（教育版内容覆盖面）。
