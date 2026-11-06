# 默认说明清理复核：B组

日期：2026-10-29

## 复核范围

逐条阅读 `output/review-2026-10-29-defaults-cleanup/audit-input/` 中 B 组 26 条命令的完整去重对象，包括所有实际变体的 command、syntax、字段、brief、description、Boolean 两态和枚举内容。对参数用途、单位、方向、显式值行为和默认说明逐字段核对。`execute` 只有根级介绍句；按任务要求保留不改。

## 清理字段

| 命令 | 字段 | 处理 |
| --- | --- | --- |
| `effect` | `[seconds: int]` | 删除“默认为30秒”及例外默认时长数值；保留秒/游戏刻单位、效果用途、显式 0 秒移除效果行为。 |
| `effect` | `[amplifier: int]` | 删除默认倍率 0；保留倍率用途和等级换算说明。 |
| `effect` | `[hideParticles: Boolean]` | false 两态保留“不隐藏”效果，删除“默认”注释；Java 版图标效果保留。 |
| `gametest` | `[width: int]`、`[height: int]`、`[depth: int]` | 删除三个轴向尺寸的默认数值，保留尺寸用途。 |
| `playsound` | `[volume: float]` | 删除默认倍率 1.0 和正常可闻范围默认距离 16 米；保留响度用途以及小于/大于 1.0 的效果说明。 |
| `playsound` | `[pitch: float]` | 删除默认值 1.0；保留音高用途及影响播放速度的说明。 |
| `playsound` | `[minimumVolume: float]` | 删除默认值 0.0；保留范围外音量用途和显式输入 0 的效果。 |
| `replaceitem` | `data: int` | 删除默认值 0；保留物品数据用途。 |

其余 B 组字段逐项复核后未发现需要删除的默认注释。`hud` 的 `reset` 是恢复 HUD 显示状态的真实枚举动作，予以保留；`time` 的 `day`、`noon` 等时间标记及其明确数值是核心枚举含义，予以保留。未修改 syntax、brief、字段元数据及 `min`、`max`、`default` 元数据。

## 补丁和验证

只在 `CHelper-Resource/data/wiki_patches/` 中维护 `effect.json`、`gametest.json`、`playsound.json`、`replaceitem.json` 的有序 replacement；旧 replacement 均保留，新增项接续旧链。未改资源数据、缓存、生成物、Rust 源码或索引。

用指定发布版 CLI 和独立配置运行 B 组 26 条命令，完整输出记录在 `CHelper-Resource/output/review-2026-10-29-defaults-cleanup/group-b/extract-final.log`，字段提案在 `.../group-b/final-docs/`。日志摘要：26 个命令、148 个资源文件、0 个 unresolved fields、0 个 unresolved catalog fields。保留 8 条既有 warning：execute 的介绍 TOC、function 的四条超长 brief、playsound 的类别 brief、toggledownfall 缺少参数段落；该批要求明确 execute 根句不改，以上其余项也不属于默认说明清理范围。

逐项查看应用后的 `effect`、`gametest`、`playsound`、`replaceitem` Wiki 文本，确认默认语句已移除且相邻用途和指定输入效果仍在。提案对象的 `brief`、`syntax` 及类型/范围/default 元数据均保持原样。
