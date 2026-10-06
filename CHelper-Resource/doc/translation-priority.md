# 翻译优先级与 DSL

## 四级优先级（每个 ID 独立回退）

粒度是**每个 ID**而非每个类别：某 ID 在高优先级来源中"有非空翻译"即停；
高优先级“没有该 ID”或“值为空/仅空白”则继续向下一级。解析后为空的结果也不会成为最终译文。
引用无法解析或最终结果为空时同样继续尝试较低优先级来源。

| 级别 | 来源 | 位置 | 说明 |
| --- | --- | --- | --- |
| 1 | 已复核翻译 | `translations/<类别>.json` | 可由人工撰写，也可先由 AI 撰写；进入此目录前须经过人工复核 |
| 2 | wiki | 工具自行抓取解析 | gamerule（《游戏规则》页）、particleEmitter（《基岩版粒子》页）；另有方块属性描述、gamerule 类型表等结构性 wiki 数据 |
| 3 | caidlist translation 目录 | master 快照 `translation/*.json` | 值多为引用表达式，其引用的 ST / JE / BE 资源由本工具**自行抓取** |
| 4 | 未复核的 AI 翻译 | `ai_translations/<类别>.json` | AI 生成且尚未人工复核；四级都没有 → 输出省略 `description` |

`translations/` 表示内容已复核，不表示译文一定由人工从头撰写。AI 参与生成的译文，
经人工核对后放入 `translations/`；仍待核对的 AI 结果放入 `ai_translations/`。

补充规则：

- `"EMPTY"` 是 caidlist 运行时写回翻译文件的"未找到"标记。遇到它视为**硬性未找到**
  （对齐 JS `matchTranslation` 在 autoMatch 之前返回 notFound 的行为），不再向更低级别回退。
- 文件为 JSONC（支持 `//` 注释与尾逗号），格式 `{ "ID": "中文译名" }`。
- 生成完成时会汇总存在部分漏译的类别，并在 `output/untranslated/<版本>/<分支>/`
  按类别写出未翻译 ID 的 JSON 数组。某类别没有漏译 ID 时删除对应旧文件；
  同一类别完全没有译文时保留清单，但不计入“部分漏译”警告。

## 引用 DSL（第 3 级翻译文件的值语法）

对齐 caidlist `src/util/templateMatch.js`：

| 写法 | 含义 |
| --- | --- |
| `中文` | 字面量 |
| `ST: <key>` | 标准译名表（键大小写不敏感；子表级联顺序见下） |
| `JE: <键>` | Java 版语言文件（如 `JE: subtitles.mob.cow.ambient`） |
| `BE: <键>` | 基岩版语言文件（如 `BE: tile.stone.name`） |
| `this: <ID>` | 当前列表内其它 ID 的翻译 |
| `<类别>: <ID>` | 其它类别结果表的翻译（如 `item: apple`），仅可引用**先于本类别匹配**的类别 |
| `Missing: <字面量>` | 暂缺标记：按字面量输出，若恰好命中标准译名表则用译名替换 |
| `{{a|b|c}}` | 拼接模板（见下） |
| `EMPTY` | 未找到标记（见上） |

### 拼接模板

`{{...}}` 内以 `|` 分隔参数；首参数决定处理函数：

- `{{<fmt>|<arg…>}}` / `{{format|<fmt>|<arg…>}}`：格式化，对齐 Node `util.format`
  （`%s`、`%d`、`%j`、`%%`……）。如 `"{{ambient.*.additions|ST!basalt deltas}}"` →
  `玄武岩三角洲：环境附加音效`。
- `{{pick|<arg…>}}`：取第一个可解析（provided）的参数。
- `{{if_exists|<test>|<a>|<b>}}`：若 `test` 存在于本类别的 ID 列表则取 `a` 否则取 `b`。
- 参数以 `'` 开头时为原始字符串（原样传递）。
- 参数形如 `ST!key` / `JE!key` / `BE!key` 为模板内外部引用（等价 `ST:key`）。
- 模板中任一引用解析失败 → 整个值置空（并告警）。

### 标准译名表（ST）级联

ST 数据是 `{ BlockSprite: {...}, ItemSprite: {...}, …, ExclusiveBlockSprite: {...} }` 的
子表集合。每个类别按优先级级联（优先子表的键先命中，其余子表按原顺序拼入）：

| 类别 | 优先子表 |
| --- | --- |
| block | `BlockSprite` → `ExclusiveBlockSprite` → 其余 |
| item | `ItemSprite` → `ExclusiveItemSprite` → 其余 |
| entity / entityEvent / entityFamily / animation / animationController | `EntitySprite` → `ExclusiveEntitySprite` → 其余 |
| effect | `EffectSprite` → `ExclusiveEffectSprite` → 其余 |
| enchant | `EnchantmentSprite` → 其余 |
| fog / biome | `BiomeSprite` → `ExclusiveBiomeSprite` → 其余 |
| location | `EnvSprite` → 其余 |
| 其它 | 全部子表按原顺序 |

查找大小写不敏感，取插入序中第一个命中。

## 与 caidlist 自动匹配链的差异

caidlist 在翻译文件缺失某 ID 时会自动猜测并**写回**翻译文件：
`stdTrans`（ID 归一化后查 ST）→ `lang`（`<前缀>.<ID>.<后缀>` 查语言文件）→
`langLikely`（模糊键），音效则用 Java 字幕做最长公共子串匹配。

本工具**不复制猜测链**，原因：caidlist 的 `translation/*.json` 提交时已包含
历次运行的写回结果（`ST: xxx`、`JE: subtitles.xxx` 等），覆盖了绝大多数 ID；
本工具的第 3 级直接消费这些写回结果，产出与 caidlist 相同。仅当出现
"翻译文件完全没有的新 ID"时，本工具会留空（交给第 4 级 AI 翻译补充），
而 caidlist 会当场猜测——这是有意的行为差异，避免输出随 wiki 数据波动。

## 各类别与翻译文件对照

| 输出文件 | 原始 ID 来源 | 翻译文件（human / caidlist / AI 同名不同目录） |
| --- | --- | --- |
| block.json | `autocompletion.blocks` | `block.json` |
| item.json | `items`（非方块项，或在翻译文件中有条目者）+ block 合并 | `item.json` |
| entity.json | `entities`（命名空间合并） | `entity.json` |
| effect / enchantType / structure / biome | `effects` / `enchantments` / `locations` / `biomes` | `effect` / `enchant` / `location` / `biome` |
| entityEvent / entityFamily / animation / animationController | `entityEventsMap` / `entityFamilyMap` / `animationMap` / `animationControllerMap` 的键 | 同名下划线文件 |
| sound → music | `package.sounds` | `sound.json`；music 为 `music.` / `record.` 前缀子集 |
| gameRuleInteger / gameRuleBoolean | `gamerules` | `gamerule.json` + wiki 第 2 级；按 wiki 类型表拆分 |
| entitySlot / damageCause / inputPermission / cameraPreset / cameraEasing / recipe / hudElement / feature / featureRule / controlScheme | 对应 autocompletion 键（受版本开关控制） | 同名下划线文件 |
| lootTable | `package.lootTables` | `loot_table.json`；键含 `/` 时输出为 JSON 引号化形式 |
| gameMode | `data/game_mode.json`（手工） | — |
| particleEmitter | wiki《基岩版粒子》页 | — |
