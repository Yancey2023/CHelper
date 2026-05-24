# 节点的定义

在前面已经讲了命令是如何注册的，其中每个命令需要在 nodes 里面写出所有用到的的节点，每个节点的 json 如何写就是接下来要讲的内容

|       类型        |         含义         |                示例                |                   备注                    |
| :---------------: | :------------------: | :--------------------------------: | :---------------------------------------: |
|       BLOCK       |         方块         | minecraft:lever\["open_bit"=true\] |                     -                     |
|      BOOLEAN      |        布尔值        |                true                |                     -                     |
|      COMMAND      |         命令         |      give @s minecraft:stone       |                     -                     |
|   COMMAND_NAME    |        命令名        |              setblock              |                     -                     |
|       FLOAT       |         小数         |                1.5                 |                     -                     |
|      INTEGER      |         整数         |                 2                  |                     -                     |
| INTEGER_WITH_UNIT |     带单位的整数     |                 2L                 |                     -                     |
|       ITEM        |         物品         |     minecraft:fire_charge 1 12     |                     -                     |
|        LF         |       结束节点       |                 -                  | 内置节点，不可使用，但可以使用 LF 这个 ID |
|   NAMESPACE_ID    |   带命名空间的 ID    |          minecraft:stone           |                     -                     |
|     NORMAL_ID     |      普通的 ID       |              creative              |                     -                     |
|    PER_COMMAND    |       每条命令       |                 -                  |            内置节点，不可使用             |
|     POSITION      |         位置         |             ~1~0.2~-5              |                     -                     |
|  RELATIVE_FLOAT   |       相对坐标       |                ~1.5                |                     -                     |
|      REPEAT       |      重复的参数      |                 -                  |      不可在定义 JSON 数据的时候使用       |
|      STRING       |        字符串        |               "a a"                |                     -                     |
|  TARGET_SELECTOR  |      目标选择器      |             @e\[r=5\]              |                     -                     |
|       TEXT        |         文字         |               score                |                     -                     |
|       RANGE       |         范围         |                1..2                |                     -                     |
|       JSON        |      Json 文本       |  {"rawtext":\[{"text":"Hello"}\]}  |      不可在定义 JSON 数据的时候使用       |
|   JSON_BOOLEAN    |     Json 布尔值      |                true                |      只能在定义 JSON 数据的时候使用-      |
|   JSON_ELEMENT    |      Json 元素       |                 -                  |            内置节点，不可使用             |
|    JSON_ENTRY     |     Json 键值对      |                 -                  |            内置节点，不可使用             |
|    JSON_FLOAT     |      Json 小数       |                1.5                 |      只能在定义 JSON 数据的时候使用       |
|   JSON_INTEGER    |      Json 整数       |                 12                 |      只能在定义 JSON 数据的时候使用       |
|     JSON_LIST     |      Json 列表       |            \[..., ...\]            |      只能在定义 JSON 数据的时候使用       |
|     JSON_NULL     |      Json 空值       |                null                |      只能在定义 JSON 数据的时候使用       |
|    JSON_OBJECT    |      Json 对象       |      {"...": ..., "...": ...}      |      只能在定义 JSON 数据的时候使用       |
|    JSON_STRING    |     Json 字符串      |               "..."                |      只能在定义 JSON 数据的时候使用       |
|        AND        |        和节点        |                 -                  |            内置节点，不可使用             |
|        ANY        |       任何节点       |                 -                  |            内置节点，不可使用             |
|       ENTRY       |        键值对        |                 -                  |            内置节点，不可使用             |
|    EQUAL_ENTRY    | 可以是不等号的键值对 |                 -                  |            内置节点，不可使用             |
|       LIST        |         数组         |                 -                  |            内置节点，不可使用             |
|        OR         |        或节点        |                 -                  |            内置节点，不可使用             |
|     OPTIONAL      |       可选节点       |                 -                  |            内置节点，不可使用             |
|   SINGLE_SYMBOL   |       单个字符       |                 -                  |            内置节点，不可使用             |

## BLOCK 方块

```mcfunction
含义：方块ID + 方块状态 / 方块ID
例子：minecraft:lever["open_bit"=true]
```

```json
{
  "type": "BLOCK",
  "id": "block",
  "description": "更改后的新方块",
  "nodeBlockType": 0
}
```

|     名字      |  类型  |                    含义                     |                 备注                  | 必需 |
| :-----------: | :----: | :-----------------------------------------: | :-----------------------------------: | :--: |
|     type      | 字符串 |                  节点类型                   |                   -                   |  是  |
|      id       | 字符串 |                   节点 ID                   |                   -                   |  否  |
|     brief     | 字符串 |                  简要说明                   | 如果 description 太长，推荐填写 brief |  否  |
|  description  | 字符串 |                  节点介绍                   |                   -                   |  否  |
| nodeBlockType | 正整数 | 0 代表方块 ID + 方块状态，1 代表只有方块 ID |                   -                   |  是  |

## BOOLEAN 布尔值

```mcfunction
含义：布尔值，只能是true或false
例子：true
```

```json
{
  "type": "BOOLEAN",
  "id": "lock",
  "description": "是否锁定日夜更替",
  "descriptionTrue": "锁定昼夜更替",
  "descriptionFalse": "不锁定昼夜更替"
}
```

|      名字       |  类型  |      含义      |                 备注                  | 必需 |
| :-------------: | :----: | :------------: | :-----------------------------------: | :--: |
|      type       | 字符串 |    节点类型    |                   -                   |  是  |
|       id        | 字符串 |    节点 ID     |                   -                   |  否  |
|      brief      | 字符串 |    简要说明    | 如果 description 太长，推荐填写 brief |  否  |
|   description   | 字符串 |    节点介绍    |                   -                   |  否  |
| descriptionTrue | 字符串 | true 值得介绍  |                   -                   |  否  |
| descriptionTrue | 字符串 | false 值得介绍 |                   -                   |  否  |

## COMMAND 命令

```mcfunction
含义：用于命令中嵌套任意命令
例子：give @s minecraft:stone
```

```json
{
  "type": "COMMAND",
  "id": "command",
  "description": "命令"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |

## COMMAND_NAME 命令名

```mcfunction
含义：命令名
例子：setblock
```

```json
{
  "type": "COMMAND_NAME",
  "id": "command",
  "brief": "命令名",
  "description": "要提供帮助的命令名称"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |

## FLOAT 小数

```mcfunction
含义：小数
例子：1.5
```

```json
{
  "type": "FLOAT",
  "id": "fadeInSeconds",
  "brief": "淡入时间",
  "description": "相机视角的淡入时间",
  "min": 0,
  "max": 10
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|     min     |  小数  |  最小值  |                   -                   |  否  |
|     max     |  小数  |  最大值  |                   -                   |  否  |

## INTEGER 整数

```mcfunction
含义：整数
例子：2
```

```json
{
  "type": "INTEGER",
  "id": "page",
  "brief": "页码",
  "description": "要展示的命令列表的页码（小于1的数字会被视为1，大于总页数会被默认为展示最后一页）"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|     min     |  整数  |  最小值  |                   -                   |  否  |
|     max     |  整数  |  最大值  |                   -                   |  否  |

## INTEGER_WITH_UNIT 带单位的整数

```mcfunction
含义：带单位的整数
例子：2L
```

```json
{
  "type": "INTEGER_WITH_UNIT",
  "id": "amount",
  "brief": "经验值数量",
  "description": "给予玩家的经验值数量",
  "units": [
    {
      "name": "L",
      "description": "一个经验等级"
    }
  ]
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|    units    |  数组  | 单位列表 |                   -                   |  是  |

每个单位：

|    名字     |  类型  |  含义  | 备注 | 必需 |
| :---------: | :----: | :----: | :--: | :--: |
|    name     | 字符串 | 单位名 |  -   |  是  |
| description | 字符串 |  介绍  |  -   |  否  |

## ITEM 物品

```mcfunction
含义：物品
例子：minecraft:fire_charge 1 12
```

```json
{
  "type": "ITEM",
  "id": "item",
  "description": "要给予实体的物品",
  "nodeItemType": 0
}
```

|     名字     |  类型  |                                        含义                                         |                 备注                  | 必需 |
| :----------: | :----: | :---------------------------------------------------------------------------------: | :-----------------------------------: | :--: |
|     type     | 字符串 |                                      节点类型                                       |                   -                   |  是  |
|      id      | 字符串 |                                       节点 ID                                       |                   -                   |  否  |
|    brief     | 字符串 |                                      简要说明                                       | 如果 description 太长，推荐填写 brief |  否  |
| description  | 字符串 |                                      节点介绍                                       |                   -                   |  否  |
| nodeItemType | 正整数 | 0 代表<物品 ID> <物品数量> <附加值> [物品组件]，1 代表<物品 ID> <附加值> <物品数量> |                   -                   |  是  |

## NAMESPACE_ID 带命名空间的 ID

```mcfunction
含义：带命名空间的ID
例子：minecraft:stone
```

```json
{
  "type": "NAMESPACE_ID",
  "id": "entityType",
  "brief": "实体类型",
  "description": "要被召唤的实体类型",
  "key": "entity",
  "ignoreError": true
}
```

|    名字     |  类型  |                            含义                            |                 备注                  | 必需 |
| :---------: | :----: | :--------------------------------------------------------: | :-----------------------------------: | :--: |
|    type     | 字符串 |                          节点类型                          |                   -                   |  是  |
|     id      | 字符串 |                          节点 ID                           |                   -                   |  否  |
|    brief    | 字符串 |                          简要说明                          | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 |                          节点介绍                          |                   -                   |  否  |
|     key     | 字符串 |              id 的键值，用于在 id 列表索引 id              |     key 和 contents 至少存在一个      |  否  |
| ignoreError | 布尔值 | 如果用户输入的内容与 id 列表不一致时是否报错，默认为 false |                   -                   |  否  |
|  contents   |  数组  |                          id 列表                           |     key 和 contents 至少存在一个      |  否  |

contents 每个 ID：

|    名字     |  类型  |   含义   |                     备注                     | 必需 |
| :---------: | :----: | :------: | :------------------------------------------: | :--: |
| idNamespace | 字符串 | 命名空间 | 默认为 minecraft，如果是 minecraft，建议省略 |  否  |
|    name     | 字符串 | ID 名字  |                      -                       |  是  |
| description | 字符串 | ID 介绍  |                      -                       |  否  |

## NORMAL_ID 普通的 ID

```mcfunction
含义：普通的ID
例子：creative
```

```json
{
  "type": "NORMAL_ID",
  "id": "hud_element",
  "brief": "HUD元素",
  "description": "将被修改的HUD元素",
  "key": "hudElement",
  "ignoreError": true
}
```

|    名字     |  类型  |                            含义                            |                 备注                  | 必需 |
| :---------: | :----: | :--------------------------------------------------------: | :-----------------------------------: | :--: |
|    type     | 字符串 |                          节点类型                          |                   -                   |  是  |
|     id      | 字符串 |                          节点 ID                           |                   -                   |  否  |
|    brief    | 字符串 |                          简要说明                          | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 |                          节点介绍                          |                   -                   |  否  |
|     key     | 字符串 |              id 的键值，用于在 id 列表索引 id              |     key 和 contents 至少存在一个      |  否  |
| ignoreError | 布尔值 | 如果用户输入的内容与 id 列表不一致时是否报错，默认为 false |                   -                   |  否  |
|  contents   |  数组  |                          id 列表                           |     key 和 contents 至少存在一个      |  否  |

contents 每个 ID：

|    名字     |  类型  |  含义   | 备注 | 必需 |
| :---------: | :----: | :-----: | :--: | :--: |
|    name     | 字符串 | ID 名字 |  -   |  是  |
| description | 字符串 | ID 介绍 |  -   |  否  |

## POSITION 位置

```mcfunction
含义：位置
例子：~1~0.2~-5
```

```json
{
  "type": "POSITION",
  "id": "position",
  "brief": "方块位置",
  "description": "要被更改方块的位置"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |

## RELATIVE_FLOAT 相对坐标

```mcfunction
含义：相对坐标
例子：~1.5
```

```json
{
  "type": "RELATIVE_FLOAT",
  "id": "xRot",
  "brief": "rx",
  "description": "相机视角绕Y轴旋转的旋转角度(-180.0表示北，-90.0表示东，0.0表示南，90.0表示西)",
  "canUseCaretNotation": false
}
```

|        名字         |  类型  |            含义            |                 备注                  | 必需 |
| :-----------------: | :----: | :------------------------: | :-----------------------------------: | :--: |
|        type         | 字符串 |          节点类型          |                   -                   |  是  |
|         id          | 字符串 |          节点 ID           |                   -                   |  否  |
|        brief        | 字符串 | 简要说明，用于显示命令结构 | 如果 description 太长，推荐填写 brief |  否  |
|     description     | 字符串 |          节点介绍          |                   -                   |  否  |
| canUseCaretNotation | 布尔值 |    是否可以使用局部坐标    |                   -                   |  否  |

## REPEAT 重复的参数

```mcfunction
含义：重复的参数
例子：-
```

```json
{
  "type": "REPEAT",
  "id": "executeParam",
  "description": "子命令",
  "key": "execute"
}
```

|    名字     |  类型  |              含义              |                 备注                  | 必需 |
| :---------: | :----: | :----------------------------: | :-----------------------------------: | :--: |
|    type     | 字符串 |            节点类型            |                   -                   |  是  |
|     id      | 字符串 |            节点 ID             |                   -                   |  否  |
|    brief    | 字符串 |   简要说明，用于显示命令结构   | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 |            节点介绍            |                   -                   |  否  |
|     key     | 字符串 | 键值，用于在 repeat 中进行索引 |                   -                   |  是  |

## STRING 字符串

```mcfunction
含义：字符串
例子："a a"
```

```json
{
  "type": "STRING",
  "id": "trackName",
  "brief": "播放音乐名",
  "description": "必须为音乐名或record.<music_name>或music.game.<music_name>",
  "canContainSpace": true,
  "ignoreLater": false
}
```

|      名字       |  类型  |                             含义                             |                 备注                  | 必需 |
| :-------------: | :----: | :----------------------------------------------------------: | :-----------------------------------: | :--: |
|      type       | 字符串 |                           节点类型                           |                   -                   |  是  |
|       id        | 字符串 |                           节点 ID                            |                   -                   |  否  |
|      brief      | 字符串 |                           简要说明                           | 如果 description 太长，推荐填写 brief |  否  |
|   description   | 字符串 |                           节点介绍                           |                   -                   |  否  |
| canContainSpace | 布尔值 |         是否可以包含空格，也就是说是否支持使用双引号         |                   -                   |  是  |
|   ignoreLater   | 布尔值 | 是否忽略后面的东西，也就是说是否把后面的所有文字都当作字符串 |                   -                   |  是  |

## TARGET_SELECTOR 目标选择器

```mcfunction
含义：目标选择器
例子：@e[r=5]
```

```json
{
  "type": "TARGET_SELECTOR",
  "id": "target",
  "description": "被给予物品的玩家",
  "isOnlyOne": false,
  "isMustPlayer": true,
  "isMustNPC": false,
  "isWildcard": false
}
```

|     名字     |  类型  |         含义          |                 备注                  | 必需 |
| :----------: | :----: | :-------------------: | :-----------------------------------: | :--: |
|     type     | 字符串 |       节点类型        |                   -                   |  是  |
|      id      | 字符串 |        节点 ID        |                   -                   |  否  |
|    brief     | 字符串 |       简要说明        | 如果 description 太长，推荐填写 brief |  否  |
| description  | 字符串 |       节点介绍        |                   -                   |  否  |
|  isOnlyOne   | 布尔值 | 是否只能选择 1 个对象 |                   -                   |  是  |
| isMustPlayer | 布尔值 |   是否只能选择玩家    |                   -                   |  是  |
|  isMustNPC   | 布尔值 |   是否只能选择 NPC    |                   -                   |  是  |
|  isWildcard  | 布尔值 |    是否可以使用\*     |                   -                   |  是  |

## TEXT 文字

```mcfunction
含义：文字
例子：score
```

```json
{
  "type": "TEXT",
  "id": "play",
  "description": "播放音乐",
  "data": {
    "name": "play",
    "description": "播放音乐"
  }
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|    data     |  数组  |   内容   |                   -                   |  是  |

data：

|    名字     |  类型  |  含义   | 备注 | 必需 |
| :---------: | :----: | :-----: | :--: | :--: |
|    name     | 字符串 | ID 名字 |  -   |  是  |
| description | 字符串 | ID 介绍 |  -   |  否  |

## RANGE 范围

```mcfunction
含义：范围
例子：1..2
```

```json
{
  "type": "RANGE",
  "id": "range",
  "description": "分数范围"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |

## JSON Json 文本

```mcfunction
含义：Json文本
例子：{"rawtext":[{"text":"Hello"}]}
```

```json
{
  "type": "JSON",
  "id": "raw json message",
  "brief": "JSON文本",
  "description": "要发送的消息",
  "key": "rawtext"
}
```

|    名字     |  类型  |             含义             |                 备注                  | 必需 |
| :---------: | :----: | :--------------------------: | :-----------------------------------: | :--: |
|    type     | 字符串 |           节点类型           |                   -                   |  是  |
|     id      | 字符串 |           节点 ID            |                   -                   |  否  |
|    brief    | 字符串 |           简要说明           | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 |           节点介绍           |                   -                   |  否  |
|     key     | 字符串 | 键值，用于在 json 中进行索引 |                   -                   |  是  |

## JSON_BOOLEAN Json 布尔值

```mcfunction
含义：Json布尔值
例子：true
```

```json
{
  "type": "JSON_BOOLEAN",
  "id": "lock",
  "description": "是否锁定日夜更替",
  "descriptionTrue": "锁定昼夜更替",
  "descriptionFalse": "不锁定昼夜更替"
}
```

|      名字       |  类型  |      含义      |                 备注                  | 必需 |
| :-------------: | :----: | :------------: | :-----------------------------------: | :--: |
|      type       | 字符串 |    节点类型    |                   -                   |  是  |
|       id        | 字符串 |    节点 ID     |                   -                   |  否  |
|      brief      | 字符串 |    简要说明    | 如果 description 太长，推荐填写 brief |  否  |
|   description   | 字符串 |    节点介绍    |                   -                   |  否  |
| descriptionTrue | 字符串 | true 值得介绍  |                   -                   |  否  |
| descriptionTrue | 字符串 | false 值得介绍 |                   -                   |  否  |

## JSON_FLOAT Json 小数

```mcfunction
含义：Json小数
例子：1.5
```

```json
{
  "type": "JSON_FLOAT",
  "id": "score",
  "description": "分数"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|     min     |  小数  |  最小值  |                   -                   |  否  |
|     max     |  小数  |  最大值  |                   -                   |  否  |

## JSON_INTEGER Json 整数

```mcfunction
含义：Json整数
例子：12
```

```json
{
  "type": "JSON_INTEGER",
  "id": "SCORE_VALUE",
  "description": "可选。如果存在此值，则无论分数是多少，都将使用此值。"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|     min     |  整数  |  最小值  |                   -                   |  否  |
|     max     |  整数  |  最大值  |                   -                   |  否  |

## JSON_LIST Json 列表

```mcfunction
含义：Json列表
例子：[..., ...]
```

```json
{
  "type": "JSON_LIST",
  "id": "WITH",
  "description": "translate使用的聊天字符串参数的列表。",
  "data": "STRING"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|    data     | 字符串 | 数据类型 |                   -                   |  是  |

## JSON_NULL Json 空值

```mcfunction
含义：Json空值
例子：null
```

```json
{
  "type": "JSON_NULL",
  "id": "null",
  "description": "空值"
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |

## JSON_OBJECT Json 对象

```mcfunction
含义：Json对象
例子：{"...": ..., "...": ...}
```

```json
{
  "type": "JSON_OBJECT",
  "id": "BLOCKS",
  "description": "方块类型",
  "data": [
    {
      "key": "block",
      "description": "方块类型",
      "value": ["BLOCK_ID_LIST"]
    }
  ]
}
```

|    名字     |  类型  |   含义   |                 备注                  | 必需 |
| :---------: | :----: | :------: | :-----------------------------------: | :--: |
|    type     | 字符串 | 节点类型 |                   -                   |  是  |
|     id      | 字符串 | 节点 ID  |                   -                   |  否  |
|    brief    | 字符串 | 简要说明 | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 | 节点介绍 |                   -                   |  否  |
|    data     |  数组  |  键值对  |                   -                   |  是  |

data 中的每个键值对：

|    名字     |    类型    |  含义   | 备注 | 必需 |
| :---------: | :--------: | :-----: | :--: | :--: |
|     key     |   字符串   |   键    |  -   |  是  |
| description |   字符串   |  介绍   |  -   |  否  |
|    value    | 字符串数组 | 值的 ID |  -   |  是  |

## JSON_STRING Json 字符串

```mcfunction
含义：Json字符串
例子："..."
```

```json
{
  "type": "JSON_STRING",
  "id": "TARGET_SELECTOR",
  "description": "目标选择器",
  "data": [
    {
      "type": "TARGET_SELECTOR",
      "id": "TARGET_SELECTOR",
      "description": "目标选择器",
      "isMustPlayer": false,
      "isOnlyOne": false,
      "isMustNPC": false,
      "isWildcard": false
    },
    {
      "type": "TEXT",
      "id": "ALL_TARGET",
      "description": "目标选择器",
      "data": {
        "name": "*",
        "description": "选择全部实体"
      }
    }
  ]
}
```

|    名字     |  类型  |       含义       |                 备注                  | 必需 |
| :---------: | :----: | :--------------: | :-----------------------------------: | :--: |
|    type     | 字符串 |     节点类型     |                   -                   |  是  |
|     id      | 字符串 |     节点 ID      |                   -                   |  否  |
|    brief    | 字符串 |     简要说明     | 如果 description 太长，推荐填写 brief |  否  |
| description | 字符串 |     节点介绍     |                   -                   |  否  |
|    data     |  数组  | 字符串文本的节点 |                   -                   |  否  |
