# 静态资源的数据源

`grammer`、`json`、`repeat` 的规范定义在此维护。不要修改
`resources/<版本>/<分支>/` 中对应的生成快照。

```text
resource_definitions/
├─ grammer/target_selector.json
├─ json/components.json
├─ json/rawtext.json
├─ repeat/execute.json
└─ netease/repeat/execute.json
```

公共定义供六个包复用。依次按公共定义、`<版本>/`、`<版本>/<分支>/` 选择整份文件，
后者只覆盖同路径文件，不会删除其他公共定义。网易execute保留独立完整定义，
包含该版本专有维度；不做节点合并或增加字段绑定语言。

`generate` 在开始时加载并验证这些数据，导出资源包后刷新所选版本对应的
`resources` 快照。缺少或损坏数据源时直接报错，绝不从旧快照回退。
四个公共定义和网易专用execute都是必需源；同时验证定义ID、图入口与节点、repeat分支结构，
避免合法JSON空对象或缺失版本覆盖被错误导出。
更改公共定义即可更新全部适用包，修改后的快照不再作为下一轮生成的输入。

命令Wiki文字继续在`data/wiki_patches`维护；ID译名继续使用已有`translations`入口。
本目录用于静态语法、JSON结构和repeat定义，不复制整套命令说明或新增Wiki绑定。
