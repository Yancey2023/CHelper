# 拓展包开发示例：逐字段走读（Demo 服务器命令包）

> **状态**：教学文档｜配套真实示例：`CHelper-Resource/examples/demo-server-pack`
> 本页把示例包里**每个 json 文件的每个字段**讲一遍：字段含义、取值约束、被核心哪个模块消费、最终影响什么效果。
> 示例文件是教学视角的注释版（`//` 注释**不属于合法 json**，仅用于讲解；正式文件见示例包目录）。
> 规范速查另见：[/cpack/manifest](../../cpack/manifest.md)、[/cpack/command](../../cpack/command.md)、[/cpack/id](../../cpack/id.md)、[/cpack/node](../../cpack/node.md)。

## 0. 模块地图：字段在哪被消费

示例包所有字段最终由核心这些模块消费：

| 代号 | 模块（核心代码） | 做什么 |
| --- | --- | --- |
| 【装载】 | `resources/CPack.cpp`（`applyCommand/applyId/applyJson...`） | 从 json 装载数据进 CPack |
| 【结构】 | `serialization/Serialization.h` `Codec<NodePerCommand>` | 把 `command/*.json` 变成语法图：syntax 数组建 trie、node keys 作 token 词典 |
| 【物化】 | `node/NodeInitialization.cpp` `initNode` | 展开节点内部引用：`key`→候选表、`JSON.key`→json 结构、`REPEAT.key`→repeat 数据、TARGET_SELECTOR 装配 |
| 【解析】 | `parser/Parser.cpp` | 把用户输入的命令文本按语法图解析成 AST |
| 【补全】 | `auto_suggestion/AutoSuggestion.cpp` | 依据 AST 与光标位置产出建议（命令名/候选/符号/空格…） |
| 【提示】 | `command_structure/` `parameter_hint/` `syntax_highlight/` | 结构提示文本、参数注释、语法高亮（同走 AST） |
| 【合成】（规划） | Extension Composer | 多包合并、冲突裁决、来源标注（见 [composer.md](./composer.md)） |
| 【选择器】（已落地 V1 子集） | `TargetSelectorData` 数据驱动化 | `selector/*.json` 的自定义变量/简单参数已生效（见 [selector-data.md](./selector-data.md) §2.1） |

---

## 1. `manifest.json` —— 包的身份与合成指令

```jsonc
{
  // 【装载】name 显示在包管理列表与"来源标注"（如 补全项显示"来自 Demo 服务器命令包"）
  "name": "Demo 服务器命令包",

  // 【装载】description 是包的介绍，显示在导入确认页
  "description": "演示 CHelper 拓展包：自定义命令、候选表与 JSON 结构……",

  // 【装载】展示用版本号；versionCode 是程序比较版本（同 packId 时更高 versionCode 视为更新）
  "version": "0.1.0",
  "versionCode": 1,

  // 【装载】沿用内置包的语义："beta/release" 对应基岩版命令集年代；第三方包建议固定 "thirdparty"
  "versionType": "beta",
  "branch": "thirdparty",

  // 【装载】作者名，显示在包列表
  "author": "CHelper",

  // 【装载】幂等键：同 packId 重复导入 = 更新/忽略，而不是叠加两份
  "packId": "demo-server-pack-0.1.0",

  // 【装载】第三方包必须 false。true 只允许内置资源使用（包级安全开关）
  "isBasicPack": false,

  // 【预留】信息/预留字段：合成器不读取；冲突裁决 = 装载顺序（应用内"资源包管理"列表，靠上优先）
  "priority": 100,

  // 【合成】合成指令：允许把 extensions/ 片段里的分支追加进内置 execute 的 REPEAT 数据
  "extends": {
    "repeat": {
      "execute": {            // 目标 REPEAT 数据 id（只能扩展已存在的，不能新建）
        "mode": "append",     // 追加模式（当前唯一取值）
        "file": "extensions/execute-addon.json"  // 片段文件路径（放 extensions/，不进 command/）
      }
    }
  }
}
```

| 字段 | 类型 | 必填 | 被谁消费 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | 字符串 | ✅ | 装载/UI | 包显示名；来源标注用这个名字 |
| `description` | 字符串 | 否 | UI | 导入确认页显示 |
| `version`/`versionCode` | 字符串/整数 | ✅ | 装载 | 版本展示与程序比较 |
| `packId` | 字符串 | ✅ | 装载 | 幂等标识 |
| `versionType`/`branch` | 字符串 | 否 | 装载/管理 | 建议 `"beta"/"thirdparty"` |
| `author` | 字符串 | 否 | UI | 包作者 |
| `isBasicPack` | 布尔 | 否 | 装载 | 第三方必须 `false` |
| `priority` | 整数 | 否 | 预留 | 信息/预留字段：合成不读取；生效顺序 = 应用内"资源包管理"列表顺序（靠上优先） |
| `extends.repeat.<id>` | 对象 | 否 | 合成 | execute 子命令扩展指令 |

---

## 2. `command/kit.json` —— 一条完整命令

### 2.1 顶层：命令身份与语法骨架

```jsonc
{
  // 【装载】命令名（别名数组）。这里只有 "kit" 一个命令头。
  //   —— 同命令多个命令头（如 teleport/tp）合并到一个文件、写进同一个数组即可。
  "name": ["kit"],

  // 【提示/展示】命令的介绍：
  //   - 命令名补全列表里显示
  //   - /help 结构提示等位置使用
  "description": "领取礼包（示例服务器命令）",

  // 【结构】★ 语法图的事实来源（不是给人看的花架子！）
  //   每条字符串 = 一种语法分支。写法："/命令名 " 后跟占位 token 序列：
  //     <xxx: Type> 必填参数    [xxx: Type] 可选参数    纯文本 = 字面量关键字
  //   示例两行 = 两个分支：
  //     A. /kit <礼包名> [玩家]     B. /kit list（字面量关键字）
  "syntax": [
    "/kit <kitName: Kit> [player: target]",
    "/kit list"
  ],

  // 【结构】节点定义字典：键 = 占位 token 原文（与 syntax 里完全一致，含括号），
  //   值 = 该参数的类型与属性定义。syntax 里出现的每个 token 都必须在这里有键，
  //   否则加载阶段就会报 "unknown syntax token"。
  "node": {
    // —— 2.2 必填 ID 表参数 ——
    "<kitName: Kit>": { ... },
    // —— 2.3 可选目标选择器 ——
    "[player: target]": { ... },
    // —— 2.4 字面量关键字参数 ——
    "list": { ... }
  }
}
```

### 2.2 `<kitName: Kit>` —— NORMAL_ID（引用 ID 表）

```jsonc
"<kitName: Kit>": {
  "type": "NORMAL_ID",      // 语义：从一张候选名表里选一个 ID（普通标识符）
  "brief": "礼包名",         // 【提示】参数提示的短名（紧凑场景）
  "description": "要领取的礼包", // 【提示】参数提示/结构文本的说明文字

  // 【物化】候选来源二选一：
  //   key       = 引用 ID/ 目录里 id 字段相同的表（本例 ID/kit.json）
  //   contents  = 内联候选（自包含，不依赖任何表）
  //   物化时找不到 key 对应表 → 该包加载失败（整包拒绝）
  "key": "kit"
}
```

`NORMAL_ID` 在核心的完整效果：

| 环节 | 效果 |
| --- | --- |
| 【解析】 | 输入必须是表内某个 name（或数字串），否则 ID 错误提示 |
| 【补全】 | 光标处列出表内候选；匹配按"前缀 > 包含 > 描述"三档排序（`AutoSuggestion<NodeNormalId>`） |
| 【提示】 | 参数提示显示 brief/description |
| 忽略错误 | 加 `"ignoreError": true` 可让"不在表内"不再报错（如服务器会动态校验的项） |

### 2.3 `[player: target]` —— TARGET_SELECTOR（目标选择器）

```jsonc
"[player: target]": {
  "type": "TARGET_SELECTOR", // 语义：接受 @a/@e/@p/@r/@s、玩家名或 *
  "description": "礼包发放对象，默认为自己",
  // 以下四个字段是该位置的语义约束声明：
  "isOnlyOne": false,   // 是否只允许一个目标（@a 这类多目标变量会报错）
  "isMustPlayer": true, // 是否必须是玩家（@e 这类会报错）
  "isMustNPC": false,   // 是否必须是 NPC
  "isWildcard": false   // 是否允许 *（true 时解析会额外允许 * 分支）
}
```

方括号 `[...]` 表示**可选**：分支 A 里玩家可以省略（`/kit starter` 合法）。选择器内部语法（变量、`参数=值`、`hasitem` 等）由核心内建的 `TargetSelectorData` 处理，这里只需声明约束。

### 2.4 `list` —— TEXT（字面量关键字）

```jsonc
"list": {
  "type": "TEXT",       // 语义：必须原样输入这一个词（字面量）
  "description": "列出本服务器全部礼包",
  "data": {              // 【装载】字面量的内容定义（固定结构）
    "name": "list",      //   要匹配的原文
    "description": "列出本服务器全部礼包"  //   该字面量的说明
  }
}
```

`TEXT` 效果：【解析】输入必须是 `data.name`，否则报"找不到含义 -> xxx"；【补全】输入与 name/描述前缀匹配时给出这个字面量候选（`AutoSuggestion<NodeText>`）。

> 分支 A 与 B 是**互斥的两个语法**：`/kit list` 不会把 `list` 当成礼包名（list 分支不经过 NORMAL_ID 节点）。

---

## 3. `command/hub.json` —— 多命令头 + 无参数分支

```jsonc
{
  // 【装载】两个命令头：/hub 与 /spawn 完全等效（与内置 teleport/tp 一个机制）
  "name": ["hub", "spawn"],
  "description": "传送回大厅（双命令头示例：/hub 与 /spawn 等效）",

  "syntax": [
    "/hub",                    // 分支 1：无参数 → 命令名后立即允许结束（相当于可选空参数）
    "/hub <server: string>"    // 分支 2：带一个必填字符串参数
  ],
  "node": {
    "<server: string>": {
      "type": "STRING",        // 语义：一个字符串参数
      "brief": "目标大厅",
      "description": "要传送到的子服务器大厅名"
      // STRING 还有三个可选字段（本例未用）：
      //   canContainSpace  true = 允许含空格（输入需加双引号，编辑器提示引号）
      //   allowMissingString true = 允许为空
      //   ignoreLater      true = 吞掉后面所有内容（如 say 命令）
    }
  }
}
```

效果：输入 `/hub` 或 `/hub s1` 均合法；输入 `/hub ` 时光标处会补全提示（若 server 无候选表则只有空格/符号类建议）。

---

## 4. `ID/kit.json` —— normal 候选表

```jsonc
{
  // 【装载】表 id：命令里 NORMAL_ID/NAMESPACE_ID 的 key 通过它引用
  "id": "kit",
  // 【装载】表类型：normal = 普通 ID 表（补全候选 + 校验）
  "type": "normal",
  // 【装载】候选条目：name 是候选值，description 是补全列表里的说明
  "content": [
    { "name": "starter", "description": "新手礼包" },
    { "name": "diamond", "description": "钻石礼包" },
    { "name": "legend",  "description": "传说礼包" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 表名；与命令里 `key` 精确匹配 |
| `type` | `normal`（本示例）/ `namespace` / `block` / `item`（大表语义不同，见下节） |
| `content[].name` | 候选值本身 |
| `content[].description` | 补全列表中的说明文字 |

消费：【物化】`cpack.getNormalId("kit")` 绑定到节点；【补全】`/kit ` 后列 `starter/diamond/legend`，输入 `/kit dia` 前缀过滤出 `diamond`。

---

## 5. `ID/entity.json` —— namespace 表（同名追加示例）

```jsonc
{
  "id": "entity",
  // namespace = 带命名空间的 ID（默认命名空间 minecraft 可省略不写）
  "type": "namespace",
  "content": [
    // 无 idNamespace 字段 → 视为默认 minecraft 命名空间：
    // 完整写法 minecraft:demo_guard，但编辑器/游戏输入里省略 minecraft: 也匹配
    { "name": "demo_guard", "description": "示例守卫（服务器自定义实体）" },

    // 带 idNamespace → 完整 ID 是 demo:demo_pet，不能省略命名空间输入
    { "name": "demo_pet", "idNamespace": "demo", "description": "示例宠物（demo 命名空间）" }
  ]
}
```

要点：

- 表 id 与内置包的 `entity` 同名 → 【合成】（规划）把条目**追加**到内置表尾部，达到"给 summon 等命令增加自定义实体候选"的效果；若没有合成器而单独加载，本文件自成一个 entity 表；
- `namespace` 表的条目继承 `normal` 的全部字段，另加可选 `idNamespace`；
- 消费差异（`AutoSuggestion<NodeNamespaceId>`）：候选会同时给出"带命名空间完整形式"与"省略 minecraft 的简写形式"两种补全。

---

## 6. `json/demoMessage.json` —— JSON 结构定义

供 `"type": "JSON", "key": "demoMessage"` 的节点引用（如 tellraw 场景），描述"这段 JSON 允许长什么样"：

```jsonc
{
  // 【装载】结构 id：被 JSON 节点的 key 引用（找不到 → 物化失败/整包拒绝）
  "id": "demoMessage",

  // 【结构】起始节点：解析从哪个节点开始
  "start": "PARENT",

  // 【结构】节点图：核心把 json 文本按这套节点图解析/补全（与 rawtext.json 同机制）
  "node": [
    // 一个 JSON 对象：{ "text": "..." }
    {
      "type": "JSON_OBJECT",
      "id": "PARENT",
      "description": "演示消息对象：{ \"text\": \"...\" }",
      // JSON_OBJECT 的 data：允许的键列表
      "data": [
        {
          "key": "text",             // 允许出现的键名
          "description": "消息文本",  // 该键的说明
          "value": ["TEXT"]           // 该键的值允许是哪些节点（引用本文件的 node id）
        }
      ]
    },
    // 一个自由 JSON 字符串节点（可被上面 value 引用）
    { "type": "JSON_STRING", "id": "TEXT", "description": "原始文本字符串" }
  ]
}
```

效果（与内置 tellraw 的 rawtext 一致）：输入 `{ "text": "` 时补全 JSON 键名（含说明）；字符串值带 JSON 转义处理（`AutoSuggestion<NodeJsonString>` 会把候选按转义重映射、未闭合时提示补引号）。

---

## 7. `extensions/execute-addon.json` —— execute 子命令扩展片段

**不是普通命令**：由 `manifest.extends.repeat.execute.file` 指向，【合成】（规划）把它声明的分支追加进内置 execute 的 REPEAT 数据。

```jsonc
{
  // 以下字段仅供结构自描述（name/syntax 不参与命令名匹配）
  "name": ["execute"],
  "description": "（拓展片段：向内置 execute 追加子命令，由合成器合并后生效，不单独注册）",
  "syntax": ["/execute <addon: ExecuteChainedAddon_1>"],

  "node": {
    "<addon: ExecuteChainedAddon_1>": {
      "type": "REPEAT",          // 声明这是对 REPEAT 数据的扩展
      "key": "execute",          // 目标 REPEAT 数据 id（只能扩展已存在的 execute，不能新建）
      "description": "示例服务器动作（execute myaction <target>）",

      // ★ 片段专用字段：要追加的分支列表（普通命令节点里没有这个字段）
      "branches": [
        {
          "isEnd": true,         // 该分支执行完是否算"可终结"（影响解析：分支后能否再跟 run）
          "nodes": [             // 分支的节点序列（顺序执行）
            {
              "type": "TEXT",    // 子命令名（字面量）：execute ... myaction
              "description": "服务器动作名",
              "data": { "name": "myaction", "description": "执行示例服务器动作" }
            },
            {
              "type": "TARGET_SELECTOR",   // 后续参数
              "description": "动作作用的目标实体",
              "isOnlyOne": false, "isMustPlayer": false, "isMustNPC": false, "isWildcard": false
            }
          ]
        }
      ]
    }
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `branches[]` | 每项 = 一个可重复分支（对应内置 repeat 数据里的一类 execute 子命令） |
| `branches[].nodes` | 该分支的节点序列（子命令名 TEXT + 参数…），与 repeat 分支同构 |
| `branches[].isEnd` | 分支结束 = 允许整条 execute 链在此终结 |
| 限制 | 分支内不允许嵌套 REPEAT；只允许"表达式"节点 |

合成后效果（规划）：`execute run ...` 原分支保留，新增 `execute myaction <target>` 分支，`isEnd` 数组同步追加保证链式解析正确。

---

## 8. `selector/demoSelector.json` —— 选择器变量/参数（草案）

> 【选择器】规划能力（selector 数据化，P2）；当前 schema 为草案，字段级说明见 [selector-data.md](./selector-data.md)。

```jsonc
{
  "id": "demoSelector",   // 叠加键（同 id 后包覆盖前包）
  "variables": [          // 追加的自定义选择器变量
    {
      "name": "@x",       // 变量 token（含 @）；@a/@e/@p/@r/@s 为内置保留字，不可覆盖
      "description": "示例服务器在线白名单玩家",
      "arguments": ["type", "name", "myflag"]  // 该变量允许的参数白名单（缺省 = 全部）
    }
  ],
  "arguments": [          // 追加的参数定义（内置变量与自定义变量共用参数表）
    {
      "name": "myflag",       // 参数名
      "brief": "服务器标记",  // 短名
      "description": "是否启用服务器标记",
      "operator": "=",        // 支持的操作符（"=" 默认；"=,!" 表示还支持 !=；数值类可列全）
      "valueType": "BOOLEAN"  // 值类型：BOOLEAN/NORMAL_ID/NAMESPACE_ID/INTEGER/FLOAT/
                              // RELATIVE_FLOAT/RANGE/STRING/TARGET_SELECTOR
      // 若 valueType 是 NORMAL_ID/NAMESPACE_ID，还需 key（候选表）或 contents（内联）
    }
  ]
}
```

规划效果：`@x[myflag=true]`、`/execute as @x ...` 中 `@x` 可用且带说明；参数名补全与值补全与内置参数一致。

---

## 9. 数据流：一个输入串怎么变成补全（走一遍）

输入 `/kit dia`，光标在末尾：

1. 【解析】`NodeCommand` 读命令名 `kit` → 命中本包（合成后）的 NodePerCommand；剩余 `dia` 进入语法图分支 A → `NORMAL_ID` 节点读入 `dia`；
2. 【补全】`AutoSuggestion<NodeNormalId>` 拿 `dia` 对 ID/kit.json 候选做前缀匹配 → `diamond`（说明"钻石礼包"）；
3. 若该命令来自附加包，建议对象携带来源包名 → UI 显示"来自 Demo 服务器命令包"（规划）。

输入 `/kit list`：分支 B 的 `TEXT` 字面量精确匹配，无错误；输入 `/kit nope`：NORMAL_ID 表外内容 → ID 错误提示（除非节点声明 `ignoreError`）。

---

## 10. 字段速查索引

| 想改什么 | 改哪个文件/字段 |
| --- | --- |
| 加一条命令 / 加命令头 | `command/<名>.json`：`name` 数组；新命令就新建 json |
| 加/改一个参数 | `command/<名>.json` 的 `node`（type/brief/description/key/contents/约束字段） |
| 改候选列表 | `ID/<表>.json` 的 `content`（或命令里内联 `contents`） |
| 参数语义约束 | `TARGET_SELECTOR` 的 isOnlyOne/isMustPlayer/isMustNPC/isWildcard；`STRING` 的 canContainSpace 等 |
| 自定义 JSON 文本结构 | `json/<名>.json` + 命令 `JSON` 节点 `key` |
| 给 execute 加子命令 | `extensions/<片段>.json` + `manifest.extends`（已支持） |
| 自定义选择器变量/参数 | `selector/<名>.json`（V1 子集已支持，白名单/contents/列表值暂未生效，见 selector-data.md §2.1） |
| 给包排先后（同名命令谁生效） | 应用内 **设置 → 资源包管理** 的列表顺序（靠上优先）；`manifest.priority` 为预留字段 |
