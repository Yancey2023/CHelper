# Android 性能基准与掉帧诊断

目标是在固定真机、刷新率和负载条件下持续降低超时帧，并阻止回归。不能保证任意设备、后台负载、温度和系统调度条件下零掉帧；测试通过也不代表所有功能均没有卡顿。

## 两种基线

- **性能回归基线**：Macrobenchmark 的实际测量 JSON，包含启动耗时及帧 CPU 耗时、帧超时分布。保留原始 Perfetto trace，定位 UI、RenderThread、原生调用、调度及 GPU 等不同原因。
- **Baseline Profile**：官方 Baseline Profile 工具在真实关键路径执行后生成 ART 预编译规则，通过 ProfileInstaller 分发。可另外采集用于 DEX 布局的 Startup Profile；不手写或复制不相关规则，不把“接入插件”当成已生成应用 profile。

`apps/benchmark` 是 Android 测试驱动模块，不包含业务实现，也不会进入 Android / Windows 的运行时依赖图。业务流程调用真实的共享 UI、ViewModel、Repository 和 JNI；`apps/android/src/benchmark` 只替换 HTTP 引擎，返回固定分页数据和 2000 条命令，详情响应延迟 80 ms。它隔离网络波动，不替代真实网络、图片和第三方服务的专项测试。性能变体使用 debug 的无分析上报实现，排除 Umeng；不能直接把它的结果作为包含分析 SDK 的正式版全量成本。

生成的 `benchmarkRelease` 使用 release 的 R8 / 资源压缩配置，`nonMinifiedRelease` 用于采样 profile。所有性能专用变体包名都是 `yancey.chelper.benchmark`，运行器为 `yancey.chelper.benchmark.test`，不覆盖 `yancey.chelper`、`.debug`、`.beta`。普通构建不自动连接设备或生成 profile。

## 在真机执行

从仓库根目录、**提权的终端**执行。固定一台已授权的真机，解锁屏幕，保持相同刷新率、动画比例、电源模式、IME 和字体大小。测量期间不操作设备，避免设备过热和后台任务。不要禁用动画来掩盖页面切换卡顿。无需 root；不抑制 Macrobenchmark 的设备、温度或可调试构建校验错误。

```powershell
# 指定 adb 的完整路径和精确 serial。脚本拒绝默认选择设备。
python scripts/run_android_performance.py --serial DEVICE_SERIAL --adb C:/Android/Sdk/platform-tools/adb.exe --allow-install --mode measure

# 采集包含首页启动及关键交互的 Baseline Profile。
python scripts/run_android_performance.py --serial DEVICE_SERIAL --adb C:/Android/Sdk/platform-tools/adb.exe --allow-install --mode profile

# 重新构建，把生成的规则打包，再验证全部交互和带 profile 的冷启动。
python scripts/run_android_performance.py --serial DEVICE_SERIAL --adb C:/Android/Sdk/platform-tools/adb.exe --allow-install --mode verify

# 对同一已安装 APK 测量未预编译冷启动，不触发安装。
python scripts/run_android_performance.py --serial DEVICE_SERIAL --adb C:/Android/Sdk/platform-tools/adb.exe --use-installed --mode measure --scenario coldStartupWithoutProfile
```

脚本通过指定 serial 的 ADB 更新独立性能变体和运行器，不清除或卸载已有应用；APK SHA256 相同时跳过安装。安装被拒绝就停止，不自动重试。不要用会先卸载应用的 Gradle connected test 安装流程替代它。`measure` 每个场景测量 5 次；设置滚动、命令全文替换、命令库展开及滚动先进行 3 次编译预热，冷启动使用 `CompilationMode.None`。`verify` 的全部四项测试强制要求实际打包的 profile，缺少时必须失败。两种编译模式不能混为同一性能回归基线。`--use-installed` 记录设备上真实 APK 的哈希，适合同一 APK 的编译模式对照。

产物在 `CHelper-Compose/build/performance/<UTC时间>/`：设备与执行条件 `run.json`、安装/测试日志、Macrobenchmark JSON、Perfetto trace 和汇总 `report.json`；逐帧 JSONL 需要另加 `--collect-frames`。默认计时关闭 JankStats 文件采集，避免诊断自身改变测量。仪器测试即使以退出码 0 返回，也必须含预期数量的 JUnit 成功结果；失败保留日志并返回非零。失败的测试产物用于诊断，不得提升为已验证基线。

可在 Perfetto UI 查看 trace，或用官方 `trace_processor query -f scripts/android_frame_hotspots.sql TRACE` 导出该进程所有被 FrameTimeline 标记的异常帧和主线程热点。SQL 保留 Buffer Stuffing、SurfaceFlinger 等原因，不能只看主线程超过 16.7 ms。嵌套 slice 的 inclusive 耗时会重叠，不能相加当作总 CPU 耗时。定位单个场景时，`measure` 支持 `--scenario libraryRevealAndScroll` 等筛选；回归比较要求相同场景。

生成规则保存于 `apps/android/src/main/generated/baselineProfiles/`，经检查后与代码一起提交。过滤测试 Application、MockEngine 和 tracing 工具自身的规则；发布 APK 中必须包含生成的 `assets/dexopt/baseline.prof`，并检查合并规则实际包含应用代码。默认采集完整关键路径；`--include-startup-profile` 另外采集独立启动布局规则。本次 M098FE 上独立启动捕获持续返回空规则，因此只保留已成功采集的 Baseline Profile，没有伪造 Startup Profile。冷启动使用系统 Perfetto；交互额外开启 Compose SDK tracing，避免该设备冷启动时 SDK tracing 广播失败。

## 逐帧采集与覆盖边界

Android Debug 默认开启 JankStats，性能变体及正式版默认关闭；可用 `-PcollectFrameMetrics=true` 对正式版进行专门诊断，性能脚本用 `--collect-frames`。采集只写设备本地，不上传、不记录命令原文、账号、URL 或命令库 ID。共享 `PerformanceState` 只描述页面、文档阶段、高亮启用、命令解析及设置滚动状态；Windows 默认为空实现。Android 使用官方 `PerformanceMetricsState` 关联这些状态。

每个 Activity 从 `onStart` 到 `onStop` 采集其 Window 的所有回调帧，并在停止后排空队列。API 24+ 记录 UI / CPU 耗时；API 31+ 额外记录实际 deadline 的 `frameOverrunNanos`。`isJank` 是 JankStats 的启发式判定，不能等价为系统 FrameTimeline 的全部超时帧。统计同时保留所有 `isJank` 帧及正 overrun 帧，按状态分组给出 P50 / P95 / P99 / 最大值。

回调不做文件 IO 或 JSON 编码，后台有界队列容量 4096；写不及则计入 `lost`。单次最多 100000 帧，达到上限标记 `truncated`；最多保留最近 20 个会话文件。强杀来不及结束的文件缺少 summary，报告标为不完整。主动保留这些限制，防止诊断功能造成无限内存或存储增长；需要长时间测试时及时拉取并重新进入 Activity。会话保留与上限意味着它不是永久记录所有历史帧的服务。

本轮自动场景覆盖主 Activity 内的启动、导航、设置、8 次命令全文替换和 2000 条命令的公开库详情；不等于逐字符输入、所有补全前缀或 IME 提交路径均已覆盖。JankStats 的 Activity Window 采集不覆盖 EasyWindow 悬浮窗、IME 服务 Window、外部应用或 Windows；Perfetto 能诊断测试过程中实际出现的 Android 进程窗口及系统调度，未执行的功能仍需追加对应 journey。不要把 Compose 重组次数、桌面 `withFrameNanos` 间隔当作 Android 实际呈现掉帧数据。

## 建立与比较回归基线

只使用完整成功的真机测试建立基线。先检查 `run.json` 的构建、fixture、设备系统、刷新率、温度 / 电池条件和编译模式一致。原始大文件留在 build 产物或 CI artifact；经审阅的性能数值基线可以单独版本化，不能提交伪造或空测量。

```powershell
python scripts/analyze_performance.py --macro PATH/benchmarkData.json --frames PATH/frames.jsonl --run-metadata PATH/run.json --output build/performance/report.json --write-baseline build/performance/baseline.json
python scripts/analyze_performance.py --macro PATH/benchmarkData.json --run-metadata PATH/run.json --baseline build/performance/baseline.json --output build/performance/comparison.json
```

比较要求设备型号、设备代号、SDK、CPU 核心数一致，并拒绝缺失的指标。默认同名毫秒指标 P50 / P95 / P99 增加超过 `max(10%, 1ms)` 返回非零；包括负的 frame overrun。该阈值是初始回归门槛，可按设备测量噪声调整，不是流畅度保证。不能跨设备或把 debug 与 release 数值直接比较。`frames.complete=false` 表示不能据此推断完整掉帧率，原始慢帧仍会保留供定位。

`--require-p99-deadline` 另行要求交互场景 P99 overrun 不超过 0；它和“相对之前没有回归”是两个门槛。系统可能动态调整实际刷新率，不能只把所有帧与 16.7 ms 比较。当前实测及剩余问题见 [performance-results.md](performance-results.md)，审阅过的数值在 `performance/baselines/`。

常规 CI 编译测试驱动、运行汇总脚本及架构测试。正式性能门禁需要固定专用真机；普通云端模拟器上的帧时间不作为产品性能结论。

## 优化顺序

1. 用 trace 区分主线程计算、布局绘制、RenderThread / GPU 和调度等待，确认触发路径。
2. 将解析、IO、JNI 重计算移出 UI 线程，取消过时任务，控制预取与并发。
3. 保持输入会话与转换器稳定；只观察必要状态；长列表使用稳定 key / contentType 和按需渲染。
4. 同一设备重跑同一场景，检查启动、首个可交互内容、P95 / P99 和最大超时帧，不能只通过延迟显示或骨架最短停留来掩盖卡顿。
5. 检查视觉、输入法连续性、动画返回路径和悬浮窗兼容，再更新经过审阅的基线。

参考：[Macrobenchmark 指标](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)、[Baseline Profile 配置](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles)、[JankStats](https://developer.android.com/topic/performance/jankstats)、[Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing)。
