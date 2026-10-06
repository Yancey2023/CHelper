# netease 多出内容

netease 的 ID 数据来自配置的 `netease` 数据源（其 `version/release`）。
netease 相对该 release 版本**只多不少**，多出的 ID 在此目录以数据文件维护
（不写死在代码）：每个分支一个子目录（`vanilla/`、`experiment/`），
每个输出类别一个 JSON 文件（按输出文件名命名，如 `entity.json`、`block.json`），
内容为 `{ "ID": "中文描述" }`（描述可为空字符串，表示暂无翻译）。

规则：只叠加、不覆盖——已存在于正常管线结果中的 ID 会被忽略。
注意：仅当该类别在本版本的生成结果中存在时才会被叠加（版本特性门控之外的类别会被跳过并警告）。
