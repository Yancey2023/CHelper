# Android 性能基准与掉帧诊断

目标是在固定真机、刷新率和负载条件下持续降低超时帧，并阻止回归。不能保证任意设备、后台负载、温度和系统调度条件下零掉帧；测试通过也不代表所有功能均没有卡顿。

## 两种基线

- **性能回归基线**：Macrobenchmark 的实际测量 JSON，包含启动耗时及帧 CPU 耗时、帧超时分布。保留原始 Perfetto trace，定位 UI、RenderThread、原生调用、调度及 GPU 等不同原因。
- **Baseline Profile**：官方 Baseline Profile 工具在真实关键路径执行后生成 ART 预编译规则，通过 ProfileInstaller 分发。可另外采集用于 DEX 布局的 Startup Profile；不手写或复制不相关规则，不把“接入插件”当成已生成应用 profile。

`apps/benchmark` 是 Android 测试驱动模块，不包含业务实现，也不会进入 Android / Windows 的运行时依赖图。业务流程调用真实的共享 UI、ViewModel、Repository 和 JNI；`apps/android/src/benchmark` 只替换 HTTP 引擎，返回固定分页数据和 2000 条命令，详情响应延迟 80 ms。它隔离网络波动，不替代真实网络、图片和第三方服务的专项测试。性能变体使用 debug 的无分析上报实现，排除 Umeng；不能直接把它的结果作为包含分析 SDK 的正式版全量成本。

生成的 `benchmarkRelease` 使用 release 的 R8 / 资源压缩配置，`nonMinifiedRelease` 用于采样 profile。所有性能专用变体包名都是 `yancey.chelper.benchmark`，运行器为 `yancey.chelper.benchmark.test`，不覆盖 `yancey.chelper`、`.debug`、`.beta`。普通构建不连接设备，仍会打包依赖库提供的 profile。

## 在真机执行

从仓库根目录、**提权的终端**执行。固定一台已授权的真机，解锁屏幕，保持相同刷新率、动画比例、电源模式、IME 和字体大小。测量期间不操作设备，避免设备过热和后台任务。不要禁用动画来掩盖页面切换卡顿。无需 root；不抑制 Macrobenchmark 的设备、温度或可调试构建校验错误。

```powershell
mise run android:gradle :apps:android:assembleBenchmarkRelease :apps:benchmark:assembleBenchmarkRelease
adb -s DEVICE_SERIAL install -r PATH_TO_BENCHMARK_APP_APK
adb -s DEVICE_SERIAL install -r PATH_TO_BENCHMARK_RUNNER_APK
adb -s DEVICE_SERIAL shell am instrument -w -r -e class yancey.chelper.benchmark.FrameBenchmarks yancey.chelper.benchmark.test/androidx.test.runner.AndroidJUnitRunner
```

用同一运行器单独执行 `StartupBenchmarks` 测量冷启动，采集 Baseline Profile 时构建对应的 `nonMinifiedRelease` 变体并运行 `BaselineProfileGenerator`。安装失败后先检查设备和应用签名。采集与正式发布时，检查仪器测试结果和 APK 中实际打包的 profile。

`longCommandScroll` 使用包含 200 个 rawtext 项的真实命令，准备阶段通过设置界面断言语法高亮已启用且命令长度低于自动关闭阈值，再等待语法颜色稳定，再测量三个向右和三个向左的滑动。Android 仪器测试 `CommandEditorRenderingInstrumentedTest` 另提供 19,000 字符的成对软件绘制探针：从真实编辑器取出当前样式，与上一版 768 字符缓冲交替绘制，预热后记录 31 个样本的中位数和 P95。长命令场景使用 `offline-2000-long-highlight-v1` 夹具标识，不能与未验证高亮状态的历史长命令采样比较。该探针用于定位颜色样式成本，不代表整帧耗时或零掉帧；整帧判断使用 Release Macrobenchmark 的原始帧指标。

使用 Macrobenchmark 生成的 JSON 和 Perfetto trace 比较结果。单独诊断帧数据时启用 `-PcollectFrameMetrics=true`。

在真机上采集并启用项目自己的 Baseline Profile：

```powershell
mise run android:gradle :apps:android:generateBaselineProfile -PlocalBaselineProfile=true
mise run android:gradle :apps:android:assembleBenchmarkRelease :apps:benchmark:assembleBenchmarkRelease -PlocalBaselineProfile=true
```

插件将采集结果写入 Git 忽略的 `apps/android/local-baseline-profile/`，并过滤测试 Application、MockEngine 和 tracing 工具自身的规则。若仍有旧的 `apps/android/src/main/baseline-prof.txt`，将它移到该目录，避免 Android Gradle 插件自动读取旧路径。安装重新构建的 APK 后运行 `PackagedProfileBenchmark`。省略 `-PlocalBaselineProfile=true` 时，普通构建只使用依赖库提供的规则。

`BaselineProfileGenerator` 提供完整关键路径和独立启动布局的测试用例。M098FE 上的独立启动捕获持续返回空规则，历史测量使用了已成功采集的 Baseline Profile。冷启动使用系统 Perfetto，交互额外开启 Compose SDK tracing，避免该设备冷启动时 SDK tracing 广播失败。

## 逐帧采集与覆盖边界

Android Debug 默认开启 JankStats，性能变体及正式版默认关闭。可用 `-PcollectFrameMetrics=true` 对正式版进行专门诊断。采集只写设备本地，不上传、不记录命令原文、账号、URL 或命令库 ID。共享 `PerformanceState` 只描述页面、文档阶段、高亮启用、命令解析及设置滚动状态；Windows 默认为空实现。Android 使用官方 `PerformanceMetricsState` 关联这些状态。

每个 Activity 从 `onStart` 到 `onStop` 采集其 Window 的所有回调帧，并在停止后排空队列。API 24+ 记录 UI / CPU 耗时；API 31+ 额外记录实际 deadline 的 `frameOverrunNanos`。`isJank` 是 JankStats 的启发式判定，不能等价为系统 FrameTimeline 的全部超时帧。统计同时保留所有 `isJank` 帧及正 overrun 帧，按状态分组给出 P50 / P95 / P99 / 最大值。

回调不做文件 IO 或 JSON 编码，后台有界队列容量 4096；写不及则计入 `lost`。单次最多 100000 帧，达到上限标记 `truncated`；最多保留最近 20 个会话文件。强杀来不及结束的文件缺少 summary，报告标为不完整。主动保留这些限制，防止诊断功能造成无限内存或存储增长；需要长时间测试时及时拉取并重新进入 Activity。会话保留与上限意味着它不是永久记录所有历史帧的服务。

本轮自动场景覆盖主 Activity 内的启动、导航、设置、8 次命令全文替换和 2000 条命令的公开库详情；不等于逐字符输入、所有补全前缀或 IME 提交路径均已覆盖。JankStats 的 Activity Window 采集不覆盖 EasyWindow 悬浮窗、IME 服务 Window、外部应用或 Windows；Perfetto 能诊断测试过程中实际出现的 Android 进程窗口及系统调度，未执行的功能仍需追加对应 journey。不要把 Compose 重组次数、桌面 `withFrameNanos` 间隔当作 Android 实际呈现掉帧数据。

## 建立与比较回归基线

只使用完整成功的真机测试建立基线。先记录构建、夹具、设备系统、刷新率、温度、电池条件和编译模式。原始大文件留在 build 产物或 CI artifact；经审阅的性能数值基线可以单独版本化，不能提交伪造或空测量。

比较要求设备型号、设备代号、SDK、CPU 核心数和编译模式一致。现有已审阅数据在 `performance/baselines/`，原始指标的解读见 [performance-results.md](performance-results.md)。

CI 编译 Android 测试驱动。正式性能测量在固定专用真机上运行。

## 优化顺序

1. 用 trace 区分主线程计算、布局绘制、RenderThread / GPU 和调度等待，确认触发路径。
2. 将解析、IO、JNI 重计算移出 UI 线程，取消过时任务，控制预取与并发。
3. 保持输入会话与转换器稳定；只观察必要状态；长列表使用稳定 key / contentType 和按需渲染。
4. 同一设备重跑同一场景，检查启动、首个可交互内容、P95 / P99 和最大超时帧，不能只通过延迟显示或骨架最短停留来掩盖卡顿。
5. 检查视觉、输入法连续性、动画返回路径和悬浮窗兼容，再更新经过审阅的基线。

参考：[Macrobenchmark 指标](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)、[Baseline Profile 配置](https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles)、[JankStats](https://developer.android.com/topic/performance/jankstats)、[Compose tracing](https://developer.android.com/develop/ui/compose/tooling/tracing)。
