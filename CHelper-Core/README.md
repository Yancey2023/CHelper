# CHelper-Core 开发说明

核心保持“加载资源 → 创建命令上下文 → 查询编辑功能”的流程。资源包在构建阶段完成节点绑定和缓存初始化；发布给查询线程后，调用方不得修改共享资源或 AST 引用的节点。

## C++ 接口与所有权

`CHelperCore::create*()` 和 `createContext()` 保持返回原始指针，对象生命周期由调用方管理。调用方可以显式释放，也可以自行选择 RAII 包装：

```cpp
CHelper::CHelperCore *core = CHelper::CHelperCore::createByDirectory(path);
if (core == nullptr) {
    return;
}
CHelper::CommandContext *context = core->createContext(u"give @s stone");
{
    auto suggestions = context->getSuggestions(context->getCommand().size());
    // 在这里使用补全结果；先销毁结果，再释放上下文。
}
CHelper::CHelperCore::deleteContext(context);
delete core;
```

上下文内部共享持有资源包，可独立于内核存活。结构、提示和诊断结果独立持有其文本。补全内容直接引用已有资源，高亮结果的 `str` 直接借用命令文本，不为延长生命周期复制数据。使用补全、高亮文本、`getCommand()` 或 `getAstNode()` 的返回值期间，调用方必须保持对应上下文存活，并且不得修改共享内容。原有资源访问接口继续保留，调用方须遵守发布后只读的约定。

补全结果中的 `shared_ptr` 不保证延长其控制块和 PMR 文本所属内存池的生命周期。调用方必须在释放 context 前销毁补全结果容器及从中复制的引用；不能仅停止读取后把容器留到 context 销毁之后。直接从 CPack 取得的 ID、语法图等引用同样要求保留其资源包；其中的派生缓存不保证独立拥有资源池。

Old2New 的修复表由调用方串行初始化，完成初始化后再调用转换接口。同一上下文支持并发查询。释放内核或上下文时，调用方必须确保没有线程仍在使用正在释放的对象。

项目不替换进程的 PMR 默认资源。项目容器在构造时记录所选的实际资源，销毁不依赖线程当前作用域。自定义资源必须活到使用它的对象销毁；审计构建的诊断资源注入还须覆盖所有弱引用的生命周期，普通构建不提供该注入。

CPack 的资源仅用于加载后长期保留的数据。`createContext()` 不向 CPack 的资源分配；context 的资源仅用于构造时解析。所有查询 API（包括 `applySuggestion()` 的临时解析）不得向现有 CPack 或 context 的资源继续分配。返回容器和临时数据使用独立存储，生命周期遵守上面的接口约定。

## 校验与格式

解析器不增加人为的嵌套深度、命令长度或节点数量限制。内部范围等不变量检查只在 `CHelperDebug` 下启用；资源反序列化保留对损坏输入的边界检查。

ID 匹配和补全去重按 XXH3 64 位摘要比较，不追加字符串复查。补全摘要同时包含名称长度、范围、描述是否存在及空格行为，避免不同字段拼接产生相同输入。资源发布前完成 ID 哈希预热，查询期间不写入缓存。

Grammar 加载时拒绝空 AND/OR、重复节点 ID 和不消费输入的递归环；消耗括号等输入的合法递归仍被支持。

`.cpack` 保持现有二进制布局、节点类型编号和字段顺序；不增加新的版本头。反序列化继续检查截断输入和多余尾部字节。

JNI 在边界处理 C++ 异常，并保留已经挂起的 Java 异常。Web 的调用方与实现一同发布，保持现有轻量协议，不增加版本协商和错误查询接口。Web 结果缓冲区只在下一次结果调用前有效，JS 适配器应立即读取结果。

## 构建与验证

Windows 请在 Visual Studio 开发者终端运行。使用兼容 C++23 的编译器和 CMake 3.31 以上版本：

```sh
cmake -S CHelper-Core -B build/core -DCMAKE_BUILD_TYPE=Release
cmake --build build/core --target CHelperTest CHelperResourceGenerator
ctest --test-dir build/core -C Release --output-on-failure
```

源码清单位于 `cmake/Sources.cmake`；增加源文件时显式登记。`BUILD_TESTING=OFF` 可关闭单元测试及 GoogleTest 依赖。Android/WASM 默认不构建原生测试。

性能测试的构建、原生／WASM 比较入口及运行参数见 [基准测试](docs/benchmarking.md)。已确认的问题和证据缺口见 [性能审计](docs/performance-audit.md)，接口与实现的成本判断见 [成本与设计取舍](docs/change-cost-review.md)。完整目录见 [文档索引](docs/README.md)。

资源作用域使用独立构建验证，直接统计池收到的请求（包含已有内存块内的分配），不依赖全局 `new`：

```sh
cmake -S CHelper-Core -B build/scope -DCMAKE_BUILD_TYPE=Release -DCHELPER_ENABLE_RESOURCE_AUDIT=ON
cmake --build build/scope --target CHelperTest
ctest --test-dir build/scope -R ResourceScopeTest --output-on-failure
```

该选项默认关闭。关闭时使用原始 `std::pmr::monotonic_buffer_resource`，不加入计数、包装对象或查询时检查；审计构建用于单线程分配归属测试，不用于性能计时。

`CHELPER_ENABLE_SANITIZERS=ON` 在 GCC/Clang 上启用 ASan/UBSan，在 MSVC 上启用 ASan。使用 Clang 的 Linux/macOS 环境可构建 libFuzzer：

```sh
cmake -S CHelper-Core -B build/fuzz -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_BUILD_TYPE=RelWithDebInfo -DBUILD_TESTING=OFF -DCHELPER_BUILD_FUZZERS=ON -DCHELPER_ENABLE_SANITIZERS=ON
cmake --build build/fuzz --target CHelperFuzz
build/fuzz/CHelperFuzz -max_total_time=60 -max_len=65536
```

修改 C++ 后使用仓库 `.clang-format`，CMake 文件使用 `cmake-format`。CI 分别覆盖原生测试、Android/WASM 构建，以及 sanitizer 和 fuzz 冒烟检查。
