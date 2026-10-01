# 拓展包（Extension Pack）设计总览

> **状态**：P0 主包+合成器已实现（引擎 T1-T6 完成、安卓主包链路已通，见 [p0-tasks.md](./p0-tasks.md)）；selector 数据化与拓展包 UI 等属后续阶段；Web/Qt 未接入、维持旧 `.cpack` 流程｜面向：核心/安卓/网页开发与维护者
>
> 本文档描述一个"拓展包"能力：把补全候选与行为规则以 JSON 形式整合成独立、可导入、可叠加的包，运行时与内置资源包一起生效，并标注每条命令/候选的来源包。

## 1. 背景与动机

现状（2026-02，对应基岩版 1.26.0.29 命令集）：

- 全部命令语法、ID 候选、JSON 结构与"类型→补全行为"都被编进**单一 CPack**（`CHelper-Resource/resources/<版本>/<分支>`，构建期编译为 `.cpack` 二进制，打进 Android/Web/Qt 三端；**本能力（主包/拓展包/合成器）先在安卓应用落地**，Web/Qt 维持现状）；
- 补全候选（`id/*.json`）与补全行为（`AutoSuggestion.cpp` 的模板特化）**耦合在语法结构里**，第三方（服务器插件、网易版、模组、自定义物品）无法注入自定义命令或候选；
- 想让用户用到新命令/候选，只能改资源包并发版。

目标：

1. 提供**独立补全包**（含候选内容层 + 行为规则层），第三方无需改动 CHelper 核心或内置资源即可交付；
2. 支持**运行时导入/启用/停用**多个包（**当前只在安卓应用内**，导入无需重打包 App）；
3. **所有已启用包的命令与候选同时可用**（与内置包合并生效），附加包的命令/候选**标注来源包名**；
4. 包内允许**扩展内置 `execute` 的子命令分支**（追加式），但不允许注册类似 execute 的嵌套 REPEAT 结构。

非目标（明确不做，避免范围失控）：

- 不改写内置命令/候选（基础包仍以官方命令集为准，冲突时附加包让位或按合并规则处理，见合成器文档）；
- 不把 `AutoSuggestion` 的整体递归/性能敏感逻辑改造成"通用规则引擎"（行为层 JSON 化限定为"节点声明字段"，见包格式文档）；
- 不提供包市场/在线分发/签名体系（列入远期路线图）。

## 2. 术语

| 术语 | 含义 |
| --- | --- |
| 主包 / 基础包 | **内置补全包**：单文件 `main-pack.chepack`，**分层存储**全部原版数据——全局 `shared/` + `versions/<vt>/shared/` + 段差异（相同内容只存一份，省 ~70% 文件）；引擎按启用段（如 beta/vanilla）经"段装载器"组装完整视图（产物见 `CHelper-Resource/main-pack/`，工具 `tools/build_main_pack.mjs`） |
| 拓展包（Extension Pack） | 第三方交付的独立补全包，目录源打包为 `.chepack`（zip 改扩展名） |
| 合成器（Composer） | 把"主包 + 已启用拓展包"合并为单个只读合成 CPack 的加载层模块（实现：`CHelper-Core/src/chelper/extension/Composer.h` 的 `CHelper::Extension::compose`，见 p0-tasks.md T4） |
| 来源标注 | 命令/候选上记录的"来自哪个包"，用于 UI 展示 |
| 合成 CPack | 合成器输出的唯一驻留资源包；下游（CommandContext/Parser/补全/高亮）完全复用现有管道 |

## 3. 总体架构（合成器模型）

### 3.1 为什么是"加载期合成"而不是"运行时修补/多包路由"

依据现有实现的关键事实：

1. `CPack` 的**加载与物化分离**：目录/JSON/二进制构造先装载数据（`commands`、`normalIds`、`repeatNodeData` 等），再统一由 `afterApply()` 物化（`CPack.cpp`）。二进制格式**保留 `repeatNodeData`（repeatNodes/isEnd 源数据）**，物化在 afterApply 阶段重做——这使"合并源数据后重新物化"成为可能；
2. `NodeInitialization::initNode` 按 **CPack 自身的表**解析引用：`NORMAL_ID/NAMESPACE_ID` 的 `key` → `cpack.getNormalId/getNamespaceId`（找不到即抛异常）、`JSON` 的 `key` → 遍历 `cpack.jsonNodes`、`BLOCK/ITEM` → `cpack.blockIds/itemIds`、`REPEAT` → `cpack.repeatNodes`。因此**跨包引用只有在"合并成同一个 CPack 再物化"时才成立**；
3. `Parser<Node::NodeCommand>` 按命令名线性查找、**只解析命中的那一条** `NodePerCommand`，未命中报"命令名字不匹配"。合成 CPack 的 mainNode 覆盖全部包的命令后，命令名补全与解析天然统一；
4. 现有并发纪律：AST 节点数据指针指向 CPack 内存，CPack 由 `shared_ptr<const CPack>` 保活、只读共享。合成模型把"多包"收敛为**一个驻留对象**，纪律不变。

结论：**不建议**做"每包独立 CPack + 命令名路由分发"（命令名补全需要前端合成、错误提示割裂），也不建议"运行时修补只读主包"（破坏只读与生命周期）。采用**包集合 → 合成器 → 单一只读合成 CPack**。

### 3.2 数据流

```
已启用包集合（运行时可变）
 ├─ 主包 main-pack.chepack ─┐（含 versions/<vt>/<branch> 六段；按"启用段"装载，如 beta/vanilla）
 ├─ 拓展包 A .chepack ───────┼─ 解包/校验/按段展开（zip→json 字节集）─┐
 ├─ 拓展包 B .chepack ───────┘                                     ▼
                                            ┌───────────────────────┐
                                            │       Composer        │
                                            │ 1. 合并源数据：        │
                                            │    command / id / json │
                                            │    / repeat / selector │
                                            │ 2. afterApply() 一次物化│
                                            │ 3. 构建来源索引         │
                                            └──────────┬────────────┘
                                                       ▼
                                    合成 CPack（唯一只读实例, shared_ptr<const CPack>）
                                                       │
          ┌──────────────┬──────────────┬──────────────┼───────────────┐
          ▼              ▼              ▼              ▼               ▼
   CommandContext   Parser/AST    AutoSuggestion  高亮/结构/参数提示  Linter
   （全部复用现有实现，零改动）
```

- **版本选择 = 引擎配置**：切换 beta/release/netease × vanilla/experiment 只修改主包的"启用段"列表并重新合成，**不更换/不重新下载文件**（替代现状"每个分支一个 .cpack 文件、整体替换"的分发方式）；
- 包集合变化（导入/启用/停用/排序/版本切换）→ 触发**重新合成**一次（耗时毫秒~百毫秒级，UI 走 loading），之后与现在完全一致：多线程共享只读合成 CPack，`createContext` 创建上下文。

### 3.3 各子文档

| 文档 | 内容 |
| --- | --- |
| [pack-format.md](./pack-format.md) | `.chepack` 包格式规范：manifest、command/ID/json/selector 目录、execute 扩展、行为声明字段 |
| [composer.md](./composer.md) | 合成器设计：加载步骤、合并规则、来源标注、API、生命周期、安全 |
| [selector-data.md](./selector-data.md) | 选择器数据化设计（`selector/` 目录的 schema 与装配改造） |
| [clients-and-roadmap.md](./clients-and-roadmap.md) | 安卓接入（Web/Qt 维持现状）、工具链、测试计划与分阶段路线图 |

## 4. 全局合并策略（速览，细节见 composer.md）

| 数据 | 合并策略（草案） |
| --- | --- |
| `command/` | 主包段先行装载，扩展包**按应用内"资源包管理"列表顺序（靠上优先）先到先得**：与已装载命令同名 → 后包命令整体忽略并记录"被覆盖"（UI 可提示）；不同名直接追加 |
| `ID/` normal/namespace | 同名表（如 `entity`）**追加**（建议层按内容 hash 去重）；允许后加载包"同名条目覆盖"声明 |
| `ID/` block/item | 大表**条目级合并**（block 状态+属性描述、item 条目）：同键冲突先到先得（后装载忽略并告警），新条目追加；详见 [composer.md](./composer.md) §3.3 |
| `json/` | 按 `NodeJsonElement.id` 匹配、**取装载序首个命中**（主包先于扩展包） |
| `repeat`（execute 扩展） | 同 id（`execute`）的 `repeatNodes`/`isEnd` **分支追加**，重新物化 |
| `selector/` | 变量/参数定义叠加（基础包内置数据 + 扩展包数据，扩展包可增删改自己的变量） |
| 来源标注 | 每个命令、每条候选记录来源包；附加包内容在 UI 标注"来自 xxx" |

## 5. 阅读顺序建议

1. 先读 [pack-format.md](./pack-format.md) 了解"包长什么样"；
2. 再读 [composer.md](./composer.md) 了解"包如何生效"；
3. 需要接触选择器扩展时读 [selector-data.md](./selector-data.md)；
4. 落地排期看 [clients-and-roadmap.md](./clients-and-roadmap.md)。
