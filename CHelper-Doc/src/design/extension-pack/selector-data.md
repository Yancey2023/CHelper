# 选择器数据化设计（`selector/` 目录）

> **状态**：V1 已实现（子集，见 §2.1；合成器装载 `selector/*.json` + `TargetSelectorData::init` 数据驱动装配）｜V2 复合参数仍为草案｜把目标选择器从"代码内建"变为"数据驱动 + 可扩展"。

## 2.1 V1 落地状态（实际实现范围，与 §2 差异在此）

已实现（桌面 ComposerTest.SelectorDataVariableAndArgumentRules + test-packs/pack-a 覆盖）：

- **自定义变量**：`variables[].name`（以 `@` 开头）追加进变量表；与内置保留变量（`@a/@e/@p/@r/@s/@n/@initiator`）同名 → **整包拒绝**；
- **自定义简单参数**：`arguments[]` 并入全局参数表（内置变量与自定义变量共用），`operator` 含 `!` 时支持不等于（`=!` 写法）；值类型支持 `BOOLEAN/INTEGER/FLOAT/RELATIVE_FLOAT/STRING/RANGE/NORMAL_ID(key)/NAMESPACE_ID(key)`（引用主包/本包表，合成 CPack 上解析）；与内置参数同名 → 先到先得忽略并告警；
- **覆盖规则与 §5 一致**（变量全局唯一、参数先到先得、ID 类缺 key 整包拒绝、未知值类型整包拒绝）。

未实现（V1 子集边界，遇到时合成告警并忽略）：

- `variables[].arguments` **参数白名单**暂不生效（自定义变量与内置变量共用全参数表）；
- ID 类参数的内联 `contents`（仅支持 `key` 引用表）；
- `canBeList`（逗号多值列表）；`TARGET_SELECTOR` 值类型与其它复杂操作符。

无拓展包时 `selectorVariables/selectorArguments` 为空，装配与旧行为一致（全量回归守护）。

## 1. 现状（必须理解的存量结构）

选择器结构目前**完全由代码内建**，资源目录里没有对应数据：

- `Node::TargetSelectorData`（`CHelper-Core/src/chelper/node/CommandNode.h`）：字段即内置零件——`nodeTargetSelectorVariable`（`@a/@e/@p/@r/@s` 变量）、`nodeWildcard`（`*`）、`nodePlayerName`、`nodeItem`、`nodeFamily/nodeGameMode/nodeSlot`、`nodeEntities`、`nodeHasItemElement/nodeHasItemList1/nodeHasItemList2/nodeHasItem`、`nodeArgument`（通用 `键=值` 参数）、`nodeArguments/nodeOptionalArguments`、`nodeTargetSelectorVariableWithArgument` 等；
- 装配在 `TargetSelectorData::init(cpack)`（`CommandNode.cpp`）+ `NodeInitialization<NodeTargetSelector>::init`（`NodeInitialization.cpp`）：`@变量[参数...]`、`@变量[参数...]` 用 `NodeOr/NodeEqualEntry/NodeList` 等组装成图；
- 解析/补全走通用机制：`TARGET_SELECTOR` 节点的 AST 由这些图产生，参数名（`type/name/dx/dy/dz/hasitem/...`）是 `NodeEqualEntry.equalDatas`（`EqualData{name, description, canUseNotEqual, nodeValue}`）静态列表；
- 值类型：`NORMAL_ID`（gameMode/slot/family 表）、`NAMESPACE_ID`（entity/item 表）、`BOOLEAN`、`RELATIVE_FLOAT`、`INTEGER`、`RANGE`、复合（`hasitem` 内含 `slot`/`item` 子参数，用 `NodeList`/`NodeEntry` 表达）。

结论：选择器是"变量名 + 有序参数定义 + 参数值类型 + 少量复合参数"的结构。完全泛化（任意复合参数）等于发明 DSL；因此设计为**两阶段**：

- **V1（本文档 §2-§5）**：变量表与"简单参数"数据驱动——覆盖绝大多数第三方诉求（自定义 `@x` 变量、给某变量加 `flag=值` 布尔/ID/数值参数、限制可用变量集）；
- **V2（§6）**：复合参数通用化（`hasitem` 一类"参数内含子参数"的声明式表达）。

## 2. `selector/*.json` Schema（V1）

> 注：本节为原始设计 schema；**实际已实现的子集与差异见 §2.1**（以 §2.1 为准）。

每个文件定义一个选择器扩展，命名即内容 id（建议一个包一个文件）：

```jsonc
// selector/serverSelector.json
{
  "id": "serverSelector",

  // 新变量（可选）：在主包内置变量之外追加
  "variables": [
    {
      "name": "@x",                       // 必须以 @ 开头，不得与内置变量重复（冲突整包拒绝）
      "description": "服务器指定玩家",
      "arguments": ["type", "name", "myflag"]  // 允许的参数名白名单（可选，缺省 = 内置全参数）
    }
  ],

  // 参数扩展（可选）：内置变量与自定义变量共用参数表
  "arguments": [
    {
      "name": "myflag",                    // 参数名
      "brief": "服务器标记",
      "description": "是否启用服务器标记",
      "operator": "=",                     // 支持的操作符："=" | "=,!" | "=,<,>,<=,>=" | ...
      "valueType": "BOOLEAN",              // BOOLEAN | NORMAL_ID | NAMESPACE_ID | INTEGER | FLOAT |
                                           // RELATIVE_FLOAT | RANGE | STRING | TARGET_SELECTOR
      "key": "myItem",                     // valueType 为 NORMAL_ID/NAMESPACE_ID 时的候选表
      "contents": [...],                   // 或内联候选（同现有节点 contents）
      "canBeList": true                    // 是否支持逗号分隔多值（进入 NodeList 表达）
    }
  ]
}
```

### 字段语义表

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `id` | 字符串 | 是 | 合成时的叠加键（同 id 后包覆盖前包） |
| `variables[].name` | 字符串 | 是 | 变量 token，含 `@`；`@a/@e/@p/@r/@s` 为保留字（不可覆盖） |
| `variables[].arguments` | 字符串列表 | 否 | 白名单；缺省表示使用该变量"可见参数集"（默认内置全参数 + 本包参数） |
| `arguments[].name` | 字符串 | 是 | 参数名；与内置参数（type/name/x/y/z/dx/dy/dz/rx/ry/l/...）同名的行为见 §5 覆盖规则 |
| `arguments[].operator` | 字符串 | 否 | 操作符集合，"=" 默认；含 `!` 表示支持 `!=`；数值类可声明 `=,<,>,<=,>=` 等 |
| `arguments[].valueType` | 字符串 | 是 | 值节点类型（映射到既有 Node 类型语义） |
| `arguments[].key/contents` | 字符串/列表 | 否 | ID 类值类型的数据来源（合成后按合成 CPack 表解析） |
| `arguments[].canBeList` | 布尔 | 否 | 支持 `x=a,b,c` 多值列表，默认 false |

## 3. 数据驱动装配（V1 改造点）

目标：`TargetSelectorData::init(cpack)` 从 `cpack.selectorData` 读取变量表与参数表，**生成与当前静态等价、且包含扩展项的图**。

改造点清单（实现阶段核对行号）：

1. **数据成员**：`CPack` 增加 selector 数据成员（如 `TargetSelectorData selectorData` 内的可配置表，或独立 `struct SelectorPackData`：变量列表 + 参数定义列表），并提供"内置默认数据"（= 现状静态内容序列化出的默认值，保证无拓展包时行为完全一致）；
2. **装配**（`CommandNode.cpp` `TargetSelectorData::init` 与 `NodeInitialization.cpp`）：
   - 变量：`nodeTargetSelectorVariable` 的候选/映射由"内置 + 扩展"变量表生成（`@a...@s` + `@x`），并记录"变量 → 允许参数集"；
   - 参数：把每个"简单参数"定义（name/operator/valueType/候选）转成 `EqualData`（含 `nodeValue` 节点），沿用 `nodeEqualOrNotEqual`/`NodeList`/`NodeOr` 现有装配模式，把静态 `equalDatas` 替换为"内置 + 扩展"合成列表；
   - 每个变量解析到参数时的可用参数 = 该变量白名单 ∩ 全局参数表（内置变量缺省 = 内置全参数 + 扩展参数，白名单可收缩）；
3. **校验**：参数名与内置冲突时按 §5 规则；valueType/key 引用不存在的表 → 合成期报错（见 composer.md §6）；
4. **来源标注**：扩展参数/变量的 NormalId 条目按 composer.md §4 打 `packName`。

### 装配示例（伪代码）

```cpp
// 伪代码：把参数定义转换为 EqualData（复用 NodeNormalId/NodeNamespaceId/... 运行时构造）
EqualData makeEqualData(const SelectorArgumentDef &def, const CPack &cpack) {
    NodeWithType valueNode = makeValueNode(def.valueType, def.key, def.contents, cpack); // 复用 initNode 语义
    return EqualData(def.name, def.description, /*canUseNotEqual*/ def.operator.contains('!'), valueNode);
}
```

## 4. 兼容性与回归

- **无拓展包时**：`selectorData` = 内置默认数据，装配结果与现状**逐字节等价**（用现有 tests + 快照验证：`@p[type=...,name=...,hasitem=...]` 解析/补全/高亮全量回归）；
- 内置变量 `@a/@e/@p/@r/@s` 为保留字：扩展包不可新增同名变量、不可移除内置变量（只能通过覆盖/白名单控制某变量的参数集）；
- 内置参数名保留：扩展包与内置参数同名时的裁决按 composer.md §2.4 装载顺序（先到先得，谨慎，默认不鼓励）。

## 5. 覆盖与冲突规则

| 情形 | 规则 |
| --- | --- |
| 新变量名 | 追加（变量名全局唯一） |
| 与内置变量同名 | 整包拒绝 |
| 新参数名 | 追加到全局参数表 |
| 与内置参数同名 | 允许覆盖（装载序先到先得），默认建议用新参数名规避 |
| valueType 为 ID 类且 key 缺失 | 合成期整包拒绝 |
| 引用未声明变量（arguments 白名单含未知参数） | 忽略未知参数并告警（宽松）或拒绝（严格，默认宽松+告警） |

## 6. V2：复合参数通用化（远期，不在 V1 承诺）

`hasitem` 类"参数内含子参数树"的声明式表达（草案）：

```jsonc
{
  "name": "myfilter",
  "valueType": "COMPOSITE",
  "fields": [                       // 子参数结构（进入 NodeEntry/NodeList 表达）
    { "name": "slot", "valueType": "INTEGER" },
    { "name": "item", "valueType": "NAMESPACE_ID", "key": "item" }
  ]
}
```

- 需要把 `NodeHasItemElement`/`NodeList` 的现有硬编码装配推广到任意 `fields` 树；
- 复杂度最高，单独排期与回归（P3）。

## 7. 相关代码地图（改动时核对）

| 文件 | 相关点 |
| --- | --- |
| `node/CommandNode.h` | `TargetSelectorData` 字段；`NodeTargetSelector`（isMustPlayer/isOnlyOne/isWildcard…） |
| `node/CommandNode.cpp` | `TargetSelectorData::init` 静态装配 |
| `node/NodeInitialization.cpp` | `NodeInitialization<NodeTargetSelector>::init`（OR 组装）；各类 initNode 的值节点构造模式 |
| `resources/CPack.h/.cpp` | 增加 selector 数据装载（applySelector）与合成合并 |
| `auto_suggestion/AutoSuggestion.cpp` | 无直接特化（选择器建议由内部节点产生），若增加新建议语义再扩展 |
| `tests/` | 选择器解析/补全回归（`CommandContextTest.cpp` 等） |
