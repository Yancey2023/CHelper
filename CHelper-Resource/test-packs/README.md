# CHelper 资源包管理 - 测试包（test-packs）

导入测试用的一对拓展包，配套「设置 → 资源包管理」做验收。
包内容/格式细节见设计文档：[pack-format](../../CHelper-Doc/src/design/extension-pack/pack-format.md)、[composer §3.3](../../CHelper-Doc/src/design/extension-pack/composer.md)、[selector-data §2.1](../../CHelper-Doc/src/design/extension-pack/selector-data.md)、[tutorial](../../CHelper-Doc/src/design/extension-pack/tutorial.md)；本文件只写"怎么导、验什么"。

## 产物

- `pack-a.chepack` —— 测试资源包 A（v0.6.0）：自定义命令（/welcome 与别名 /hi、/grant、/execute myaction）、内置表引用（/pentity、/pitem、/pblock、/pgamemode）、自定义方块 demo:demo_machine、自定义物品与自定义实体（默认/demo 命名空间）、选择器自定义变量 @x + 布尔参数 myflag、JSON 结构参数 /pjson；全部 JSON 带 `//` 注释；
- `pack-b.chepack` —— 测试资源包 B：与 A 同名 /welcome（max 分支、无 /hi），用于冲突与排序验证。

## 导入

1. 把 `.chepack` 拷到手机（Android Studio Device Explorer / adb push / 传输工具均可）；
2. **设置 → 资源包管理 → 导入 .chepack** 选择文件；
3. 回到命令页（或让其回到前台）即生效。改过包内容后重新导入即可（同 packId 覆盖更新，无需先删）。

## 验收清单（操作 → 预期）

| 操作 | 预期 |
| --- | --- |
| 只启用 A | `/we` 有 welcome（说明带【A】）；`/hi` 可用；`/welcome all`、`/welcome player/vip/admin`、`/grant @a`、`/execute myaction @s` 均无报错 |
| 内置表引用 | `/pentity `/`/pitem `/`/pblock `/`/pgamemode ` 分别出实体/物品/方块/模式候选，选择后无报错 |
| 自定义方块与物品 | `/pblock ` 含 `demo:demo_machine`；`/pblock demo:demo_machine`、`/setblock ~ ~ ~ demo:demo_machine[lit=false]` 无报错且状态值有中文描述；**短名 `demo_machine` 报错**（非 minecraft 命名空间必须带前缀）；`/pitem ` 含 `custom_gadget`（默认命名空间，短名合法），选择无报错 |
| 自定义实体 | `/pentity ` 含 `demo_guard`（默认命名空间，`/pentity demo_guard` 无报错）与 `demo:demo_pet`（带前缀，`/pentity demo:demo_pet` 无报错、短名 `demo_pet` 报错）；前缀候选带「来自 测试资源包 A」 |
| JSON 结构参数 | `/pjson ` 后输入 `{"text":"hi"}` 无报错 |
| 选择器 | `/grant @a[` 出参数候选；`@a[type=player]` 无报错；`/grant @x[myflag=true]`、`@a[myflag=true]` 无报错，`@x[myflag=1]` 报错；`/grant @` 变量候选含 `@x` |
| 来源徽标 | `/we` 的 welcome 行、`/welcome ` 的 player/vip/admin 行都显示「来自 测试资源包 A」；内置命令/候选不显示 |
| 只启用 B | `/welcome max`、`boss/friend` 正常；`/welcome all` 报错；`/hi` 不存在 |
| 冲突与排序 | A 在上：all 可用、max 报错、hi 存在；把 B 移到 A 上面后反转（max 可用、all 报错、hi 消失）；双开时 `/welcome ` 候选 = 两包合并（player/vip/admin/boss/friend） |
| 停用/删除 | 停用 A → 其命令消失，重新启用恢复；删除 → 文件移除，重启不复活 |

顺带验证：A 全部 JSON 带 `//` 注释且能正常加载 = 引擎与校验器对 JSON 注释的支持正常。
