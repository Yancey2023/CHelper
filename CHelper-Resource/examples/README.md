# Demo 服务器命令包（示例拓展包）

本目录演示 CHelper **拓展包**（Extension Pack）的目录结构，与设计文档对应：

- 设计文档：`CHelper-Doc/src/design/extension-pack/`（overview / pack-format / composer / selector-data / clients-and-roadmap）
- 打包产物：`demo-server-pack.chepack`（zip，由下方校验脚本 `--pack` 生成）

## 目录结构

```
demo-server-pack/
├─ manifest.json              # 包声明（必填） + extends 合成指令
├─ command/
│  ├─ kit.json                # /kit <kitName> [player] 与 /kit list（全新命令 + ID 表引用）
│  └─ hub.json                # /hub 与 /spawn 双命令头合并为一个 json
├─ ID/
│  ├─ kit.json                # normal 表：kitName 的候选（starter/diamond/legend）
│  ├─ entity.json             # namespace 表：向内置 entity 追加自定义实体（同名合并场景）
│  └─ block.json              # block 表：自定义方块 demo:demo_machine + 方块状态补全（lit/power）
├─ json/
│  └─ demoMessage.json        # 自定义 JSON 结构（复用 rawtext 机制，供 JSON 类型节点引用）
├─ extensions/
│  └─ execute-addon.json      # execute 子命令扩展片段（由 manifest.extends 引用）
└─ selector/
   └─ demoSelector.json       # 自定义选择器变量 @x 与参数 myflag（schema 草案）
```

## 各部分生效状态

| 内容 | 状态 | 说明 |
| --- | --- | --- |
| command/kit、command/hub、ID/、json/ | 当前 schema 即支持 | 命令/候选/json 结构与内置资源完全同构；合成器（P0）落地后即可被加载，也可临时作为"迷你资源目录"用 `CPack` 目录构造验证（需补齐空目录） |
| ID/block.json | 规划能力 | 自定义方块/方块状态需合成器把 `BlockIds`（blockStateValues + 属性描述表）条目级合并进主包（原 P3 决策项，建议提前至 P1）；合并后 `setblock`/`fill` 的 BLOCK 参数即可补全 `demo:demo_machine["lit"=...]` |
| extensions/execute-addon.json | 规划能力 | 依赖合成器的 repeat 分支合并（P0 范围）；实现前**不放入 command/**，避免被当作普通命令注册 |
| selector/demoSelector.json | 规划能力 | 依赖选择器数据化（P2 范围）；当前核心不加载 selector/ 目录 |

## 静态校验与打包

```bash
# 校验（无需编译核心）
node CHelper-Resource/examples/validate_extension_pack.mjs CHelper-Resource/examples/demo-server-pack

# 校验并打包为 .chepack（zip 改扩展名）
node CHelper-Resource/examples/validate_extension_pack.mjs CHelper-Resource/examples/demo-server-pack --pack
```

## 预期效果（合成器落地后）

- 输入 `/k` → 建议 `kit`（来源标注：Demo 服务器命令包）；
- 输入 `/kit ` → 建议礼包名 `starter/diamond/legend`，输入 `/kit list` 命中字面分支；
- `/hub` 与 `/spawn` 等效（双命令头）；
- `setblock`/`fill` 输入 `demo:demo_machine[` 后补全 `"lit"=true/false`（带"（默认值）"标记）、`"power"=0..3`（ID/block.json 合并后）；
- `execute` 内新增分支 `... myaction <target>`，原有分支不受影响；
- `/summon @x[myflag=true]` 或 `/execute as @x ...` 中 `@x` 可用（选择器数据化落地后）。
