# CHelper 内 json（rawtext）补全收敛设计

> **状态**：已部分实现（数据收敛 R1-R5 已落地、主包段数据驱动已通，见 §5；补全内核引擎化 core 侧就绪、Kotlin 编辑器接线待做，见 §7）｜让安卓应用内的 rawtext（JSON 文本）编辑器补全，跟随主包/补全包更新收敛为**单一数据源 + 版本联动**。

## 1. 现状（问题盘点）

CHelper-Android 的 rawtext 编辑器（`ui/rawtext/`，编辑 tellraw/titleraw 的 JSON 文本）补全是 **Kotlin 自实现**（`RawtextAutocomplete`），数据来自 assets 散文件（`RawtextDatasets`）：

| assets 文件 | 用途 | 来源/现状 | 主包是否已有同源数据 |
| --- | --- | --- | --- |
| `rawtext/entity.json` | 选择器 `type=` 实体补全 | CHelper-Resource `id/entity.json` 的**拷贝**（手工维护） | ✅ `id/entity.json`（同格式） |
| `rawtext/entityFamily.json` | 选择器 `family=` 族补全 | 同上（`id/entityFamily.json` 拷贝） | ✅ `id/entityFamily.json` |
| `rawtext/items.json` | `hasitem item=` 物品补全 | 独立 {id: 中文} 映射 | ✅ `id/item.json`（`{name,description}` 含中文名，`name` 无前缀） |
| `rawtext/translate.json` | 翻译识别符键补全（≈1.16MB） | vanilla-data + zh_CN.lang + Wiki | ❌ 主包没有 |
| `rawtext/slots.json` | —— | **无代码引用（死文件）** | —— |

问题：

1. **数据双份/脱节**：entity/entityFamily/items 与主包（CHelper-Resource）重复，assets 里是手工维护的拷贝，与资源更新不同步；`build.py` 也不拷贝它们；
2. **与版本选择脱节**：assets 是固定一份数据；用户在设置里切换 beta/release/netease（未来=主包启用段）后，rawtext 补全数据**不随版本切换**（命令补全会随，json 补全不会）；
3. **维护成本**：五件套分散（entity/entityFamily/items/translate/slots），格式各异（readMap vs readContent）。

## 2. 目标

- rawtext（json）补全数据与命令补全**同一数据源（单个补全包/主包）**，随版本段一致切换；
- 安卓 assets 里不再维护 rawtext 数据拷贝（随主包落地后删除）；
- 补全实现仍留在 Kotlin（rawtext 是自由 JSON 文本编辑，不接入 core 的 AST 补全管道——**不引入 core 补全**）。

## 3. 方案

### 3.1 数据同源映射

| rawtext 需要 | 收敛到主包 | 说明 |
| --- | --- | --- |
| 实体类型 types | `versions/<vt>/<branch>/id/entity.json` | 格式已兼容（content 的 `{name,description}`）；Kotlin `readContent` 直接复用 |
| 族 families | `id/entityFamily.json` | 同上 |
| 物品 items | `id/item.json` | 条目 `{name,description}` 含中文名；旧 readMap 映射改为 readContent；`name` 无 `minecraft:` 前缀，Kotlin 匹配处统一按"可省略前缀"处理（与命令补全一致，`shortId` 已存在） |
| 翻译识别符 translate | **新增主包数据目录 `text/translate.json`** | 见 3.2 |
| slots | 删除 | 无引用（如确需，放 Kotlin 常量，与 `RawtextConstants.SLOT_TYPES` 同源） |

### 3.2 主包 schema 扩展：允许任意数据子目录（`text/`）

- 现状：主包段固定 `command/id/json/repeat + manifest.json`；装载（build_main_pack / 段装载器）按目录清单处理；
- 扩展：把"段内文件"处理**通用化**——任何顶层数据子目录（`text/` 等）都随分层装载（shared → vt/shared → branch 后层覆盖），`manifest.json` 仍是段差异层；
- `text/translate.json` 按版本放 `resources/<vt>/<branch>/text/translate.json`：跨版本相同内容会自动进主包 `shared/` 层（分层工具按 hash 判定），版本差异则留在段层；
- 影响点：`tools/build_main_pack.mjs`（collect/拷贝改为整树而非固定子目录清单）；段装载器（segment-loader 的"文件集合"本就任意 relPath，无改动）；拓展包不涉及。

### 3.3 安卓侧数据获取（跟随主包与版本）

- 新增 JNI：`MainPack_readFile(vt, branch, relPath) → byte[]`（内部走段装载器合并规则：shared → `<vt>/shared` → `<vt>/<branch>`，只取单个文件，避免整段导出）；
- `RawtextDatasets` 改为：
  - 数据源 = 当前启用段的主包（读取 entity/entityFamily/item/`text/translate`）；
  - `ensureLoaded/ensureTranslateLoaded` 由 assets 读取改为经 JNI 按需 `readFile`（同样惰性、同样分离 translate 延迟加载）；
  - 版本段切换 → 重新装载（`RawtextDatasets` 增加"按段刷新"，随设置里版本选择回调执行）；
- assets 清理：删除 `rawtext/*`（entity/entityFamily/items/translate/slots）——rawtext 数据不再随 APK 单独携带（主包本身在 assets/主包路径中）。

### 3.4 与"这次更新"（P0 主包+合成器+安卓单端）的关系

- rawtext 收敛**依赖**主包落地与 JNI `MainPack` 系列（P0）——排在 P1（与来源标注 UI、包管理 UI 一批）；
- 旧 6 份 `.cpack` 在引擎替换后一并退场，rawtext 是其中最后一个"吃散数据"的模块，收敛后安卓 assets 只剩：主包（`.chepack`）+ 引擎所需文件。

## 4. 决策点（已定）

1. **translate 数据归属**：并入 `CHelper-Resource`（随版本、分层去重、一处维护）✅；
2. **数据完整度策略**：**结构先就位、内容渐进补** ✅——
   - 主包 `text/` 目录、装载与 `MainPack_readFile` 全部打通；
   - translate 先放入**现有数据集基线**（来自 `CHelper-Android/assets/rawtext/translate.json`，16909 键，对象格式 `{key: 中文}`），在 manifest/文档标注"内容待补全"；
   - 键不全、来源未整理**不阻塞架构**：后续数据更全/来源整理好后，替换或增量合并 json 并重跑 `tools/build_main_pack.mjs` 即同步（分层按 hash 自动去重，内容变化只影响差异层）；
3. **translate 是否逐版本**：先各版本放同一份基线（跨版本相同会自动进主包 `shared/` 层）；待来源带版本化后按版本细分，工具无需改动；
4. **前缀策略**：主包表 name 无 `minecraft:` 前缀；rawtext 使用处保持"省略前缀可匹配、补全显示与原 CHelper 命令补全一致"（以现有 `shortId` 语义对齐，确认 hasitem/type 的显示形态不回退）；
5. **slots.json**：删除（无引用）；若未来需要物品栏位补全，用 Kotlin 常量（SLOT_TYPES）。
6. **补全候选与文案来源边界** ✅（已拍板）：补全候选与文案（参数说明 / 值表中文 / 翻译键）统一由内核/主包数据提供，数据包可覆写；Kotlin 仅保留编辑器动态上下文（文档内记分板目标 / tag 候选）与纯 UI 引导文案，不维护数据。

## 5. 任务落点（P1）

| # | 任务 | 文件 |
| --- | --- | --- |
| R1 | 主包 schema 通用化（任意数据子目录 text/）+ 重建主包 | `tools/build_main_pack.mjs`、重新生成 `main-pack/` ✅ **已落地**（整树扫描；自检 639 文件一致） |
| R2 | translate 基线入库 `resources/<vt>/<branch>/text/translate.json`（从现有 16909 键数据集迁移，manifest 标注待补全） | `CHelper-Resource` ✅ **已落地**（六分支基线入 `text/`；分层自动合并为 `main-pack/shared/text/translate.json` 一份） |
| R2b | 来源更新脚本框架 `update_translate.mjs`：合并官方键表 / zh_CN.lang / wiki 增量 → 去重/缺译回退键名 → 输出 `text/translate.json`（记录键数/缺译数/来源版本元数据） | `CHelper-Resource/tools/` |
| R3 | JNI `MainPack_readFile`（段合并取单文件） | `CHelperAndroid.cpp`、Kotlin `MainPack` 包装 ✅ **已落地**（`MainPack_readFile0` + `MainPack.readFile`） |
| R4 | `RawtextDatasets` 迁移到主包段数据 + 版本联动刷新 | `ui/rawtext/RawtextDatasets.kt`、`RawtextViewModel.kt` ✅ **已落地**（`loadFromMainPack`/`loadTranslateFromMainPack`/`MainPackProvider` + `RawtextViewModel` 接线：数据只走主包段，随 `cpackBranch` 联动；`ensureLoaded`/`ensureTranslateLoaded` 已 no-op） |
| R5 | assets 清理（rawtext/* 删除；主包 .chepack 进 assets） | `CHelper-Android/app/src/main/assets/`、`scripts/build.py` ✅ 主包分发已入 build.py；✅ 旧 rawtext/* 与旧 cpack/* 已移出源码 assets（归档于仓库外 `D:\CHelper-legacy-assets`，不删除） |
| R6 | 回归：rawtext 补全（选择器/翻译/物品/实体/族）与现行为一致；切版本后数据随之切换；translate 缺失项显示回退（键名）不崩溃 | 手工 + 截图比对（步骤见 §6） |

## 6. 收尾步骤（需安卓环境）

1. **构建**：跑 `scripts/build.py`（生成 `main-pack.chepack` 并拷入安卓 assets）；Android Studio 首次直跑需先执行此步，否则 `MainPackProvider` 缺 assets 而回退旧数据；✅ 主包已在 assets（830707 字节），assets 现只含 `main-pack.chepack` + `about/` + `old2new/`；
2. **回归 R6**：进 rawtext 编辑器 → 选择器 `type=`/`family=`/`hasitem item=`、翻译键补全与现行为一致；切换设置里的版本/分支（cpackBranch，选项来自主包 `segments`）后重新进入编辑器，数据随之切换；`text/translate.json` 缺失键回退键名不崩溃；
3. **清理 R5**：✅ 已完成——`assets/rawtext/`（entity.json/entityFamily.json/items.json/translate.json/slots.json）与旧 6 份 `.cpack` 已移出源码 assets（归档 `D:\CHelper-legacy-assets`，弃用不删除）。

## 7. 补全内核引擎化（rawtext 补全改用命令补全引擎）

> **状态**：core 侧已实现（2026-09）；Kotlin 接线已落地——值候选（hasitem item/location、type/family/m、翻译键）走 `FragmentContext`（`FragmentCompletion` 适配器：内核优先 + 本地过滤/兜底），结构语境（参数骨架、scores/hasitem 子键、文档内目标/tag）保留本地；待真机回归。

目标：rawtext 编辑器 **UI 与功能不变**，仅把补全内核换成 CHelper-Core（与命令补全同一套 Parser/AutoSuggestion/Linter），数据统一来自主包。

已落地：

- **`FragmentContext`**（`CHelper-Core/src/chelper/FragmentContext.h/.cpp`）：片段补全上下文，不依赖完整命令直接解析一段语法单元：
  - `createTargetSelector(cpack, text)`：目标选择器片段（rawtext 选择器字段），变量/参数/值/hasitem/scores 补全与命令内一致；
  - `createId(cpack, key, text)`：ID 表片段（翻译键等键表补全）；
  - API：`getSuggestions / getErrorReasons / applySuggestion`（与 CommandContext 同管道）；
  - 生命周期：`shared_ptr<const CPack>` 保活 + 根节点实例按值持有（unique_ptr 稳定地址），纪律同 CommandContext。
- **翻译键表接入合成器**：`compose` 装载主包段时把 `text/translate.json`（对象 `{键: 中文}`）读入 `normalIds["translate"]`，`FragmentContext::createId(..., "translate", ...)` 直接前缀/包含补全键名（中文为描述）。
- **测试**：`tests/FragmentContextTest.cpp` 4 项（选择器空值单错误、参数建议、实体候选、翻译键前缀补全），桌面 gtest 全量 37/37 通过。

Kotlin 接线（已落地，2026-09，待真机回归）：

- **内核租约**：`RawtextCompletionKernel`（ui/rawtext）按启用段从共享 `KernelCache` 租借内核，生命周期随 `RawtextViewModel`（loadDatasets 时 IO 线程 ensure，onCleared 归还）；未就绪时补全完整回落本地。
- **适配器 `FragmentCompletion`**（内核优先 + 本地兜底）：
  - 值候选位置（kind=item/slot、value 的 type/family/m）→ `FragmentContext.openSelector`，候选（名字+中文描述）与命令补全同源同表；本地再做前缀/中文过滤与排序（内核在 hasitem 嵌套值位不过滤前缀），空结果/异常回落 `RawtextAutocomplete` 原逻辑；结构引导（逗号/闭括号、`!` 取反）保持本地规则；
  - 翻译键字段 → `FragmentContext.openId("translate")`（前缀 + 中文包含命中），回落本地 `keySuggestions`；
  - 编辑器动态上下文（scores 记分板目标、tag 候选）与 UI 引导文案维持 Kotlin 本地（非主包数据）。
- **值表主包化**：`GAMEMODES`/`SLOT_TYPES` 硬编码删除，游戏模式（含 default/别名）与 hasitem location 槽位改读主包 `id/gameMode.json`、`id/entitySlot.json`（描述回退键名）；数据集/翻译键表随启用段切换自动重载（`loadedSegment`）。
- **记录的行为差异（供回归对照）**：游戏模式候选由 4 → 13（含 default/s/c/a/d/0/1/2/5）；槽位描述与主包同源（如 slot.armor.body=实体护甲栏（如狼铠），slot.equippable 无描述回退键名）；参数/值中文描述与命令补全一致。

原计划中"assets 数据验证后整体删除"即 R5（assets 清理），已完成：旧 rawtext/* 与旧 cpack/* 已移出源码 assets（归档 `D:\CHelper-legacy-assets`，见 §5 R5/§6 步骤 3），与本次引擎化接线无关。

边界：`RawtextAutocomplete` 仍保留结构语境/文档上下文的实现（变量、参数骨架、scores/hasitem 子键、范围/目标/tag/引导提示与本地表兜底），值表数据全走主包段；翻译键来源 = 主包 text/（当前通用键表，如需按版本细分放回对应分支段）。

## 8. 不做（边界）

- rawtext 编辑器 UI/功能不改，仅换补全内核；
- translate 不进 command/id 体系（仅键表补全，不参与命令解析）；
