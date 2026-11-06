# 基准测试

以下命令从仓库根目录执行。Windows 使用 Visual Studio 开发者终端；示例采用 Ninja 单配置构建，方便比较脚本直接读取构建目录中的可执行文件与 `CMakeCache.txt`。

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

准备好两份 Release 构建后比较；两边均须包含上述两个目标，使用相同基准源码、资源、工具链和编译选项：

```sh
python scripts/benchmark_core.py build/core-before build/core-after --output build/native-comparison --rounds 5 --long-repeat 200 --long-warmup 1000
```

[比较脚本](../../scripts/benchmark_core.py)保存程序摘要、配置、可用的编译信息和原始日志；配置不一致时拒绝比较。当前源码摘要不能证明旧二进制的构建来源，缺失的历史构建信息也不能由新运行补出。

计时构建关闭 `CHELPER_ENABLE_SANITIZERS` 和 `CHELPER_ENABLE_RESOURCE_AUDIT`。资源归属检查、sanitizer 和 fuzz 的构建方法见 [Core 开发说明](../README.md#构建与验证)。

## WASM 比较

```sh
python scripts/benchmark_web_core.py before.wasm after.wasm resource.cpack --output build/web-comparison --iterations 200 --rounds 5
```

[Python 启动器](../../scripts/benchmark_web_core.py)负责 Windows 进程策略和 [Node 测量工具](../../scripts/benchmark_web_core.mjs)的握手。默认预先完成 TurboFan 编译；`--v8-default` 保留 V8 默认编译方式，`--cpu` 指定 Windows 逻辑处理器。

工具核对独立实例、结果指纹，记录每批缺页和 GC 重叠；同一路径也创建两份实例。直接运行 `.mjs` 不验证进程策略，不能与启动器管理的运行视为相同条件。

## 参数与观测边界

| 参数                                    | 适用入口               | 用途                                                         |
| --------------------------------------- | ---------------------- | ------------------------------------------------------------ |
| `--windows-qos high/system/eco`         | 两个 Python 启动器     | Windows 执行速度策略；Windows 默认 high，其它平台使用 system |
| `CHELPER_BENCH_WINDOWS_QOS`             | 直接运行原生基准       | 显式选择策略；未设置时继承原策略                             |
| `--long-only`                           | 原生比较脚本           | 仅执行长命令比较                                             |
| `--long-warmup`                         | 原生比较脚本           | 长命令预热次数，默认 1,000                                   |
| `--require-long-fault-free`             | 原生比较脚本           | 测量中出现缺页时，先保存结果再以非零状态退出                 |
| `LONG_WARMUP`、`LONG_TRACK_PAGE_FAULTS` | 直接运行原生长命令用例 | 控制预热及缺页统计                                           |
| `LONG_RECORD_SAMPLES=1`                 | 原生长命令用例         | 输出逐次时间戳、阶段耗时、缺页及 Windows 线程周期；默认关闭  |

策略设置、回读及恢复由工具管理，失败时保留观测与日志。端点验证不保证测量期间没有策略变化；绑定逻辑 CPU 不保证同核心 SMT 邻居空闲。预热次数和零缺页也不代表已完全隔离运行环境。

逐样本记录的存储预先分配，线程周期读取、写记录和输出位于被测总时间窗口之外；这些辅助观察仍可能改变进程状态。缺页统计包围辅助查询，不等于只统计 Core。线程周期不是 PMU 缓存／分支事件或 CPU 频率。

原生耗时输出保留九位毫秒小数，减少文本量化造成的百分比放大。输出位数和时间戳单位不代表计时器具有相应分辨率。比较两边必须使用相同的记录设置。

## 如何解读结果

保留逐轮分布、构建身份和语义指纹，并用同一程序的 A/A 对照评估测量方法。A/A 差值不是可接受退化额度，也不是环境影响的上限。

分配、计时和插桩分别观察。减少一次复制、引用操作、指令或栈字节，可以证明工作量或容量发生变化；整体延迟仍需在相应调用环境下验证。

不同资源内容、资产时点、编译参数或运行器的数据不能直接相减归因。API 组合基准也不等于包含 JNI、WASM 传输和 UI 更新的端到端延迟。当前可支持的结论见 [性能审计](performance-audit.md)。
