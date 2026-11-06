# netease 多出内容

netease 的 ID 数据来自配置的 `netease` 数据源（其 `version/release`）。
netease 相对该 release 版本**只多不少**，多出的 ID 在此目录以数据文件维护
（不写死在代码）：每个分支一个子目录（`vanilla/`、`experiment/`），
每个输出类别一个 JSON 文件（按输出文件名命名，如 `entity.json`、`block.json`），
内容为 `{ "ID": "中文描述" }`（描述可为空字符串，表示暂无翻译）。

规则：只叠加、不覆盖——已存在于正常管线结果中的 ID 会被忽略。
注意：仅当该类别在本版本的生成结果中存在时才会被叠加（版本特性门控之外的类别会被跳过并警告）。

## cameraEasing

固定的 NetEase `caidlist` 快照没有 `cameraEasings` 类别，但现有 NetEase camera 命令的 `easeType` 和 `fovEaseType` 节点仍引用 `cameraEasing`。因此 vanilla 与 experiment 分支分别在 `cameraEasing.json` 登记现有维护目录中的32个ID，保持原顺序，映射值留空；此处只补枚举名称，不复制用途说明。最终用途说明继续由 `data/wiki_patches/camera.json` 提取并通过 wiki catalog 管线生成。
