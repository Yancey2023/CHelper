# 合成器（Composer）设计

> **状态**：核心（command/id/json/repeat 合并 + block/item 大表条目级合并，见 §3.3）已实现并接入安卓（`CHelper-Core/src/chelper/extension/Composer.h`，见 [p0-tasks.md](./p0-tasks.md) T4）；正文为设计稿，个别早期选择已变更（如"core 内解析 zip/miniz"改为平台层解压后以文件集合传入，见 [segment-loader.md](./segment-loader.md) §4；**冲突裁决由草案的"`priority` 大者生效"改为"装载顺序先到先得（主包段 → 拓展包按应用内列表顺序）"，`manifest.priority` 为信息/预留字段**，见 §2.4/§3.1）；selector 数据化属 P2（见 [selector-data.md](./selector-data.md)）｜负责"主包 + 已启用拓展包 → 单一只读合成 CPack"。

## 1. 职责与位置

```
已启用包集合（含列表顺序）
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│ Composer（core 新模块，建议放 CHelper-Core/src/chelper/  │
│  extension/ 或复用 resources/ 命名空间）                 │
│  · 解包 .chepack（zip）→ json 字节集                     │
│  · 校验 manifest 与内容引用                               │
│  · 把各包数据合并进"同一个 CPack 加载流程"               │
│  · afterApply() 一次物化                                  │
│  · 构建来源索引（命令/候选 → 包名）                      │
└─────────────────────────────────────────────────────────┘
        │
        ▼
shared_ptr<const CPack>（合成 CPack）── 供 CHelperCore/CommandContext 使用
```

设计约束（继承自现有架构，见 [overview.md](./overview.md) §3.1）：

- 合成 CPack 是**唯一驻留对象**，只读、可多线程共享；
- **不在合成之后修补任何节点**（物化产物全部由 afterApply 生成，指针指向合成 CPack 自身内存，生命周期 = 合成 CPack 的 shared_ptr）；
- 下游（Parser/AutoSuggestion/高亮/结构/Linter）**零改动**；
- 包集合变化 → 重新合成（幂等），旧合成 CPack 由 shared_ptr 自然释放（需等待引用它的 CommandContext 释放，语义与现状一致）。

## 2. 加载与合成步骤

1. **输入归一**：输入 = **主包**（内置补全包 `main-pack.chepack`，或兼容目录/预编译 `.cpack`）+ **启用段列表**（如 `["beta/vanilla"]`）+ 各拓展包。主包与拓展包接受同样的 `zip 字节`（core 内解析，推荐）或 `路径→字节` 映射（客户端已解压）；
2. **解包/解析**：读 `manifest.json`；主包按 `layout`（当前 `"layered"`）与 `versions/` 结构处理——**段装载器**按 `shared → versions/<vt>/shared → versions/<vt>/<branch>` 组装启用段视图（后层覆盖同名，见 [pack-format.md](./pack-format.md) §9.2），段内目录 `command/id/json/repeat` 与拓展包同 schema；拓展包按目录归类 `command/*.json`、`ID/*.json`、`json/*.json`、`selector/*.json`；执行基本结构校验（JSON 可解析、manifest 字段齐全；主包段 `isBasicPack==true`，第三方拓展包必须 `false`）；
3. **去重**：按 `packId` 去重（同 packId 已启用 → 覆盖/忽略由上层包管理决定，合成器只报结果）；
4. **定序（当前实现）**：主包段视图先行，扩展包**按调用方传入顺序**装载（安卓端 = 应用内"资源包管理"列表顺序，**靠上优先**）；该顺序即**冲突裁决顺序**（先到先得）。草案曾设计按 `manifest.priority` 降序，已废弃——该字段为信息/预留字段，合成器不读取；
5. **合并数据源**（见 §3）：把主包与各拓展包的 command/id/json/repeat(扩展分支)/selector 汇入同一份"待物化数据"；
6. **物化**：调用一次现有物化逻辑（等价于 `CPack::afterApply` 的流程：selector 装配 → jsonNodes init → repeat 物化 → 各命令 initNode → commands 排序 → mainNode 构建）；
7. **来源索引**：合成完成后构建 `命令名(别名) → 包名`、候选来源标记（见 §4）；
8. **返回** `shared_ptr<const CPack>` + 元数据（各包生效状态、被覆盖列表、告警）。

实现提示：`CPack` 目前的 apply 系列（`applyId/applyJson/applyRepeat/applyCommand`）与 `afterApply` 是 private 且构造即物化；需要**小重构**——把"装载（apply*）+ 物化（afterApply）"从构造函数中拆出为可累积调用的加载器/builder（保持现有三个构造入口行为不变），合成器复用它。

## 3. 合并规则

以下规则以"合成 CPack 自身的表"为最终形态，物化时 `initNode` 的 key 解析全部落在合成表上（天然支持跨包引用）。

### 3.1 命令（commands）

| 情形 | 规则 |
| --- | --- |
| 命令名与已装载命令不同 | 直接追加 |
| 命令名冲突（任一别名命中） | 按 §2.4 装载序裁决：**先到先得**，后装载包的命令**整体忽略**，记入"被覆盖"列表（来源索引保留记录供 UI 提示） |
| `extends.repeat` 片段命令（见 [pack-format.md](./pack-format.md) §6） | 不进入 commands，转入 §3.4 repeat 合并 |

命令名匹配使用**任一别名**（name 数组），冲突判定按别名全集。

### 3.2 ID 候选（normalIds / namespaceIds）

| 情形 | 规则 |
| --- | --- |
| 表名（如 `entity`）不存在 | 直接新增（key → content 列表） |
| 表名已存在 | **追加**：把后包条目接在前包条目之后；候选展示层已有 XXH64 去重（`Suggestions::addSuggestion`），同 name 不同 description 的条目保留 |
| 条目覆盖声明 | 可选：条目带 `"override": true` 时替换同 name 前包条目（实现阶段决定是否支持） |

### 3.3 block / item 大表

`BlockIds`/`ItemIds` 是复合大对象（含方块状态、属性描述表等）。**条目级合并已实现**：

- **block（`ID/block.json`，与内置 block.json 同构：`content` 为对象）**：
  - `blockStateValues`：条目键 =（`idNamespace` 缺省视为 `minecraft` + `name`）。与已有条目同键 → **忽略并记告警（先到先得：主包段先装载，其后拓展包按列表序）**；新方块追加并打来源标记（供 `Suggestion.packName` 徽标）。已追加方块的解析/补全与内置方块一致（`/setblock ~ ~ ~ <方块>[状态]`）；
  - `blockPropertyDescriptions`：`common` 按 `propertyName`、`block` 分组按"方块集相交 + 属性名相同"先到先得合并（冲突属性忽略并告警），组随方块集归属；
  - ⚠️ 约束（合成期不检查、装配/解析期强制）：某方块 `properties` 里声明的每个属性，合并后必须能在（合并后的）`common` 或 `block` 描述里查到，否则解析该方块状态时 `BlockPropertyDescriptions::getPropertyDescription` 抛异常——第三方自定义方块**必须自带属性描述表**；复用内置属性名时可省略；
- **item（`ID/item.json`，与内置 item.json 同构：`content` 为 `{name, description}` 列表）**：条目按（`idNamespace` 缺省 `minecraft` + `name`）先到先得合并——同键忽略并告警，新条目追加并打来源标记。与 `pitem`（ITEM 节点）等引用方即时生效。
- 示例：`test-packs/pack-a`（自定义方块 `demo:demo_machine` + 自定义物品）与 `examples/demo-server-pack/ID/block.json`。
- 序列化/二进制格式不动（合并发生在合成期，物化走既有 afterApply）。

### 3.4 json 结构（jsonNodes）

按 `NodeJsonElement.id`（命令里 `JSON.key` 的匹配键）匹配：init 阶段**取装载序首个命中**（先到先得，装载序同 §2.4），后续同 id 元素不生效。物化时 `NodeInitialization<NodeJson>` 遍历合成 jsonNodes 匹配首个命中，无需改动。

### 3.5 repeat（execute 子命令扩展）

- 主包二进制保留 `repeatNodeData`（RepeatData 数组），是扩展的前提（已验证：`CPack` 二进制构造反序列化 `repeatNodeData` 后统一物化）；
- 对每个 `extends.repeat.<id>`（当前仅 `execute`，`file` 指向 `extensions/` 下的片段文件）：
  1. 在合成 repeatNodeData 中定位同 id 的 RepeatData；
  2. 读取片段文件，把其 `branches` 的每个分支（`nodes` 数组）**追加**到该 RepeatData 的 `repeatNodes`，并把分支的 `isEnd` 追加到 `isEnd` 数组；
  3. 缺失 id / 片段文件或含非法节点（如嵌套 REPEAT）→ 整包拒绝（见 §6）；
- 物化阶段由现有 afterApply 逻辑生成扩展后的 OR 分支图，`Parser<NodeRepeat>` 的 `isEnd[whichBest]` 映射因数组同步追加而正确。

### 3.6 selector 数据

见 [selector-data.md](./selector-data.md)。合成器把各包 `selector/*.json` 合并为 `cpack.selectorData`（内置默认数据 + 扩展数据叠加），随后 `TargetSelectorData::init` 数据驱动装配。

## 4. 来源标注

### 4.1 目标

附加包的**命令与候选**在 UI 标注"来自 xxx 包"。命令名建议、参数候选建议都可能在一条补全列表里混排，必须逐条可溯源。

### 4.2 数据结构（草案）

- `NormalId` / `NamespaceId` 增加**内存态**可选字段（如 `std::optional<std::u16string> packName`）：
  - 序列化约束：**不进 cpack 二进制、不作为持久化字段**（NormalId 被大量序列化，加持久化字段会破坏既有包格式兼容；改为内存字段 + 合成时打标）；
  - id 表条目在**合并阶段**由合成器统一打标（条目来自哪个包）；
  - 命令名来源：来源索引 `map<命令别名, 包名>`（NodeCommand 特化产出建议时查询，或合成时给命令对应的 NormalId 打标——实现取更简单者）；
- `Suggestion` 对象透传：`Suggestion.content` 已是 `NormalId`，来源字段随 content 一并携带；JNI 输出 `Suggestion` 时新增 `packName` 字段（**安卓协议小改**，兼容策略：旧端忽略新字段、新端对缺省值视为内置包）。

### 4.3 UI 呈现

建议列表项追加来源徽标/文字（如 `entity · 来自 XX 服务器包`）；仅附加包内容显示，内置包不显示或显示"内置"。

## 5. API 草案

> 主包分层装载的接口（`MainPack::open` / `loadSegment` / 段视图）见 [segment-loader.md](./segment-loader.md)；合成器直接消费其输出的段视图文件集合。

### 5.1 C++（core）

```cpp
namespace CHelper::Extension {

    // 单个拓展包（已解包或未解包）
    struct ExtensionPackData {
        // zip 字节（未解包）与 path->bytes（已解包）二选一
        std::vector<uint8_t> zipBytes;                       // 未解包时填充
        std::vector<std::pair<std::string, std::vector<uint8_t>>> files; // 已解包时填充
    };

    struct ComposeResult {
        std::shared_ptr<const CPack> cpack;                  // 合成 CPack（启用段主包+全部已启用包）
        // 元数据：每包/每段 生效命令数/被覆盖命令列表/告警 等（供 UI）
    };

    // 主包（内置补全包，含版本段）+ 启用段 + 已启用拓展包 → 合成
    // enabledSegments 示例：{"beta/vanilla"}（六段单选；见 pack-format.md §9）
    ComposeResult compose(
            const std::filesystem::path &mainPackPath,       // main-pack.chepack（或兼容目录/.cpack）
            const std::vector<std::string> &enabledSegments,
            const std::vector<ExtensionPackData> &packs,
            const ComposeOptions &options = {});
}
```

- zip 解析：引入单头库 `miniz`（public domain）或等价实现；只读、不落盘；
- 生成器侧（CHelperResourceGenerator）同步提供 `目录 → .chepack`（zip）输出与校验器（见 [clients-and-roadmap.md](./clients-and-roadmap.md)）。

### 5.2 Android JNI（P0 唯一导出面）

在 `CHelperAndroid.cpp` 增加（命名与 Kotlin 包装层对应，签名见 [segment-loader.md](./segment-loader.md) §5）：

- `Java_..._MainPack_open0(byte[] zipBytes) → long mainPackPtr`（主包常驻）；
- `Java_..._MainPack_listSegments0(long mainPackPtr) → SegmentMeta[]`；
- `Java_..._CHelperCore_compose0(long mainPackPtr, String[] enabledSegments, byte[][] extensionPackZips) → long 合成 core 句柄`；
- `Suggestion` 序列化新增 `packName` 字段。

### 5.3 Web / Qt（暂不做，维持现状）

**暂不做**：Web/Qt 端维持现状（继续加载旧的六份 `.cpack`、旧分支切换），不引入合成器/主包/拓展包 API；若未来要做，再按本文档与 segment-loader.md 补 wasm/桌面导出。

### 5.4 兼容性（安卓）

- 老版本核心不识别 `.chepack`/合成 API → 安卓端按核心能力隐藏导入入口；
- 合成 API 全部新增，不改变任何现有 JNI 导出签名。

## 6. 错误处理与安全

| 场景 | 行为 |
| --- | --- |
| zip 损坏/不是 zip | 整包拒绝，报错信息上抛 UI |
| manifest 缺失/字段非法 | 整包拒绝 |
| 引用不存在（normalIds/namespaceIds/json key） | **整包拒绝**（不半加载，避免运行时 initNode 抛异常） |
| 片段命令非法（extends.repeat 目标不存在/含 REPEAT 嵌套） | 整包拒绝 |
| zip 炸弹/超限 | 解析时限制解压总大小与文件数（如 64MB/1024 文件，可配） |
| 重复 packId | 后者忽略（由上层包管理提示"已安装"） |
| 合成失败 | 保持旧合成 CPack 不动，UI 提示失败原因（不降级半成品） |

## 7. 性能与生命周期

- 合成只发生在"包集合变更"：毫秒~百毫秒级（取决于扩展包大小；主包 1-2MB 级），UI loading 即可；
- 合成 CPack 与 CommandContext 的生命周期沿用现有 `shared_ptr<const CPack>` 纪律：`CHelperCore`/ComposeResult 持有它，上下文共享；
- 重新合成前需保证旧上下文释放或仍可安全并存（两者都只读，可并存；旧 core 释放后旧上下文仍可用——与现状"core close 后 context 可用"一致）；
- 安卓内存：主包解包常驻 + 合成 CPack 大表（block.json 等）驻留，监控点（P1/P2）。
