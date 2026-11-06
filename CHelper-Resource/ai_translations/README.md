# 未复核的 AI 翻译（第 4 优先级）

此目录只存放 AI 生成、尚未经过人工复核的翻译。每个类别一个 JSON 文件
（如 `block.json`、`camera_easing.json`），格式为 `{ "ID": "中文译名" }`。
这些译文会参与生成，因此应视为未经确认的候选内容。
若某条未复核 AI 译文成为最终采用的翻译，生成资源包会在译文后追加
`（AI翻译，仅供参考）`，方便使用者识别。

复核通过的译文（无论最初由人工还是 AI 撰写）应移入 `translations/`；
未复核的 AI 译文留在本目录。两目录内容重复时，`translations/` 的值优先。

翻译优先级：已复核翻译（`translations/`）> wiki > caidlist translation 目录 >
**未复核 AI 翻译（本目录）**。
四级都没有的 ID 在输出中省略 description 字段。

当前为空：所有类别都没有未复核的 AI 翻译。
