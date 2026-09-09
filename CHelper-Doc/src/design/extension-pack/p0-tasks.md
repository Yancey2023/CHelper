# P0 实现任务清单（主包 + 合成器 + 段装载器）

> **状态**：T1-T6 引擎侧已完成（桌面 gtest 37/37，含 MainPackTest/ComposerTest/FragmentContextTest）；T7 安卓侧 JNI 与 Kotlin 包装已落地（MainPack/MainPackProvider/CHelperCore.compose/FragmentContext），主链路 Kotlin 接线完成——命令补全与库页 MCD 高亮经共享 KernelCache 按启用段合成，真机回归通过（见 [kernel-cache.md](./kernel-cache.md)），rawtext 数据同走主包段。rawtext 编辑器补全内核已切 FragmentContext（值候选，适配器见 [rawtext-convergence.md](./rawtext-convergence.md) §7 引擎化，不在 R5）。版本/分支可选项**由主包（数据包）提供**：设置页列表来自聚合 `manifest.json` 的 `segments` 索引（含 name/version），数据包新增段后 UI 自动出现，应用不硬编码六段。
> 关联文档：[overview](./overview.md)、[pack-format](./pack-format.md) §9、[segment-loader](./segment-loader.md)、[composer](./composer.md)。

## 0. 前置条件（环境）

本机当前**没有** cmake/ninja/编译器。开工前先装工具链：

- Windows：Visual Studio Build Tools（含 MSVC）+ [cmake](https://cmake.org) + [ninja](https://ninja-build.org)，或 CLion 自带 toolchain；确保 `git` 可用（FetchContent 需从 GitHub 拉 fmt/spdlog/utfcpp/xxHash/serialization/googletest，锁 commit 版本，见 `CHelper-Core/CMakeLists.txt`）；
- 构建：`cmake -S CHelper-Core -B CHelper-Core/cmake-build-release -G Ninja && cmake --build CHelper-Core/cmake-build-release`；
- 跑测试：构建 `tests` 目标后运行测试可执行（gtest）。

已完成（无需再做）：分层主包 `CHelper-Resource/main-pack/` + `generated/main-packs/main-pack.chepack` + 制作工具 `tools/build_main_pack.mjs`。

---

## 任务一览（按依赖顺序）

| # | 任务 | 依赖 | 预计改动面 |
| --- | --- | --- | --- |
| T1 | 构建基建：确认测试目标可跑（**无需 miniz**：zip 由平台层解压后以 `Files` 传入） | 无 | `tests/` 占位、`extension/` 源码自动进 core |
| T2 | CPack 装载/物化重构（CPackBuilder + 文件集合装载） | T1 | `resources/CPack.h/.cpp`、`serialization/Serialization.h` |
| T3 | 段装载器 MainPack（open/listSegments/loadSegment） | T2 | 新增 `extension/MainPack.h/.cpp` |
| T4 | 合成器 Composer（段视图 + 拓展包 → 合成 CPack） | T2、T3 | 新增 `extension/Composer.h/.cpp` |
| T5 | 来源标注数据结构（NormalId/NamespaceId 内存字段 + 合成打标） | T4 | `resources/id/NormalId.h`、`NamespaceId.h`、Composer |
| T6 | 测试套件（一致性/装载/示例包/非法包） | T2-T5 | `tests/` |
| T7 | 安卓端导出与端到端（JNI + Kotlin + assets 主包 + 版本切换）——P0 收尾 | T3-T5 | `apps/CHelperAndroid.cpp`、`CHelper-Android` Kotlin 包装与 UI |

---

## T1. 构建基建：确认测试目标可跑（无需 miniz）

**文件**：`CHelper-Core/tests/`（新增 `MainPackTest.cpp`/`ComposerTest.cpp` 占位）、`CHelper-Core/CMakeLists.txt`（仅确认现有 gtest 目标可用）

- **不引入 miniz**：`.chepack`（zip）由平台层解压（安卓 `ZipInputStream`）后以 `Files` 集合传入 core，core 只实现 `Directory`/`Files` 两种源（见 [segment-loader.md](./segment-loader.md) §4）；
- `extension/*.cpp` 落在 `src/chelper/` 下，随 `file(GLOB_RECURSE .../src/chelper/*.cpp)` 自动进 `CHelperCore`，无需改 CMake 源列表；
- 确认 gtest 目标可构建、可运行（先跑一个空用例验证链路）。

**完成定义**：`cmake --build` 成功；空 gtest 用例通过。

---

## T2. CPack 装载/物化重构（关键前置）

**目标**：把"装载（apply*）+ 物化（afterApply）"从 CPack 构造中拆出，使合成器/段装载器能把"文件集合"累积装载后统一物化；**保持三个现有构造入口（目录/单 JSON/二进制）行为完全不变**。

**文件**：`CHelper-Core/src/chelper/resources/CPack.h`、`CPack.cpp`

改动要点（推荐实现：新增内部 Builder，而非大改现有构造）：

1. 抽出装载状态与动作到 `class CPackBuilder`（可放 `CPack.h` 内或新 `resources/CPackBuilder.h`）：
   - 成员：`Manifest manifest`、`std::vector<...> commands`、`normalIds/namespaceIds/blockIds/itemIds/jsonNodes/repeatNodeData`（与 CPack 现成员一致）；
   - 方法：`applyManifest(rapidjson doc)`、`applyCommandFile(bytes)`、`applyIdFile(bytes)`、`applyJsonFile(bytes)`、`applyRepeatFile(bytes)` —— 内部复用现有 `applyId/applyJson/applyRepeat/applyCommand` 的解析逻辑（把现有私有方法搬到 Builder 或改为静态辅助）；
   - `std::shared_ptr<const CPack> build()`：按现 `CPack::afterApply()` 顺序物化后构造/返回 CPack（`mainNode`/`cacheNodes` 等按现状建好）。
2. `CPack` 现有构造改为走 Builder（目录构造：先 builder.applyManifest + 各文件 apply* → build）；保证旧测试全绿。
3. 确定**文件→装载类型**的分派规则（段/拓展包通用）：
   - `manifest.json` → applyManifest；
   - `command/**` → applyCommandFile；`id/**` → applyIdFile；`json/**` → applyJsonFile；`repeat/**` → applyRepeatFile；
   - 其余忽略/告警。
4. `currentCreateStage`（NodeCreateStage）语义保持：applyJson/applyRepeat/applyCommand 期间设置正确阶段（Builder 内维护）。

**注意**：`NodePerCommand` 内含不可拷贝的 `FreeableNodeWithTypes nodes`，commands 以 `std::vector<NodePerCommand>` 值装载（与现状一致），**不要**做跨包 NodePerCommand 拷贝——合并靠"装载到同一 Builder"，见 T4。

**完成定义**：目录/JSON/二进制三个入口加载结果与重构前一致（跑现有 `CommandContextTest.cpp` 等全量）；`applyCommandFile(bytes)` 可单文件装载。

---

## T3. 段装载器 MainPack（segment-loader.md §3-§4）

**文件**：新增 `CHelper-Core/src/chelper/extension/MainPack.h`、`MainPack.cpp`（namespace `CHelper::Extension`）

实现 `segment-loader.md` §4 接口：

1. `MainPackSource{Kind::Files|Directory}` + `MainPack::open(source)`（与 T1 一致，**core 不内置 zip/miniz**，见 [segment-loader.md](./segment-loader.md) §4）：
   - Files → 平台层已解压 `.chepack`（zip）得到的文件集合（relPath + 字节，`shared_ptr` 常驻），直接采用；
   - Directory → 分层目录递归读（开发/调试），内部收敛为同一文件集合装载；
   - 读根 `manifest.json`，校验 `layout`（`"layered"` 当前；`"flat"` 兼容），建立分层索引：`shared/`、`versions/<vt>/shared/`、`versions/<vt>/<branch>/`；
2. `listSegments()`：从聚合 manifest `segments` 返回 `SegmentMeta{id, version, packId, name}`（UI 版本选择数据）；
3. `loadSegment(vt, branch)`：三层合并（后层覆盖同名），返回 `SegmentData{segmentId, files}`（含 `manifest.json`）；
   - 段缺失/层引用缺失 → 抛异常（带原因）；
   - 无副作用、可并发。
4. `verify()`（调试/测试用）：对本段组装结果与 `resources/<vt>/<branch>/` 抽样比对（构建期已全量自检，运行期可抽样）。

**完成定义**：单测：六段 `loadSegment` 文件集合与 `resources/` 对应段逐文件 sha1 一致；缺 manifest/段缺失拒绝；Files 与 Directory 源装载结果一致（坏 zip 在平台层解压阶段即被拒绝，core 不见 zip）。

---

## T4. 合成器 Composer

**文件**：新增 `CHelper-Core/src/chelper/extension/Composer.h`、`Composer.cpp`

实现 `composer.md` §2 步骤与 §3 合并规则（先做 P0 子集）：

1. `ComposeResult compose(SegmentData seg, std::vector<ExtensionPackData> packs, options)`：
   - 装载顺序：**主包段视图 → 各拓展包**（按调用方传入顺序，安卓端即"资源包管理"列表序）全部 apply 到**同一个 CPackBuilder**；
2. 合并规则（P0）：
   - `command`：主包段装载后，拓展包命令名与已有命令冲突 → 后装载者忽略并记入"被覆盖"（来源索引保留）；不冲突直接装载；
   - `id`（normal/namespace）：同名表**追加**（打来源标，见 T5）；`json`：按 `NodeJsonElement.id` 追加/覆盖；`repeat`：主包段数据 + 拓展包 `extensions/*.json`（`extends.repeat`）分支追加到对应 `RepeatData`（isEnd 同步）后再统一物化；
   - `block`（P1 再做，P0 允许拓展包不带 block 数据；若带则先告警不支持）；`selector`（P2 再做）；
3. `ComposeResult`：`shared_ptr<const CPack> cpack` + 元数据（各包生效命令数、被覆盖列表、告警）；
4. 整包失败策略：引用缺失/非法片段 → 整包拒绝（抛异常），不产出半成品。

**完成定义**：无拓展包时 `compose(段视图)` 结果与直接加载该分支 cpack 一致（补全/高亮快照）；示例包 `demo-server-pack` 追加命令可解析补全；`extensions/execute-addon.json` 分支追加进 execute 且原分支不受影响。

---

## T5. 来源标注数据结构

**文件**：`CHelper-Core/src/chelper/resources/id/NormalId.h`、`NamespaceId.h`、T4 的 Composer

1. `NormalId` 增加**内存态**可选字段 `std::optional<std::u16string> packName`（NamespaceId 继承自动获得）：
   - **不注册进 Codec**（不进二进制/持久化，避免包格式兼容问题）；内存字段，默认空；
2. 合成器打标：
   - id 表条目：合并/装载时给每个条目设置所属包名（主包段条目 → 内置名或空；拓展包 → `manifest.name`）；
   - 命令名来源：构建来源索引 `map<命令别名, 包名>`（供 UI/测试查询，也便于未来 Suggestion 透传）；
3. 序列化层不动（Suggestion.content 已是 NormalId，packName 随对象带出；JNI 透传字段放 P1 UI 阶段）。

**完成定义**：合成后：主包命令/候选 packName 为空或"内置"；示例包命令与候选 packName = "Demo 服务器命令包"；cpack 二进制往返后 packName 不落盘（内存字段验证）。

---

## T6. 测试套件

**文件**：`CHelper-Core/tests/`（新增 `MainPackTest.cpp`、`ComposerTest.cpp`；沿用现有 gtest 结构）

覆盖 `segment-loader.md` §9 与 `clients-and-roadmap.md` §5.1：

1. 六段装载一致性（T3 完成定义）；
2. 无拓展包合成 = 直接加载分支一致（补全/高亮快照——复用现有 CommandContext 测试模式）；
3. 示例包端到端：`/k`→kit、`/kit list`、`/hub`/`/spawn`、execute 扩展分支；
4. 同名命令冲突裁决、被覆盖列表；
5. 非法包（坏 zip/缺 manifest/引用缺失）整包拒绝且不影响已有 core；
6. 并发：多线程同时 `loadSegment`/`createContext` 无竞态。

**完成定义**：全量 gtest 通过（含既有测试零回归）。

---

## T7. 安卓端导出与端到端（P0 收尾）

**文件**：`CHelper-Core/src/apps/CHelperAndroid.cpp`（JNI）、`CHelper-Android` Kotlin 包装（`MainPack`/`CHelperCore.compose`）与版本选择 UI

1. JNI 新增：`MainPack::open`（平台层解压 `.chepack`（zip）后以 relPaths + contents 文件集合传入，与 T3/segment-loader.md §4 一致）、`listSegments`、`readFile` 导出与 `compose(mainPack, enabledSegments, extensionPacks)`；
2. Kotlin 包装：`MainPack`（AutoCloseable）、`CHelperCore.compose` 工厂与包管理模型；
3. 安卓端到端：assets 主包 → 选段 → compose → 示例包叠加 → 补全验证；版本/分支切换 = 只改启用段配置并重新合成（沿用 `CompletionViewModel.refreshCHelperCore` 风格重建）。

**完成定义**：安卓冒烟：`/k` 提示 kit；切换段后命令集随之切换（示例包与主包段叠加正确）。

**落地状态（最新）**：JNI 导出（MainPack open/listSegments/readFile、CHelperCore.compose0、FragmentContext）与 Kotlin 包装已完成并随 .so 部署；命令补全与库页 MCD 高亮统一走共享 **KernelCache**（见 [kernel-cache.md](./kernel-cache.md)）——`CompletionViewModel.refreshCHelperCore`/`MCDRenderer` 按启用段 acquire/release 租借（IO 线程 + 代际丢弃防竞态；合成入口收敛为 `KernelCache.acquire` → `MainPackProvider` + `CHelperCore.compose(启用段)`；`compose` 内部全局锁串行——C++ 合成复用全局构造阶段，禁止并发合成）；设置页命令分支列表改由主包 `segments`（name/version）动态生成；MCD 高亮内核与 rawtext 数据集同走主包段；真机回归通过（KernelCache 步骤 4 Android 冒烟，见 [kernel-cache.md](./kernel-cache.md) §8）。剩余：rawtext 编辑器补全内核切 FragmentContext（Kotlin SuggestionField 接线，见 [rawtext-convergence.md](./rawtext-convergence.md) §7 引擎化，不在 R5）。

---

## 实施顺序与风险

- 建议单人按 T1→T7 串行；T2 是最大回归风险点（三个构造入口行为不变 + 旧测试全绿）；
- T3/T4 可直接对照 `segment-loader.md` 与 `composer.md` 的 §合并规则实现；
- 若想在 P0 前先看到代码骨架：可在工具链就绪前，先按本清单落地 `extension/` 目录的 .h 接口与 T2 重构代码（编译验证后补）；
- 每项完成后更新本文件的勾选状态与备注。
