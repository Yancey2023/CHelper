# 客户端接入、工具链与路线图

> **状态**：安卓已接入主包链路（P0 引擎侧完成、Kotlin 接线完成并经真机回归通过——命令补全/库页高亮共享 KernelCache 按启用段合成，rawtext 数据同走主包段）｜**当前只落地安卓应用**（Web/Qt 维持现状，继续加载旧 `.cpack`）。

## 1. 工具链（资源侧）

### 1.1 打包与校验

在 `CHelper-Resource`（或 `scripts/`）提供：

- **打包脚本**：把"拓展包目录"（含 manifest/command/ID/json/selector）校验后打成 zip 并改名 `.chepack`；
  - 校验项：manifest 必填字段、目录命名规范化（`ID/`→`id/` 建议统一为小写，由打包工具规范化）、json 均可解析、命令 json 的 `extends` 声明与 `branches` 一致性、ID 引用存在性（可先做静态检查，运行期仍由合成器兜底）；
- **示例包**：仓库内维护 1-2 个示例（含：自定义命令 + 多命令头 + 自定义 ID 表 + execute 分支扩展 + 自定义选择器变量），作为文档/测试的黄金样例；
- **主包制作**：`CHelper-Resource/tools/build_main_pack.mjs` 把 `resources/{beta,release,netease}/{vanilla,experiment}` 六分支生成完整主包目录源 `CHelper-Resource/main-pack/`（versions/<vt>/<branch> 段 + 聚合 manifest，见 [pack-format.md](./pack-format.md) §9）；`--zip` 另产出单文件 `generated/main-packs/main-pack.chepack`。目录源是正式产物（可审阅/引擎目录装载），zip 用于分发；`generated/` 为 gitignore 的构建产物目录。

### 1.2 与现有生成流程的关系

`scripts/build.py` 现有流程（资源目录 → `CHelperResourceGenerator` → `generated/cpack`）**不变**（Web/Qt 继续使用它产出的 `.cpack`）；**安卓转入新架构**：主包（`.chepack`）与拓展包作为引擎（Composer）输入，版本/分支切换不再依赖多份 `.cpack` 文件替换。可选的"预编译"优化（把 .chepack 预校验/预物化缓存）列 P3。

## 2. Android 接入

现状（已切换，可复用）：`MainPackProvider`（assets 打开 `main-pack.chepack` 一次、常驻）+ `CHelperCore.compose(mainPack, enabledSegments, extensionPacks)`；合成内核由应用级 **`KernelCache`** 统一持有（见 [kernel-cache.md](./kernel-cache.md)）——命令补全（`CompletionViewModel`）与库页 MCD 高亮（`MCDRenderer`）按启用段 acquire/release 租借同一份内核，切段 = 只改启用段配置经 KernelCache 重新合成、旧段自动驱逐；rawtext 数据集经 `MainPack.readFile` 读主包启用段，与命令补全同源主包；`SettingsScreen.kt` 命令分支列表**由主包 `segments`（name/version）动态生成**（数据包新增段自动出现）。旧 Kotlin 加载 API（`fromAssets`/`fromFile`）清理中——Web/Qt 用各自平台加载路径，与此无关。

改动（剩余）：

1. **核心 API**：JNI 合成入口与 Kotlin `CHelperCore.compose(mainPack, enabledSegments, extensionPacks)` 工厂已落地（P0，见 p0-tasks.md T7 与 kernel-cache.md）；包管理模型（已装包列表/启停/顺序/删除）已随"资源包管理"页落地（见下）；
2. **导入**：
   - 文件选择（SAF）选取 `.chepack` → 复制到应用文件目录（`filesDir/packs/`）或直接读字节；
   - 展示 manifest 信息（名称/作者/版本）→ 确认导入；
3. **包管理 UI**（设置页"资源包管理"区，已落地）：已装包列表（名称/版本/来源作者）、启用开关、**手动排序**（列表顺序即合成顺序，靠上优先）、删除；变更 → 合成 → 命令页回到前台自动重建；
4. **来源展示**：`Suggestion` 序列化新增 `packName`，补全列表 UI 对非内置来源显示徽标（如 `来自 xx`）；
5. **兼容**：低版本核心无合成 API → 隐藏拓展包入口。

## 3. Web / Qt（暂不做，维持现状）

**暂不做**：Web/Qt 端继续使用旧的六份 `.cpack` 与旧分支切换，不引入主包/合成器/拓展包 API；若未来要做，接入方式按本文档与 [segment-loader.md](./segment-loader.md) 补齐。

## 4. 待办与排期（见 §6 路线图）

## 5. 测试计划

### 5.1 Core 单测（优先，决定行为正确性）

| 用例 | 覆盖点 |
| --- | --- |
| 无拓展包合成 | 合成 CPack 与直接加载主包行为一致（解析/补全/高亮快照） |
| 主包版本段装载 | 从 `main-pack.chepack` 装载 `beta/vanilla` 等各段，合成结果与对应分支直接加载一致；段清单/UI 版本切换正确 |
| 追加自定义命令 | 命令名补全含新命令；`/mycmd ...` 结构与补全正确；来源标注正确 |
| 多命令头 | `/tp` 与 `/teleport` 均解析到同一 NodePerCommand |
| 同名命令冲突 | 装载序裁决（先到先得）、被覆盖列表 |
| 自定义 ID 表 / 跨包引用 | 命令 `key` 引用扩展表；与主包同名表追加去重 |
| execute 分支扩展 | 扩展子命令可解析、isEnd 正确、原有 execute 行为不变（回归） |
| 非法包 | 坏 zip / 缺 manifest / 引用缺失 / REPEAT 嵌套 → 整包拒绝且不破坏现有 core |
| 来源字段 | 序列化往返不落盘（内存字段）、建议对象携带 packName |
| selector V1 | 自定义 `@x` 变量与参数可用；内置变量行为逐项回归 |

### 5.2 安卓冒烟

安卓跑一遍：导入示例包 → 启用 → 输入扩展命令 → 补全含来源徽标 → 停用恢复内置。

## 6. 路线图

| 阶段 | 范围 | 依赖 | 说明 |
| --- | --- | --- | --- |
| **P0** | 主包（`main-pack.chepack`）制作与装载（版本段）+ 合成器核心（command/id/json/repeat 合并 + 冲突策略 + 来源标注数据结构）；`CPack` 装载/物化小重构；安卓端到端跑通 | 无 | 核心最小闭环；不动选择器 |
| **P1** | 来源标注透传 + UI 徽标；`.chepack` 打包/校验脚本；安卓导入与包管理 UI；**blockIds 条目级合并**（自定义方块状态进 setblock/fill，见 [composer.md](./composer.md) §3.3 与示例 `ID/block.json`）——以上各项**已全部落地**（含 item 条目级合并）；rawtext 数据源迁移**已提前落地**（R1-R5：主包段数据 + 版本联动），P1 收尾其 R2b/R6 与 §7 补全内核引擎化接线（见 [rawtext-convergence.md](./rawtext-convergence.md)） | P0 | 用户可见价值 |
| **P2** | selector 数据化 V1（变量表 + 简单参数）——**已落地（子集）**：自定义变量 + 简单参数并入全局参数表（见 [selector-data.md](./selector-data.md) §2.1）；V2 复合参数待排 | P0 | 核心最大单项 |
| **P3** | selector V2（复合参数）；行为声明字段扩展（suggest/noSuggestion/SUGGEST_LIST）；预编译缓存/包签名 | P2 | 远期能力 |

## 7. 风险登记（汇总）

| # | 风险 | 等级 | 缓解 |
| --- | --- | --- | --- |
| 1 | selector 数据化改变内置行为 | 高 | 内置默认数据 + 逐项回归/快照；分 V1/V2 |
| 2 | NormalId 来源字段与既有序列化兼容 | 中 | 内存字段、不进二进制；安卓协议加字段且向前兼容 |
| 3 | execute 扩展依赖主包保留 repeatNodeData | 中 | 已验证二进制保留；json 目录天然保留；文档声明约束 |
| 4 | item 大表合并成本（block 已定条目级合并，P1） | 中 | **已落地**：与 block 同套条目级合并（见 composer.md §3.3）；自定义物品进 give/pitem |
| 5 | 合成性能与安卓内存 | 低-中 | 变更时合成；主包解包常驻 + 合成 CPack 大表驻留监控 |
| 6 | 第三方包安全 | 中 | 解压限额、整包拒绝、失败不降级半成品 |
| 7 | `CPack` 构造重构回归 | 中 | 保持三个现有构造入口行为不变；全量现有测试回归 |

## 8. 验收标准（V1 全量）

1. 无拓展包时与当前行为**完全一致**（自动化回归通过）；
2. 一个示例 `.chepack` 导入启用后：自定义命令/候选/execute 子命令/来源徽标全部按本文档工作；
3. 停用/删除包后恢复内置行为；
4. 非法包被拒绝且不影响当前核心；
5. 安卓验收用例通过（rawtext 补全在 [rawtext-convergence.md](./rawtext-convergence.md) 另验）。
