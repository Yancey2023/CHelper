# 拓展包格式规范（`.chepack`）

> **状态**：已部分实现——主包分层结构（`main-pack.chepack`）与段内 command/id/json/repeat 已按本文档落地并被引擎/安卓使用；正文个别早期设计已调整（如"core 内做 zip 解析"改为平台层解压后以文件集合传入，见 [segment-loader.md](./segment-loader.md) §4）；JSON 注释支持、拓展包打包/校验工具链与 block/item 大表条目级合并（见 [composer.md](./composer.md) §3.3）已落地；selector 数据化属 P2（见 [clients-and-roadmap.md](./clients-and-roadmap.md)）｜本文件定义拓展包的目录结构与 JSON schema。

## 1. 包形态

拓展包以**目录**为源格式（便于第三方编写与版本管理），**发布/分发**时打成 zip 压缩包并**改扩展名为 `.chepack`**（zip 内容不变，仅扩展名区分）。

```
server-pack/
├─ manifest.json          # 包声明（必填，含合成指令）
├─ command/               # 命令（可选目录，可缺省）
│   └─ <cmd>.json
├─ ID/                    # 候选表（可选目录，可缺省）
│   ├─ item.json
│   ├─ block.json
│   └─ ...
├─ json/                  # JSON 结构定义（可选目录，可缺省）
│   └─ <name>.json
└─ selector/              # 选择器变量/参数定义（可选目录，可缺省）
    └─ <name>.json
```

- 子目录缺省视为无该类型数据；目录可全部为空（空包只做版本占位，允许但告警）；
- 运行时加载：合成器支持两种输入 —— 直接收 zip 字节（推荐，core 内做 zip 解析，安卓端只需传文件字节），或收"路径→JSON 字节"映射（客户端自行解压时用）。两者 API 都提供，细节见 [composer.md](./composer.md)。

## 2. manifest.json（包声明）

沿用 CPack 的 `Manifest` 语义（name/version/versionCode/packId/author/isBasicPack…），并新增合成指令字段：

```jsonc
{
  // —— 以下沿用现有 Manifest 字段 ——
  "name": "示例服务器命令包",
  "description": "为 XX 服务器提供自定义命令与候选补全",
  "version": "1.0.0",
  "versionCode": 1,
  "versionType": "beta",          // 语义沿用；第三方包可约定 "thirdparty"
  "branch": "thirdparty",
  "author": "...",
  "packId": "server-pack-1.0.0",  // 幂等键：同 packId 重复导入视为更新/忽略
  "isBasicPack": false,           // 第三方包必须为 false

  // —— 以下为拓展包新增字段 ——
  "extends": {                    // 可选：合成指令（见 §6 execute 扩展）
    "repeat": { "execute": { "mode": "append" } }
  },
  "priority": 100,                // 信息/预留字段：合成器不读取（保留仅为兼容示例）
  "minCoreVersion": "1.26.0.29"   // 可选：兼容性声明（实现阶段定义比较规则）
}
```

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `name`/`description` | 字符串 | name 是 | 包名（用于 UI 与来源标注）与介绍 |
| `version`/`versionCode` | 字符串/整数 | 是 | 展示版本与程序比较版本 |
| `packId` | 字符串 | 是 | 幂等标识；同 packId 且 versionCode 更低的导入被拒绝 |
| `branch` | 字符串 | 否 | 建议固定 `"thirdparty"`，避免与内置分支冲突语义 |
| `extends` | 对象 | 否 | 合成指令（当前仅 repeat 分支追加） |
| `priority` | 整数 | 否 | 信息/预留字段：合成器不读取；冲突裁决 = 包装载顺序（见 §3） |
| `minCoreVersion` | 字符串 | 否 | 最低核心版本声明 |

## 3. `command/` —— 命令注册

- **一条命令一个 json**；同命令有多个命令头（如 `teleport`/`tp`）**合并为一个 json**，用 `name` 数组表达（与内置 `command/help.json` 的 `["?", "help"]` 同机制）；
- 文件内 schema **完全沿用现有命令格式**（见 [cpack/command.md](../../cpack/command.md)）：`name`/`description`/`syntax`/`node`；
- 命令依赖的候选：
  - 引用**本包或主包已有的** `ID/` 表（`NORMAL_ID.key`/`NAMESPACE_ID.key`）——解析发生在合成 CPack 上，跨包引用天然可用；
  - 或内联 `contents`（自包含，不依赖任何表）。

```jsonc
// command/teleport.json —— 多命令头合并示例
{
  "name": ["teleport", "tp"],
  "description": "传送实体",
  "syntax": ["/teleport <destination: x y z>", "/teleport <destination: target>"],
  "node": {
    "<destination: x y z>":   { "type": "POSITION", "description": "目标坐标" },
    "<destination: target>":  { "type": "TARGET_SELECTOR", "isOnlyOne": true,
                                "isMustPlayer": false, "isMustNPC": false, "isWildcard": false }
  }
}
```

**命名空间冲突策略（当前实现）**：合成按**装载顺序先到先得**——主包段先行，其后各扩展包按调用方传入顺序（安卓端 = 应用内"资源包管理"列表顺序，**靠上优先**，见 [composer.md](./composer.md) §2.4）；后装载包的命令若与已装载命令同名（任一别名），该命令文件**整体忽略**并记入"被覆盖"列表供 UI 提示。`manifest.priority` 为信息/预留字段，不参与裁决。细节见 [composer.md](./composer.md)。

## 4. `ID/` —— 候选表

- 每种候选一个 json，命名即 `id`（如 `item.json`、`block.json`、`entity.json`）；
- 文件 schema **完全沿用现有 id 格式**（见 [cpack/id.md](../../cpack/id.md)）：顶层 `{ "id", "type", "content" }`，`type` ∈ `normal | namespace | block | item`；
- 引用方（命令 json 的 `NORMAL_ID.key`/`NAMESPACE_ID.key`）在合成后统一按表名解析，见 [composer.md](./composer.md) 的合并规则。

```jsonc
// ID/myItem.json —— 自定义物品表（若主包 ID/item.json 存在，作为追加内容合并）
{
  "id": "item",
  "type": "namespace",   // 或 normal / block / item
  "content": [ { "name": "demo:sword", "description": "演示剑" } ]
}
```

## 5. `json/` —— JSON 结构定义

- 复用现有 `NodeJsonElement` 机制（内置示例：`json/rawtext.json`、`json/components.json`），供 `JSON` 类型节点通过 `key` 引用；
- 文件 schema 与 [cpack/cpack.md](../../cpack/cpack.md) 中 json 目录一致：`{ "id", "node": [...], "start": "..." }`。

```jsonc
// json/demoMessage.json —— 自定义 JSON 文本结构
{
  "id": "demoMessage",
  "node": [ /* 复用现有 node 数组定义格式 */ ],
  "start": "..."
}
```

命令中的引用写法（与内置完全一致）：

```jsonc
"<msg: json>": { "type": "JSON", "key": "demoMessage" }
```

## 6. execute 子命令扩展（`extends.repeat`）

约束：拓展包**可以给内置 REPEAT 数据（如 `execute`）追加子命令分支**，**不能注册新的 REPEAT 嵌套结构**（不提供 repeat 数据目录）。

写法：片段文件放在 **`extensions/`** 子目录（**不放 `command/`**，避免被当作普通命令注册/被现有关卡误载），并在 manifest 的 `extends.repeat.<id>.file` 中声明：

```jsonc
// manifest.json
"extends": {
  "repeat": {
    "execute": { "mode": "append", "file": "extensions/execute-addon.json" }
  }
}
```

```jsonc
// extensions/execute-addon.json —— 仅新增分支
{
  "name": ["execute"],
  "description": "（被合并，不参与命令名匹配）",
  "syntax": ["/execute <addon: ExecuteChainedAddon>"],
  "node": {
    "<addon: ExecuteChainedAddon>": {
      "type": "REPEAT",
      "key": "execute",            // 指向目标 REPEAT 数据
      "description": "附加子命令",
      "branches": [                // 新增字段：仅在此模式下使用，见下
        {
          "isEnd": true,
          "nodes": [
            { "type": "TEXT", "data": { "name": "myaction", "description": "服务器自定义动作" } },
            { "type": "NORMAL_ID", "key": "myItem", "ignoreError": true, "contents": [...] }
          ]
        }
      ]
    }
  }
}
```

合成规则（见 [composer.md](./composer.md) §合并规则）：

- 合成器按 `manifest.extends.repeat.<id>` 找到片段文件，把该文件 `branches` 的每个分支（`nodes` 数组）解析为 `NodeAnd(Wrapped...)`，**追加到主包 `repeatNodes[<id>]` 的 `repeatNodes`/`isEnd` 尾部**，随后统一重新物化；
- 追加分支只允许使用"表达式"节点（TEXT/NORMAL_ID/NAMESPACE_ID/INTEGER/FLOAT/BOOLEAN/STRING/POSITION/RELATIVE_FLOAT/TARGET_SELECTOR/JSON/COMMAND_NAME…），不允许嵌套 REPEAT 或引用不存在的数据；
- 片段文件的 `name`/`syntax` 不参与命令名匹配；`branches` 缺失时合成器整包拒绝（见 composer.md §6）。

## 7. 节点行为声明字段（行为层 JSON 化）

行为层 JSON 化**限定为"节点上的可选声明字段"**，由补全解释器读取；不改变既有类型的默认行为（回归零风险）。新增字段（草案，实现时在 [pack-format 上级 node 规范](../../cpack/node.md) 同步登记）：

```jsonc
"<arg>": {
  "type": "NORMAL_ID",
  "key": "myItem",
  "suggest": {                      // 可选：覆盖默认匹配方式
    "filter": "prefix"              // prefix | contains | description | all（默认三档）
  },
  "noSuggestion": true              // 可选：该位置不产出建议
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `suggest.filter` | 字符串 | 覆盖默认"前缀 > 包含 > 描述"匹配策略（仅对 ID/字面量类节点生效） |
| `noSuggestion` | 布尔 | 抑制该节点位置的建议（等价于现有 `NodeOr.noSuggestion` 的声明式表达） |

远期（P3）可扩展的声明类型（不在 V1 承诺内）：

```jsonc
{ "type": "SUGGEST_LIST", "contents": [{ "name": "...", "description": "..." }] }  // 纯候选列表节点
```

## 8. 包级规则与限制汇总

| 规则 | 说明 |
| --- | --- |
| JSON 注释 | 包内 JSON（manifest/command/ID/json/extensions）允许写 `//` 行注释与 `/* */` 块注释，装载时引擎与校验工具自动忽略（字符串内的标记不处理；注释替换为空格避免粘连）。模板见 `CHelper-Resource/templates/extension-pack` |
| 目录/文件命名 | 建议全小写；`ID/` 目录名大小写由合成器统一（内置资源为 `id/`，第三方书写两种均可，打包工具规范化） |
| 自包含优先 | 依赖内置表可以，但推荐自带 `contents`/`ID/`，避免版本漂移 |
| 禁止 | 修改/删除主包已有命令（只能追加或整体覆盖同名命令）；注册新 REPEAT 数据；覆盖主包 `manifest` |
| 引用校验 | 引用不存在的 `key`（normalIds/namespaceIds/json 结构）在合成/物化阶段报错并**整包拒绝**（不半加载） |
| 来源标注 | 包内所有命令与候选自动标记来源 `manifest.name`，UI 展示；来源字段不进 cpack 二进制（见 composer.md） |

## 9. 主包（内置补全包）与版本选择

主包 = 把**当前所有原版版本的完整数据**（命令/ID/方块/物品/json 结构/repeat）放进**一个** `.chepack`，由"版本选择"决定运行时启用哪些内容——替代现状"每个分支一个 .cpack 文件、整体替换"的分发方式。

**分层存储**：六个段（beta/release/netease × vanilla/experiment）中**相同内容只存一份**，不同内容放到版本/分支差异层，运行时由"段装载器（版本控制器）"按层组装出启用段的完整视图。实测：633 个源文件 → 分层后 190 个（省 70%），zip 约 0.5 MB。

产物：

- **目录源（正式产物）**：`CHelper-Resource/main-pack/`——分层结构，可审阅，引擎可直接目录装载；
- **单文件**：`CHelper-Resource/generated/main-packs/main-pack.chepack`（`tools/build_main_pack.mjs --zip` 生成，分发/下载用）。

均由 `CHelper-Resource/tools/build_main_pack.mjs` 从 `resources/{beta,release,netease}/{vanilla,experiment}` 生成（**与资源同源**，改资源后重跑即同步；工具自带**全量装载自检**，保证合并结果与源逐文件一致）。

### 9.1 目录结构（分层）

```
main-pack/
├─ manifest.json                    # 聚合清单：layout:"layered" + segments 索引
├─ shared/                          # 全局公共：六段内容完全相同的文件（command/id/json/repeat 相对路径不变）
└─ versions/<versionType>/
   ├─ shared/                       # 该版本下 vanilla/experiment 共有（与其它版本不同）
   ├─ vanilla/                      # 段差异（该段独有/与同版本另一分支不同；含段 manifest.json）
   └─ experiment/
```

- 归层规则（简单可预期，由工具按文件内容 hash 自动判定）：
  1. 六段都有且 hash 全同 → `shared/`；
  2. 某版本两分支 hash 相同 → `versions/<vt>/shared/`（覆盖该 vt 两段）；
  3. 其余出现该文件的段 → 各自 `versions/<vt>/<branch>/`（段差异，每段保留自己那份）；
- 每段 `manifest.json`（packId/version 不同）必定落在该段差异层；
- 段内允许**任意数据子目录**（如 `text/` 放翻译词典），不限于 command/id/json/repeat：归层与装载按"文件相对路径"通用处理，子目录名不影响分层（配合 `tools/build_main_pack.mjs` 的整树扫描；见 [rawtext-convergence.md](./rawtext-convergence.md)）；
- 段内的 `repeat/execute.json` 等源数据随层保留 → execute 分支扩展（§6）作用在"组装后的段视图"上。

### 9.2 装载规则（版本控制器 / 段装载器）

启用段 `<vt>/<branch>` 的完整视图 = **后层覆盖同名文件**：

```
shared/  ∪  versions/<vt>/shared/  ∪  versions/<vt>/<branch>/
```

- 装载器是引擎中**唯一感知分层**的模块：组装出段视图后，其余逻辑（合成/解析/补全）与"全量段"完全一致，不感知分层；
- 每次主包构建都会对六个段做**全量自检**：按该规则组装后每个文件 hash 必须与 `resources/<vt>/<branch>/` 对应文件一致。

### 9.3 版本选择语义

| 项 | 说明 |
| --- | --- |
| 启用段 | 运行时配置：如 `["beta/vanilla"]`（六选一）。引擎按 §9.2 组装该段视图作为基础数据源参与合成 |
| 切换成本 | 只改配置 + 重新合成，**不换文件、不重新下载**（主包文件固定） |
| 与现状等价 | 组装后的段视图 ≈ 现在的单一分支 .cpack 效果（需回归验证，见 clients-and-roadmap §5） |
| 多段（远期） | 若未来需要同版本 vanilla+experiment 共存，按 composer 冲突策略合并；V1 维持单选与现状一致 |

### 9.4 未来维护须知

- **更新资源**：官方版本数据变更/新增版本 → 只改 `resources/`，重跑 `tools/build_main_pack.mjs --zip` 即可（幂等重建：先清空 `main-pack/` 再生成），无需手工移动分层文件；
- **正确性保障**：构建时全量自检（合并视图 vs 源逐文件 hash）是分层安全的护栏，任何归层/装载错误都会在构建期被发现，而不是运行期；
- **认知成本**：日常修改以 `resources/` 为准；`main-pack/` 是生成产物，不要手工编辑；需要核对"某文件在哪层"时，运行构建工具的归层统计即可；
- **引擎侧**：分层只出现在"段装载器"一处（§9.2），其余模块不感知；若未来放弃分层（回到全量段），只需段装载器直接读全量目录、其余不变。

### 9.5 与 .cpack 预编译形态的关系

- 主包 `.chepack`（zip/json）是**源/合成输入形态**：引擎按 §9.2 组装段视图并保留全部可合并源数据；
- `.cpack`（二进制，现有生成器产出）可作为引擎的**预编译缓存形态**（内容等价、加载更快）；V1 引擎先支持 `.chepack`/目录，`.cpack` 作为后续优化。

## 10. 与现有文档的关系

- 本文件定义的 `command/ID/json` 目录即现有资源目录的**子集复用**，因此第三方可直接参考 [cpack 文档](../../cpack/cpack.md)；
- 选择器目录是新概念，schema 见 [selector-data.md](./selector-data.md)。
