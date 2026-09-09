# CHelper 拓展包模板（extension-pack template）

这是编写 **CHelper 拓展包**（`.chepack`，旧称资源包）的**完整起步包**：覆盖自定义命令（候选表/字面量/目标选择器）、JSON 结构声明与 execute 子命令扩展；**每个 JSON 文件都内联了逐字段注释**——JSON 注释会被引擎自动忽略，可放心阅读与保留。

> 关联文档：
> - 包格式完整规范：[设计/包格式规范](../../CHelper-Doc/src/design/extension-pack/pack-format.md)
> - 逐字段走读教程：[开发示例：逐字段走读](../../CHelper-Doc/src/design/extension-pack/tutorial.md)（无注释示例包在 `examples/demo-server-pack`）
> - 拓展包进入应用：安卓端 **设置 → 资源包管理** 支持导入/启用停用/删除与列表排序（合成顺序 = 列表顺序，靠上优先）；回到命令页即生效。

## 目录结构

```
extension-pack/
├── README.md                    # 本说明
├── manifest.json                # 包信息 + 合成指令（extends）——先改这里
├── command/
│   ├── welcome.json             # /welcome <who> 与 /welcome all（候选表 + 字面量）
│   └── grant.json               # /grant <目标选择器>（TARGET_SELECTOR 参数）
├── ID/
│   └── greetWho.json            # 候选表：welcome 的 <who> 可选值
├── json/
│   └── demoMessage.json         # JSON 结构声明（"type":"JSON" 节点引用）
└── extensions/
    └── execute-addon.json       # execute 子命令扩展片段（manifest.extends 引用）
```

## JSON 注释规则

包内所有 JSON 都支持注释（**引擎与校验脚本都会自动忽略**）：

```jsonc
{
  "name": "示例",      // 行注释（// 到行尾）
  /* 块注释
     可以跨行 */
  "version": "1.0.0"
}
```

注意：注释只写在**语法空隙**处（键值之间/行尾），不要写在字符串内部；注释会被替换为空格，不会粘连两侧 token。

## 三分钟上手

1. **复制本目录**为你的包目录，例如 `my-server-pack/`；
2. 修改 `manifest.json` 的 `name`（中文名）、`packId`（唯一标识，建议 `xxx-0.1.0`）、`author`；
3. 按注释把 `command/*.json` 改成你的命令（不必保留全部演示文件，用不到的可删除）；
4. 在 `CHelper-Resource/` 目录下校验 + 打包（`--pack` 先校验再打包）：

```bash
node examples/validate_extension_pack.mjs my-server-pack        # 只校验
node examples/validate_extension_pack.mjs my-server-pack --pack # 校验并打包成 my-server-pack.chepack
```

5. 打包产物（条目使用 `/`、无目录条目）可直接交给合成器/安卓平台层解压装载。

## 文件与字段说明

### manifest.json（必填：`name` `version` `versionCode` `packId`）

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `name` | ✅ | 包显示名（中文），会作为补全候选的"来源"展示 |
| `description` | | 一句话描述 |
| `version` / `versionCode` | ✅ | 展示版本 / 递增的整数版本号 |
| `versionType` / `branch` | | 声明面向的游戏版本类型与分支（`thirdparty` 为第三方包惯例） |
| `author` | | 作者名 |
| `packId` | ✅ | 全局唯一标识，惯例 `<slug>-<version>`（改命令/表后请递增 versionCode 并更新它） |
| `isBasicPack` | | 第三方拓展包必须为 `false`（主包才为 `true`） |
| `priority` | | 信息/预留字段：合成器不读取；生效顺序 = 应用内"资源包管理"列表顺序（靠上优先） |
| `extends` | | 选填：扩展内置 `execute` 子命令等合成指令（见 demo-server-pack） |

### command/welcome.json（每文件注册一条命令）

| 字段 | 说明 |
| --- | --- |
| `name` | 命令名数组：`["welcome"]` 注册 `/welcome`；多写一个 `["welcome","hi"]` 即为别名 |
| `description` | 命令说明 |
| `syntax` | 人类可读的语法行（纯文档性质，用于展示/校对） |
| `node` | **解析树**：键 = `syntax` 里的占位符或字面量，值 = 节点定义 |

本模板演示两类节点：

```jsonc
// command/welcome.json（节选）
{
  "syntax": [
    "/welcome <who: GreetWho>",   // 占位符 <名字: 类型注释>
    "/welcome all"                 // 字面量分支
  ],
  "node": {
    "<who: GreetWho>": {           // 键必须与 syntax 占位符一致
      "type": "NORMAL_ID",         // 候选项 = 引用 ID 表的 key
      "key": "greetWho",           // 对应 ID/greetWho.json 的 id
      "brief": "欢迎对象",          // 参数栏简短提示
      "description": "……"         // 补全列表里的中文说明
    },
    "all": {                       // 字面量：输入 all 命中此分支
      "type": "TEXT",
      "description": "……",
      "data": { "name": "all", "description": "……" }
    }
  }
}
```

常用节点类型（完整清单与字段见 pack-format.md / tutorial.md）：

| type | 用途 |
| --- | --- |
| `NORMAL_ID` | 从本包/内置 ID 表选值（最常用，配合 `key`） |
| `NAMESPACE_ID` | 命名空间 ID（如物品 `minecraft:xxx`，内置表有 minecraft 前缀） |
| `TEXT` | 字面量关键词（如 `all`、`clear`） |
| `TARGET_SELECTOR` | 目标选择器 `@a[...]`（含内置补全与校验） |
| `INTEGER` / `FLOAT` / `BOOLEAN` / `POSITION` / `STRING` | 数值/布尔/坐标/字符串参数 |
| `JSON` | JSON 结构（`key` 引用 `json/` 目录的结构声明，如 rawtext） |

### ID/greetWho.json（候选表）

```jsonc
{
  "id": "greetWho",          // 命令里 NORMAL_ID 的 key 指向这里
  "type": "normal",          // normal=普通表；namespace=命名空间表
  "content": [
    { "name": "player", "description": "所有玩家" },  // name=候选值，description=中文提示
    { "name": "vip",    "description": "VIP 玩家" }
  ]
}
```

## 想加更多能力？

- **多参数 / 多分支命令、别名**：在 `syntax` 增加占位符与 `node` 分支即可；
- **execute 子命令扩展、JSON 结构声明、block/item 大表**：直接对照
  [`examples/demo-server-pack`](../../examples/demo-server-pack)（含注释演示）与
  [tutorial.md](../../CHelper-Doc/src/design/extension-pack/tutorial.md) 逐字段修改；
- 内置表引用（实体/物品/游戏模式等）不需要复制数据，`NORMAL_ID`/`NAMESPACE_ID` 的 `key`
  直接用内置表 id 即可（如 `key: "entity"`）。

## 常见问题

- **打包后安卓装不上/数据为空**：检查产物条目是否为 `/` 分隔（`--pack` 已保证）；
  手工 zip 时不要用会写 `\` 目录条目的工具（如 PowerShell `Compress-Archive`）。
- **校验报 "syntax 与 node 不匹配"**：`syntax` 中每个 `<占位符>` 与字面量都必须在 `node`
  中有同名键；删改任意一边都要同步另一侧。
- **改包后测试没变化**：versionCode/packId 更新后再打包；缓存/段切换问题先查合成器日志。
