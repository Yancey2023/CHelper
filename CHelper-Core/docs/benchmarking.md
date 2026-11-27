# 基准测试

以下命令从仓库根目录执行。Windows 使用 Visual Studio 开发者终端；示例采用 Ninja 单配置构建，构建目录保存可执行文件和 `CMakeCache.txt`。

## 原生构建与比较

```sh
cmake -S CHelper-Core -B build/core-bench -G Ninja -DCMAKE_BUILD_TYPE=Release -DCHELPER_BUILD_BENCHMARKS=ON
cmake --build build/core-bench --target CHelperLatencyBench CHelperBench
build/core-bench/CHelperLatencyBench --gtest_filter=Bench.CommandContextOperations
```

两个目标用途不同：

| 目标                  | 用途                              | 限制                                               |
| --------------------- | --------------------------------- | -------------------------------------------------- |
| `CHelperLatencyBench` | 使用普通分配器计时                | 不启用全局分配拦截或 HeapProfile 存活表            |
| `CHelperBench`        | 观察全局 `new`／`delete` 和存活量 | PMR 可能绕过钩子，`new/net` 不代表完整堆内存或 RSS |

比较两份 Release 构建时，使用同一基准源码、资源、工具链、编译选项和运行参数，并保留两边的原始日志。计时构建关闭 `CHELPER_ENABLE_SANITIZERS` 和 `CHELPER_ENABLE_RESOURCE_AUDIT`。资源归属检查、sanitizer 和 fuzz 的构建方法见 [Core 开发说明](../README.md#构建与验证)。

## 参数与观测边界

| 环境变量 | 用途 |
| --- | --- |
| `CHELPER_BENCH_WINDOWS_QOS` | 显式选择 Windows 执行速度策略 |
| `LONG_WARMUP`、`LONG_TRACK_PAGE_FAULTS` | 控制长命令的预热与缺页统计 |
| `LONG_RECORD_SAMPLES=1` | 输出长命令的逐次时间戳、阶段耗时、缺页及 Windows 线程周期 |

逐样本记录的存储预先分配，线程周期读取、写记录和输出位于被测总时间窗口之外；这些辅助观察仍可能改变进程状态。缺页统计包围辅助查询，不等于只统计 Core。线程周期不是 PMU 缓存／分支事件或 CPU 频率。

原生耗时输出保留九位毫秒小数，减少文本量化造成的百分比放大。输出位数和时间戳单位不代表计时器具有相应分辨率。比较两边必须使用相同的记录设置。

## 如何解读结果

保留逐轮分布、构建身份和语义指纹，并用同一程序的 A/A 对照评估测量方法。A/A 差值不是可接受退化额度，也不是环境影响的上限。

分配、计时和插桩分别观察。减少一次复制、引用操作、指令或栈字节，可以证明工作量或容量发生变化；整体延迟仍需在相应调用环境下验证。

不同资源内容、资产时点、编译参数或运行器的数据不能直接相减归因。API 组合基准也不等于包含 JNI、WASM 传输和 UI 更新的端到端延迟。当前可支持的结论见 [性能审计](performance-audit.md)。
