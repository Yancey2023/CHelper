# 验证报告

目标：确认本工具生成的 chelper 资源包与 caidlist `new_chelper` 分支修改后
生成的内容**格式完全一致**。

- 参考输出：`E:\project\CHelper\caidlist\output\chelper`（new_chelper 分支的
  JS 生成器产出，数据版本 release 1.26.52.3 / beta 1.26.60.29，与 master 快照一致）
- 验证命令：`chhelper verify --reference E:/project/CHelper/caidlist/output/chelper`
- 完整逐文件结果见 [verification_run.txt](verification_run.txt)

## 结论

release / beta × vanilla / experiment 共 4 个分支、每分支 26 个公共文件：

| 分支 | 完全一致 | 格式一致、内容有差异 | 缺失 |
| --- | --- | --- | --- |
| release/vanilla | 23 | 3 | 0 |
| release/experiment | 23 | 3 | 0 |
| beta/vanilla | 23 | 3 | 0 |
| beta/experiment | 22 | 4 | 0 |

- **格式层面零差异**：所有 104 个对比文件在 UTF-8、4 空格缩进、CRLF 行尾、
  末尾无换行、JSON 合法性、键序结构上全部符合契约。
- 内容差异全部为**数据源更新**导致（见下），无解析或管线缺陷。

## 内容差异明细与根因

### 1. gameRuleBoolean / gameRuleInteger（各 1 处）——wiki 页面实时更新

| ID | 参考（new_chelper 运行时的 wiki 文本） | 本工具（当前 wiki 文本） |
| --- | --- | --- |
| mobGriefing | "……包括苦力怕、僵尸……" | "……包括苦力怕、**硫方怪**、僵尸……"（wiki 新增译名） |
| maxCommandChainLength | "决定了连锁型命令方块能连锁执行的总数量。" | "决定了连锁型命令方块**和函数**能连锁执行的总数量。" |

已用 new_chelper 缓存的 `version/common/wiki/gamerule.txt` 交叉验证解析器：
对同一份 wikitext，本工具解析结果与参考一致，差异纯粹来自 wiki 页面内容更新。

### 2. block.json（40~41 处）——wiki 方块属性描述更新

全部 40+ 处差异为同一模式：参考输出中 `blockPropertyDescriptions` 的 values
条目仅有 `valueName`，本工具多出 `description`。例如 `age_bit`：

- 参考运行时 wiki 模块中该属性无按值描述；
- 当前 wiki 模块已补充按值描述（"新近生成"/"可以生长"）。

即 wiki 的 `Module:Block_property_descriptions_BE` 在参考输出生成之后新增了内容。
其余结构（blockStateValues 顺序、common 描述、键序）完全一致。

### 3. item.json（仅 beta/experiment 1 处）——master 比 new_chelper 多一次写回

`icicle`（新实验性物品）：new_chelper 的 `translation/item.json` 尚无该 ID
（参考输出无描述）；master 的 `translation/item.json` 已写回 `"ST: icicle"`，
而标准译名表中 `icicle` 尚未被翻译为中文，故输出英文 `"Icicle"`。
这是数据源（master）比参考生成时更新的正常结果，行为符合第 3 级优先级定义。

### 4. 其余 20~23 个文件：字节级完全一致

包括最复杂的 `sound.json`（模板拼接 + JE 字幕引用）、`lootTable.json`
（`{{entities|entity!xxx}}` 模板 + 引号键）、`recipe.json`（dataDriven 标识符
替换）、`entity.json`（命名空间合并）、`block.json` 之外的全部结构化输出。

## 本工具新增文件（无参考对照）

- `gameMode.json`：与 CHelper-Resource 手工维护版本内容一致（数据即取自那里）；
- `particleEmitter.json`：wiki《基岩版粒子》页解析产出（190 条），与手工版本
  同源（update_translation.py 逻辑），格式同 normal 类型。

## netease

数据源为 `XeroAlpha/caidlist` 历史上 release 数据停留在 **1.21.50.07** 的最后一个
提交（`5c780d1d`，对应网易 1.21.50.07），生成成功：vanilla / experiment 各 26 个
文件（1.21.50 时代尚无 controlScheme、feature、featureRule 三类命令，特性门控
下不产出）、8400 / 8401 条目；方块属性取该 commit 自带的 beta gametest 数据
（1.21.60.23 时代）。格式与 release/beta 相同，并叠加 `netease_extra/` 中
手工维护的多出 ID。

## 复现

```bash
cargo run --release -- generate release beta
cargo run --release -- verify --reference E:/project/CHelper/caidlist/output/chelper
```
