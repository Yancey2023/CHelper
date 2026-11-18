# 2026-11-15 真机性能记录

## 2026-11-18 长命令单行滚动

在同一台 M098FE 上重跑 `longCommandScroll`。每组先预热 3 次，再测量 5 次，使用相同的 4,927 字符命令、已开启的语法高亮和独立 R8 性能包。CPU 耗时使用 Macrobenchmark 的 `frameDurationCpuMs`，包含 UI 线程与 RenderThread；超时比例以 FrameTimeline 的实际期限计算。

| 实现 | 帧数 | CPU P50 | CPU P95 | CPU P99 | 超时 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 已开启高亮的基线 | 2,642 | 10.372 ms | 27.196 ms | 68.944 ms | 53.44% |
| 保留 Foundation 组合注释和选区绘制的字形缓存 | 2,378 | 10.632 ms | 23.475 ms | 28.705 ms | 55.85% |
| 光标离开视口时跳过整个原生绘制（已撤回） | 2,191 | 16.243 ms | 26.723 ms | 34.928 ms | 74.90% |
| 字形缓存加 Foundation 光标矩形缓存 | 3,153 | 9.109 ms | 21.092 ms | 25.328 ms | 35.43% |
| 在字形缓存路径跳过原生文字绘制（已撤回） | 3,108 | 8.664 ms | 21.059 ms | 25.092 ms | 36.07% |
| 分块查询视口并加速 Foundation 文本比较 | 3,268 | 8.711 ms | 20.663 ms | 22.815 ms | 35.01% |
| 在分块与文本比较优化上跳过透明文字提交（已撤回） | 3,254 | 8.590 ms | 20.717 ms | 22.851 ms | 36.14% |

方法采样发现 Foundation 在滚动期间重复查询同一排版和偏移的光标位置，并逐字符比较相同的不可变文本。分块视口查询在共享回归中将模拟滚动的字宽查询从 2,135 次降至 154 次。真机的像素回归、滚动与样式更新、等长文字替换、原生组合下划线和输入连接连续性均通过。

分块查询版本的首轮 Perfetto 轨迹中，达到 18 ms 的 48 个绘制片段平均耗时 19.40 ms，其中主线程已获调度的执行时间约 18.99 ms。去掉透明文字提交之后，P95 与超时比例均未改善，因此保留分块查询和文本比较版本。与已开启高亮的基线相比，完整帧 CPU P95 降低 24.0%，仍高于 5 ms。各轮 JSON、日志与轨迹位于本机 `build/performance/highlight-scroll-32-v9-native-annotations/` 至 `highlight-scroll-32-v14-paint-mask/`。

### 原生 Canvas 补测

在同一条长命令上进行两次 12 秒的 Simpleperf 采样。透明文字提交改到 Android Canvas 入口过滤后，`Canvas::drawText` 及其下游字形排版在进程采样中的占比由 54.86% 降到 1.41%。这两份记录保存在本机 `build/performance/v13-native-report.txt` 和 `v15-native-report.txt`，用于定位原生绘制热点。

| 实现 | 帧数 | CPU P50 | CPU P95 | CPU P99 | 超时 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 在 Canvas 入口跳过标记的透明文字 | 2,644 | 9.035 ms | 24.374 ms | 28.078 ms | 40.81% |
| 移除诊断代码后的 Canvas 版本 | 2,816 | 8.603 ms | 23.239 ms | 28.003 ms | 40.63% |
| 按正文内容保留图层路径并过滤透明文字提交 | 2,730 | 1.971 ms | 3.941 ms | 12.672 ms | 1.90% |

两版在相同的长命令滚动基准中均高于 20.663 ms 基线。清理后的版本通过高亮像素、关闭高亮后的普通文字像素、选区以及原生输入法下划线和输入连接连续性检查。完整报告位于本机 `build/performance/highlight-scroll-32-v15-native-canvas/` 和 `highlight-scroll-32-v16-final-canvas/`。

保留版本按正文内容判断是否可以复用高亮图层，避免选区或组合注释更新产生的新文本快照使透明前景和有色图层切换。滚动基准在计时前确认命令进入高亮图层路径。五次测量各自的 CPU P95 为 3.55、4.01、4.19、3.89 和 3.76 ms。合计 52 帧超过实际期限，整帧 CPU P95 达到 5 ms 目标。三项 Android 编辑器回归、132 项共享 UI 测试和 99 项 Windows 测试通过。报告位于本机 `build/performance/highlight-scroll-32-v17-stable-mask/`。

同一测试运行器复测先前 20.663 ms 的 APK 时，准备阶段在 15 秒内等不到 `command-glyph-cache`，采样于计时前停止。两版在新版就绪条件下没有可配对的耗时数据，20.663 ms 保留为旧测试流程下的历史值。失败日志位于本机 `build/performance/highlight-scroll-32-v13-readiness-control/`。手机已恢复为通过测量的优化版本，息屏设置恢复为 600,000 ms。

## 2026-11-16 长命令高亮补测

### 当日性能候选

针对约 27 ms 的完整帧 P95，补充了 ART 方法采样。采样中的原生字宽查询约 1.11 s 来自视口命中路径；该结果用于定位热点，不作为无采样开销的性能成绩。

纯左向字符位置缓存按底层字形布局复用后，5 次完整测量（`build/performance/highlight-scroll-32-v6b-paragraph-cache/`）共 2,474 帧，CPU P50 / P95 / P99 为 10.659 / 28.113 / 34.850 ms，overrun P99 为 +29.374 ms，超时 50.44%。它降低了尾部峰值，但没有解决 P95。

首个字形显示列表缓存候选仍在选择或组合输入时回退原生绘制，5 次测量（`build/performance/highlight-scroll-32-v7-glyph-cache/`）共 2,549 帧，CPU P50 / P95 / P99 为 10.607 / 26.751 / 34.958 ms，超时 52.77%；不足以认定有效提升。其 Android 像素、字号、主题和原生组合输入连接回归通过。缓存层默认空裁剪轮廓曾造成文字缺失，已通过真实像素回归发现并改为宿主视口裁剪。

当日的候选进一步保留 Foundation 的组合注释和选区绘制顺序，使选择与组合输入也能复用字形缓存。该候选当日通过共享测试和 Windows 的 6 项编辑器回归，Android 真机结果见 2026-11-18 记录。手机临时息屏设置已恢复为原来的 600,000 ms。

高亮默认阈值由 4,000 调整为 400,000 字符，设置上限由 100,000 调整为 10,000,000。已有阈值通过版本化迁移放大 100 倍（0 保持关闭），以后重新启动不会再次放大。实际 JNI 测试验证了超过 40,000 字符的命令仍返回覆盖全文的颜色 token；共享测试覆盖旧设置迁移、迁移后的用户修改和严格小于阈值的启用边界。

同一台 M098FE 的软件绘制对照使用 19,000 字符、相同字体和可见颜色，每组预热 8 次，交替顺序采样 31 次。参考窗口产生 160 个样式区间，当前视口窗口产生 20 个；绘制中位数从 8.588 ms 降到 2.284 ms，约 3.76 倍，P95 从 11.269 ms 降到 2.441 ms。这只测量文本绘制，不代表完整帧耗时，也不能与旧 Android 10 手机的 16 ms 直接比较。

启用真实高亮后的 `longCommandScroll` 使用独立 fixture `offline-2000-long-highlight-v1`，准备阶段检查高亮开关和实际长度阈值。5 次测量共 2,642 帧，CPU P50 / P95 / P99 分别为 10.372 / 27.196 / 68.944 ms，overrun P99 为 +73.179 ms，超时 1,412 帧（53.44%）。原始证据位于本机 `build/performance/highlight-scroll-32-v2-enabled/`。完整滚动场景仍需优化，不能据文本绘制提升宣称无掉帧。

更早的 `highlight-scroll-32-v1` 超过旧阈值、实际未开启高亮，不作为高亮性能基线。额外图层候选在解锁后完成 5 次测量，共 2,637 帧，CPU P50 / P95 / P99 为 10.569 / 27.059 / 70.478 ms，overrun P99 为 +74.545 ms，超时 1,360 帧（51.57%）。它没有显示稳定的帧耗时收益，已撤回；证据位于 `build/performance/highlight-scroll-32-v4-layer-awake/`。

最终保留版本在同一手机通过长命令编辑器测试，覆盖滚动视口颜色、深浅主题、输入法会话连续性、Unicode 输入撤销和切换多行模式。额外图层未进入交付 APK；手机息屏时间及常亮设置均已恢复到测试前的值。

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
