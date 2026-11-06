# B组交叉复核报告

日期：2026-10-29

## A组复核

已按 `CHelper-Resource/output/review-2026-10-29-defaults-cleanup/audit-input/` 完整阅读A组26条命令的全部去重变体和字段，包括 `aimassist`、`camerashake`、`clone`、`damage`、`difficulty`、`event`、`fog`、`gamerule`、`help`、`kick`、`listd`、`me`、`op`、`party`、`playanimation`、`reload`、`save`、`scoreboard`、`setblock`、`spawnpoint`、`structure`、`teleport`、`testfor`、`tickingarea`、`titleraw`、`weather`。交叉阅读确认命令语法、字段brief、用途、单位、方向、显式输入效果、Boolean两态与真实枚举动作的关系。

核对了aimassist在beta、release和网易版的提案：X/Y轴角度、最大距离和瞄准模式默认注释均已删除，brief及min/max元数据保留。structure在beta、release和网易版的提案中，结构名的命名空间默认提示已移除；0/90/180/270度、none/x/z/xz和memory/disk仍保留为真实枚举ID及用途，默认标签已删除。标题类的 `title`、`titleraw` 两命令均保留 `reset` 的重置目的说明，淡入/持续/淡出字段保留游戏刻单位。

按root统一重跑的最新 `final-docs`，逐个查看了title与titleraw的release、beta、网易版及vanilla、experiment提案共12个文件。所有变体的语法一致，`reset` 说明为“重置指定玩家屏幕标题的淡入、持续、淡出时间。”；三个时长字段分别只说明其时长用途和游戏刻单位，没有默认数值、秒/游戏刻换算例子或重复单位。brief保留。

另确认scoreboard操作字段中的默认距离排序注释已移除，Boolean与枚举操作含义保留。weather持续时间显式输入0时产生随机时长的效果属于0值行为，保留。未发现其他默认值相关遗漏。

## B组目录复核

逐项人工阅读65条目录输入：`entitySlot` 13条、`hudElement` 14条、`gameRuleBoolean` 33条、`gameRuleInteger` 5条。均为枚举用途或游戏规则效果，没有默认注释或依赖默认值的附加比较需要删除，因此无需修改 `game_rules.json`。保留条目名称、单位、游戏设置概念和用途含义。

详细目录复核记录见 [defaults-cleanup-2026-10-29-group-b-catalogs.md](defaults-cleanup-2026-10-29-group-b-catalogs.md)，B组命令字段记录见 [defaults-cleanup-2026-10-29-group-b.md](defaults-cleanup-2026-10-29-group-b.md)。
