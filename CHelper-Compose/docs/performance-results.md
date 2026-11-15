# 2026-11-15 真机性能记录

已建立可重复测量与回归基线，尚未达到所有交互零超时帧。以下是本次测得的结果，不能推广为所有设备或所有功能的流畅度保证。

## 测量条件和证据

- M098FE / songyuan，Android 17 API 37；固件和 CPU / ART 信息保存在数值基线中。
- 独立 `.benchmark` 包，R8 release 配置，真实共享界面、Repository、JNI，离线 HTTP fixture `offline-2000-v2`，2000 条命令、详情响应延迟 80 ms；不包含 Umeng 的成本。
- 动画保持启用，系统刷新率设置均为 `null`，以 FrameTimeline 的实际 deadline 判断超时；不能推断为固定 60 / 120 Hz。
- 默认计时关闭 JankStats JSONL 采集，交互开启 Compose SDK tracing；每项重复 5 次。同一设备上的不同轮次仍有调度噪声。
- 全部四项测试通过的版本化基线：[M098FE-api37-profile-v2.json](../performance/baselines/M098FE-api37-profile-v2.json)。原始 JSON、日志及 Perfetto 位于本机 `build/performance/verified-profile/`，不提交大体积 trace。

## 保留的优化

实际采集的 Baseline Profile 有 30,808 条规则，其中 3,891 条属于应用。已过滤 fixture、MockEngine 和 tracing 工具自身，验证合并规则包含应用代码，并检查 APK 内编译产物。来源及哈希见 [profile-capture.json](../performance/profile-capture.json)。独立 Startup Profile 捕获返回空结果，因此没有提供该文件；完整 Baseline Profile 已包含首页启动路径。

同一目标和运行器 APK 的冷启动对照（各 5 次，首次显示与完整显示数值相同）：

| 编译模式 | 启动 P50 | 启动 P95 |
| --- | ---: | ---: |
| None，未预编译 | 195.49 ms | 207.15 ms |
| Partial，强制使用打包 Profile | 171.72 ms | 179.05 ms |

本次中位数降低 23.78 ms，约 12.2%。未预编译的原始证据在 `build/performance/uncompiled-class/`，数值已保存到 [startup-uncompiled-v2.json](../performance/baselines/M098FE-api37-startup-uncompiled-v2.json)。两轮 `run.json` 的 APK SHA256 相同；这是一组有限次数的对照，不是统计显著性证明。该差异体现同一 APK 在两种 ART 编译状态下的表现，不能作为新增 Profile 相对原版的净收益；后者需要原有规则与新增规则在相同编译条件下另行对照。安装时也可能已完成 Profile 引导的 ART 预编译，不能把 None 模式直接等同于所有用户的正常安装状态。

增加了 `ReportDrawnWhen`，让启动测量等待既有设置 / 背景恢复门槛。它报告首页初始内容准备，不代表深链接或远端业务数据全部加载。

## 当前交互基线与剩余问题

负 overrun 表示在帧期限前完成，正值表示超时。超时比例统计测试实际产生的帧，不是用户会话时间占比。

| 场景 | 采样帧 | CPU P99 | overrun P99 | 超时帧比例 |
| --- | ---: | ---: | ---: | ---: |
| 设置进入、滚动、返回 | 1196 | 8.50 ms | -3.73 ms | 6/1196，0.50% |
| 命令全文替换 8 次 | 914 | 12.17 ms | +3.32 ms | 22/914，2.41% |
| 命令库展开、滚动、返回 | 1398 | 10.42 ms | +1.40 ms | 17/1398，1.22% |

设置达到交互 P99 deadline 目标，但仍有偶发超时。命令和命令库未达该目标，`--require-p99-deadline` 应失败，不能为了通过门禁上调成“无掉帧”。冷启动只有约两帧 / 次，不套用交互 P99 目标。

诊断 trace 中，命令库一个约 20.30 ms 的 UI 帧包含约 12.62 ms 的 measure/layout，后续应优先缩小首屏布局和文本测量成本。命令编辑一个约 16.65 ms 的帧涉及选择光标 Popup 的创建、重布局和 Binder 调用；这只能证明该帧的热点，不能认定所有输入超时均由弹窗或解析导致。保留文本选择、IME、返回动画行为，不用删除功能掩盖耗时。FrameTimeline 的 Buffer Stuffing / SurfaceFlinger 等异常也保留在 SQL 输出中。

## 已测量并撤回的尝试

- 后台文本预测量没有稳定收益，命令库 P99 overrun 从约 -0.41 ms 增至 +1.50 ms，已撤回。这组早期诊断的采集条件与最终基线不同，不能用于推断 Profile 的收益。
- 官方 LazyLayoutCacheWindow（前方半屏、后方 0.2 屏）候选重复 5 次，命令库 P99 overrun 为 +2.81 ms、超时 25/1467（1.70%），比最终基线 +1.40 ms、1.22% 更差，已撤回。证据在 `build/performance/cache-window/`。单轮结果不足以证明普遍有害，但不足以支持保留该优化。

本轮自动化没有覆盖逐字符输入及所有命令前缀、IME 提交、EasyWindow 悬浮窗、真实网络和 Windows。逐帧采集有队列、单会话和保留文件上限；缺失、丢样、截断会明确标记，不能视为零掉帧。后续可在相同条件下追加这些 journey，再按 trace 热点逐项优化。

## 最终验证

撤回试验后重新构建的性能 APK SHA256 为 `4c943a4c18ca1d75e75e1eab285ad5f6038688ce7eb0dcef4ffb118727f52bf0`，与通过全部四项测试的 APK 完全相同。正式版及性能 APK 的 Compose 共享资源均校验通过，编译后的 Profile 分别为 12,985 和 12,856 字节。正式版运行时没有 MockEngine、Macrobenchmark 或 Perfetto 测试二进制。

Android release / benchmarkRelease 构建、Lint、共享 UI JVM 测试及 Windows 编译通过；11 项性能脚本测试、26 项架构测试、源码架构与 GPL header 检查通过。Android / Windows 解析依赖及现有 Windows 分发目录没有 Material。Windows 此次验证是构建和依赖检查，没有测量其帧呈现性能。
