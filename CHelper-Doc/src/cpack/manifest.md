# manifest.json

在资源包的根目录里应该存在一个叫做`manifest.json`的文件用来存储命令包的基本信息：

```json
{
  "name": "资源包示例",
  "description": "CHelper扩展包的官方示例",
  "version": "1.20",
  "versionType": "release",
  "branch": "vanilla",
  "author": "Yancey",
  "updateDate": "2025-5-30",
  "packId": "ExamplePack-1.20-vanilla",
  "requiredPack": ["BasicPack-1.20"],
  "versionCode": 1,
  "isBasicPack": true,
  "isDefault": true
}
```

|     名字     |  类型  |         含义          |                    备注                    |        必需        |
| :----------: | :----: | :-------------------: | :----------------------------------------: | :----------------: |
|     name     | 字符串 |     资源包的名字      |                  用于显示                  |  否（不建议忽略）  |
| description  | 字符串 |     资源包的简介      |                  用于显示                  |         否         |
|   version    | 字符串 |    对应的游戏版本     |                  用于显示                  |         否         |
| versionType  | 字符串 |     游戏版本类型      |                  用于显示                  |         否         |
|    branch    | 字符串 |     游戏版本分支      |                  用于显示                  |         否         |
|    author    | 字符串 |         作者          |                  用于显示                  |         否         |
|  updateDate  | 字符串 |       更新日期        |                  用于显示                  |         否         |
|    packId    | 字符串 |      资源包的 ID      |               用于识别资源包               |         是         |
| requiredPack |  数组  | 需要依赖的资源包的 ID |          用于识别资源包的依赖关系          | 否（默认没有依赖） |
| versionCode  |  整数  |        版本号         |             用于显示和判断版本             |         是         |
| isBasicPack  | 布尔值 |   是否是命令基础包    | 只能同时加载一个命令基础包和多个命令扩展包 | 否（默认为 false） |
|  isDefault   | 布尔值 |   是否为默认资源包    |    如果为 true，应用启动时默认加载此包     | 否（默认为 false） |

::: warning
暂时还不支持命令拓展包，目前只支持命令基础包
:::
