# 命令资源的人工审阅

`resources/<版本>/<分支>/command` 是命令定义的维护源。`generate` 导出这些已复核定义；
命令页面缓存器只下载 Wiki 原文，不推导节点、编写说明或判断参数语义。

## 来源

先运行 `cache-command-wiki` 或 `generate`。在 `output/command_wiki/<命令>/` 中阅读：

- `page.wiki`：中文 Minecraft Wiki 返回的原始 wikitext；
- `source.json`：页面链接、重定向链、导出时间和抓取状态；
- `overview/page.wiki`：命令总览，可以补充别名与命令摘要。

页面不可用时查看错误和保留快照状态，不用自行撰写的文本替代页面。

## 逐命令复核

由 `gpt-6-luna` 子 agent 阅读命令文件和对应 Wiki 全文，逐一确认各实际存在的分支。
不能用程序扫描或自动修复替代这一步。

1. 核对根级和所有嵌套的 `description`，包括 `data`、枚举内容、
   `descriptionTrue` 和 `descriptionFalse`。以 Wiki 为语义依据，用自然中文说明作用；
   不把原文中的类型限制、默认值或“必须为以下其中之一”等枚举引导句复制进说明。
   说明字段避免使用“以下”，因为资源数据没有 Wiki 页面中紧接其后的列表或段落。
   按具体用途改写完整句子；数值上限可用“不超过”等表述，不机械删除词语。
2. 命令节点和枚举 ID 都应有说明。没有直接对应的原句时继续查阅相关 Wiki 页面，
   根据已确认的作用写清楚，不能靠删除说明字段结束审阅。
   布尔值两种状态分别说明实际行为，不使用“若为false……反之亦然”的条件模板。
3. 核对参数类型、值域、默认值和分支版本。最新版 Wiki 的语法不能直接覆盖旧版本能力。
4. 上游更改参数名称时，先判断是否为已有节点的同一参数；复用或重命名该节点，
   避免保留同义的旧节点和新节点。确认每个语法引用都有对应定义。
5. 别名保留在 `name`，`syntax` 只写主命令形式。
6. 在 `output/command_review` 手工记录每个命令的覆盖分支、Wiki 依据、改动和未决项。
   只读到页面不能记为完成全字段复核。

`execute` 按项目要求保留原有 REPEAT 入口和原节点说明。
`data/command_patches/execute.json` 是固定 patch，命令同步和自动生成都应用它；
子命令解析仍由 `repeat/execute.json` 提供。

## 导出和打包

审阅完成后重新运行 `generate`，再用 Core 打包完整输出：

```powershell
CHelperResourceGenerator.exe CHelper-Resource/output/chelper CHelper-Resource/generated
```

打包器会重新加载输出的 JSON 和二进制资源。最终还应运行 CHelper-Core 全部测试；
打包或测试暴露的问题需要修复到维护源或生成器中，再生成和验证。
