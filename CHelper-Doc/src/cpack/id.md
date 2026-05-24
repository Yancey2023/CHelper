# ID 的注册

在个根目录中，有一个`id`的文件夹用于存储命令，里面的每个 json 文件对应一种 id。

ID 有 4 种类型：

|   类型    |      含义       |      备注      |
| :-------: | :-------------: | :------------: |
|  normal   |     普通 ID     |       -        |
| namespace | 带命名空间的 ID |       -        |
|   block   |     方块 ID     |  带有方块状态  |
|   item    |     物品 ID     | 带有物品数据值 |

## 普通的 ID

```json
{
  "type": "normal",
  "id": "fogs",
  "content": [
    {
      "name": "minecraft:fog_bamboo_jungle",
      "description": "竹林"
    }
  ]
}
```

|  名字   |  类型  |     含义      | 备注 | 必需 |
| :-----: | :----: | :-----------: | :--: | :--: |
|  type   | 字符串 |    ID 类型    |  -   |  是  |
|   id    | 字符串 | ID 列表的名字 |  -   |  是  |
| content |  数组  |    ID 列表    |  -   |  是  |

每个 ID：

|    名字     |  类型  |  含义   | 备注 | 必需 |
| :---------: | :----: | :-----: | :--: | :--: |
|    name     | 字符串 | ID 名字 |  -   |  是  |
| description | 字符串 | ID 介绍 |  -   |  否  |

## 带有命名空间的 ID

```json
{
  "type": "namespace",
  "id": "entities",
  "content": [
    {
      "idNamespace": "minecraft",
      "name": "allay",
      "description": "悦灵"
    }
  ]
}
```

|  名字   |  类型  |     含义      | 备注 | 必需 |
| :-----: | :----: | :-----------: | :--: | :--: |
|  type   | 字符串 |    ID 类型    |  -   |  是  |
|   id    | 字符串 | ID 列表的名字 |  -   |  是  |
| content |  数组  |    ID 列表    |  -   |  是  |

每个 ID：

|    名字     |  类型  |   含义   |                     备注                     | 必需 |
| :---------: | :----: | :------: | :------------------------------------------: | :--: |
| idNamespace | 字符串 | 命名空间 | 默认为 minecraft，如果是 minecraft，建议省略 |  否  |
|    name     | 字符串 | ID 名字  |                      -                       |  是  |
| description | 字符串 | ID 介绍  |                      -                       |  否  |

## 方块 ID

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
          {
            "name": "button_pressed_bit",
            "defaultValue": false
          },
          {
            "name": "facing_direction",
            "defaultValue": 0
          }
        ]
      }
    ],
    "blockPropertyDescriptions": {
      "common": [
        {
          "propertyName": "age",
          "description": "植物的生长阶段 刚种下或刚生长出的植物方块的该属性值为0 该值随着时间变化不断增长，直到15时可以继续在上方生长出新的植物方块",
          "values": [
            {
              "valueName": 0
            },
            {
              "valueName": 1
            }
          ]
        }
      ],
      "block": [
        {
          "blocks": ["creaking_heart"],
          "properties": [
            {
              "propertyName": "active",
              "values": [
                {
                  "valueName": true,
                  "description": "嘎枝之心处于激活状态"
                },
                {
                  "valueName": false,
                  "description": "嘎枝之心处于未激活状态"
                }
              ]
            }
          ]
        }
      ]
    }
  }
}
```

|  名字   |  类型  |   含义   | 备注  | 必需 |
| :-----: | :----: | :------: | :---: | :--: |
|   id    | 字符串 | ID 名字  | block |  否  |
|  type   | 字符串 | ID 类型  |   -   |  是  |
| content |  对象  | 方块数据 |   -   |  是  |

content：

|           名字            |         类型         |                含义                | 备注 | 必需 |
| :-----------------------: | :------------------: | :--------------------------------: | :--: | :--: |
|     blockStateValues      |         数组         | 所有方块及其方块状态属性名和默认值 |  -   |  是  |
| blockPropertyDescriptions | 所有方块状态值及注释 |              ID 类型               |  -   |  是  |

blockStateValues 每个 ID：

|    名字     |  类型  |   含义   |                     备注                     | 必需 |
| :---------: | :----: | :------: | :------------------------------------------: | :--: |
| idNamespace | 字符串 | 命名空间 | 默认为 minecraft，如果是 minecraft，建议省略 |  否  |
|    name     | 字符串 | ID 名字  |                      -                       |  是  |
| description | 字符串 | ID 介绍  |                      -                       |  否  |
| properties  |  数组  | 方块状态 |                      -                       |  否  |

properties 每个方块状态：

|     名字     |  类型  |          含义          |    备注    | 必需 |
| :----------: | :----: | :--------------------: | :--------: | :--: |
|     name     | 字符串 |     方块状态参数名     |     -      |  是  |
| defaultValue | 正整数 | 默认值是第几个方块状态 | 暂时没用到 |  是  |

blockPropertyDescriptions：

|  名字  | 类型 |          含义          | 备注 | 必需 |
| :----: | :--: | :--------------------: | :--: | :--: |
| common | 数组 | 所有方块共用的方块状态 |  -   |  是  |
| block  | 数组 | 部分方块特有的方块状态 |  -   |  是  |

common 每个方块状态：

|     名字     |  类型  |        含义        | 备注 | 必需 |
| :----------: | :----: | :----------------: | :--: | :--: |
| propertyName | 字符串 |   方块状态参数名   |  -   |  是  |
| description  | 字符串 |      ID 介绍       |  -   |  否  |
|    values    |  数组  | 方块状态参数值列表 |  -   |  是  |

values 每个方块状态的值：

|    名字     |           类型           |      含义      | 备注 | 必需 |
| :---------: | :----------------------: | :------------: | :--: | :--: |
|    value    | 布尔值 / 正整数 / 字符串 | 方块状态参数值 |  -   |  是  |
| description |          字符串          |      介绍      |  -   |  否  |

block 中的每个值：

|    名字    |     类型     |   含义   | 备注 | 必需 |
| :--------: | :----------: | :------: | :--: | :--: |
|   blocks   |  字符串数组  | 方块 ID  |  -   |  是  |
| properties | 方块状态数组 | 方块状态 |  -   |  是  |

properties 中的每个方块状态：

|     名字     |  类型  |        含义        | 备注 | 必需 |
| :----------: | :----: | :----------------: | :--: | :--: |
| propertyName | 字符串 |   方块状态参数名   |  -   |  是  |
| description  | 字符串 |      ID 介绍       |  -   |  否  |
|    values    |  数组  | 方块状态参数值列表 |  -   |  是  |

values 每个方块状态的值：

|    名字     |           类型           |      含义      | 备注 | 必需 |
| :---------: | :----------------------: | :------------: | :--: | :--: |
|    value    | 布尔值 / 正整数 / 字符串 | 方块状态参数值 |  -   |  是  |
| description |          字符串          |      介绍      |  -   |  否  |

## 物品 ID

```json
{
  "id": "item",
  "type": "item",
  "content": [
    {
      "name": "stone_block_slab2",
      "description": "红砂岩台阶/紫珀台阶/海晶石台阶/暗海晶石台阶/海晶石砖台阶/苔石台阶/平滑砂岩台阶/红色下界砖台阶",
      "descriptions": [
        "红砂岩台阶",
        "紫珀台阶",
        "海晶石台阶",
        "暗海晶石台阶",
        "海晶石砖台阶",
        "苔石台阶",
        "平滑砂岩台阶",
        "红色下界砖台阶"
      ]
    }
  ]
}
```

|  名字   |  类型  |  含义   |    备注     | 必需 |
| :-----: | :----: | :-----: | :---------: | :--: |
|   id    | 字符串 | ID 名字 | 永远是 item |  否  |
|  type   | 字符串 | ID 类型 |      -      |  是  |
| content |  数组  | ID 列表 |      -      |  是  |

每个 ID：

|     名字     |    类型    |             含义              |            备注            |         必需         |
| :----------: | :--------: | :---------------------------: | :------------------------: | :------------------: |
|  namespace   |   字符串   |           命名空间            | 如果是 minecraft，建议省略 | 否(默认为 minecraft) |
|     name     |   字符串   |            ID 名字            |             -              |          是          |
| description  |   字符串   |            ID 介绍            |             -              |          否          |
|     max      |   正整数   |         数据值最大值          |             -              |          否          |
| descriptions | 字符串列表 | 每个数据值的介绍（从 0 开始） |             -              |          否          |
