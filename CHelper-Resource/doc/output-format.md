# chelper 输出格式契约

产物目录：`output/chelper/{release,beta,netease}/{vanilla,experiment}/id/`。
每个分支 29 个文件。格式与 caidlist `new_chelper` 分支
`src/generators/chelper.js` 的输出**逐字节兼容**。

未翻译 ID 清单单独写在 `output/untranslated/{版本}/{分支}/{类别}.json`，每个文件是
ID 字符串数组。只要类别仍有未翻译 ID 就保留该文件；漏译清空后会删除旧文件，
避免过期清单造成误读。

最终采用 `ai_translations/` 中未复核译文的条目，会在资源包 `description` 后追加
`（AI翻译，仅供参考）`；已复核、wiki 和 caidlist 来源的译文不添加该标记。

## 全局格式规则

- UTF-8，无 BOM；
- 4 空格缩进（对齐 `JSON.stringify(value, null, 4)`）；
- **CRLF** 行尾（JS 侧为 `JSON.stringify(...).replace(/\n/g, '\r\n')`）；
- 文件**末尾无换行符**（以 `}` 结束）；
- 键顺序 = 构造顺序（JSON 对象键保序）；
- 值为 `undefined` / 空时**整个字段省略**（如 `description`、`properties`、`valid`）；
- 整数值的浮点数按 JS 规则输出为整数（`1.0` → `1`）；
- 无任何版本号 / uuid / 生成时间元信息。

## 四种条目类型

### normal（绝大多数类别）

```json
{
    "id": "cameraEasing",
    "type": "normal",
    "content": [
        {
            "name": "in_back",
            "description": "缓入（回弹效果）"
        },
        {
            "name": "camera_relative"
        }
    ]
}
```

- 条目仅 `name`（+ 可选 `description`）；无翻译时 `description` 整个省略。

### namespace（仅 entity.json）

同 normal，但 `type: "namespace"`；键以 `minecraft:` 开头的条目被跳过。

### block（仅 block.json）

```json
{
    "id": "block",
    "type": "block",
    "content": {
        "blockStateValues": [
            {
                "name": "acacia_button",
                "description": "金合欢木按钮",
                "properties": [
                    { "name": "button_pressed_bit", "defaultValue": false },
                    { "name": "wood_type", "defaultValue": "acacia", "valid": ["acacia"] }
                ]
            }
        ],
        "blockPropertyDescriptions": {
            "common": [
                {
                    "propertyName": "age",
                    "description": "积累随机刻数",
                    "values": [ { "valueName": 0 }, { "valueName": 1 } ]
                }
            ],
            "block": [
                { "blocks": ["bamboo_sapling"], "properties": [] }
            ]
        }
    }
}
```

- 方块属性数据**固定取 `version/beta/gametest/all.json`**（与生成的是哪个版本无关）；
  `valid` 来自 `validStateOverrides`（仅存在时输出）；
- 方块在 gametest 数据中不存在 → 仅有 `name`/`description`，`properties` 省略并告警；
- 属性描述来源为 wiki `Module:Block_property_descriptions_BE`，三种形态：
  - 字符串 → 整段 `description`；
  - 首元素为字符串的数组（弃用标记）→ 取**末元素**为 `description`，values 无描述；
  - `[[键, 描述], …]` 对数组 → 按 `valueName` 匹配（键可为字符串或字符串数组）；
  - wiki 文本先经 `parseWikiText` 清洗（`<br>`→空格、去 `<b>`/`<code>`/`[[ ]]`/`{{…}}`）；
- `block[n].properties` 恒为数组（可能为空）——复刻 JS 对普通对象取 `.size` 恒为
  `undefined` 的行为。

### item（仅 item.json）

同 normal，另对 `potion` / `splash_potion` / `lingering_potion` 三种物品附加
`descriptions` 数组（47 条药水描述，来自 `data/potion_descriptions.json`）。

## 文件清单与特殊规则

| 文件 | 规则 |
| --- | --- |
| `gameRuleInteger.json` / `gameRuleBoolean.json` | gamerule 按 wiki 类型表拆分；输出 `name` 为类型表中的 camelCase 原名（匹配大小写不敏感）；类型表之外的类型告警并跳过 |
| `entity.json` | `namespace` 类型；跳过 `minecraft:` 前缀键 |
| `lootTable.json` | 键来自 `lootTableWrapped`：全部键的 JSON 引号化形式（`"\"entities/armor_stand\""`），不含 `/` 的键另有去引号形式 |
| `music.json` | `sound` 结果中 `music.` / `record.` 前缀子集 |
| `structure.json` | `location` 类别改名 |
| `enchantType.json` | `enchant` 类别改名 |
| `gameMode.json` | 本工具新增，内容来自 `data/game_mode.json` |
| `particleEmitter.json` | 本工具新增，wiki《基岩版粒子》页解析（`minecraft:` 前缀键） |

类别排除：`ability`、`command`、`blockState`、`lootTool`、`summonableEntity`
以及未改名前的 `lootTable` 不输出（对齐 chelper.js 的过滤与改名表）。
