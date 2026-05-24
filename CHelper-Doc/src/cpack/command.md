# 命令的注册

在资源包的根目录中，有一个`command`的文件夹用于存储命令，里面的每个 json 文件对应一个命令。

```json
{
  "name": ["alwaysday", "daylock"],
  "description": "锁定或解锁日夜循环",
  "syntax": ["/alwaysday [lock: Boolean]"],
  "node": {
    "[lock: Boolean]": {
      "type": "BOOLEAN",
      "brief": "是否锁定日夜更替",
      "description": "是否锁定日夜更替",
      "descriptionTrue": "锁定昼夜更替",
      "descriptionFalse": "不锁定昼夜更替"
    }
  }
}
```

|    名字     |    类型    |    含义    |                        备注                        | 必需 |
| :---------: | :--------: | :--------: | :------------------------------------------------: | :--: |
|    name     | 字符串列表 | 命令的名字 |                 可以填写多个命令名                 |  是  |
| description |   字符串   | 命令的介绍 |                         -                          |  否  |
|   syntax    | 字符串列表 | 语法字符串 |         每个字符串表示一种语法，以`/`开头          |  是  |
|    node     |  节点对象  |  节点定义  | 键为语法片段，值为节点定义，将所有节点定义写在这里 |  是  |

## syntax 语法字符串

在 `syntax` 数组中，每个字符串表示命令的一种语法。语法字符串以 `/` 开头，后面跟着参数占位符：

- `<参数名: 参数类型>` — 必选参数，用尖括号包裹
- `[参数名: 参数类型]` — 可选参数，用方括号包裹

多个语法字符串可以提供不同的语法变体。例如 `gamemode` 命令：

```json
"syntax": [
  "/gamemode <gameMode: GameMode> [player: target]",
  "/gamemode <gameMode: int> [player: target]"
]
```

## node 节点对象

`node` 对象中的键就是语法字符串中的参数占位符（包括括号），值就是该节点的定义。

当同一个参数位置有多种类型可供选择时，可以用 `|` 分隔：

```json
"<gameMode: GameMode>|<gameMode: int>": {
  "type": "NORMAL_ID",
  "description": "玩家的新游戏模式",
  "ignoreError": true,
  "key": "gameMode"
}
```

这意味着 `<gameMode: GameMode>` 和 `<gameMode: int>` 共享同一个节点定义。

当同一语法位置可选的类型较复杂时，可以将多种选择组合成一个节点 ID：

```
"<tileName: Block>|<tileName: Block> <blockStates: block states>": { ... }
```
