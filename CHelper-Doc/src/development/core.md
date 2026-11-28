# CHelper 内核文档

CHelper 内核包含了语法解析、错误检测、补全提示、命令结构、参数解释、语法高亮、旧命令语法等功能，为不同平台提供同一套内核。我们的理念是通过一个内核打通所有平台，甚至支持网页版。

## 资源包

首先讲一下工作流。我们使用 json 格式写资源包，其中详细的格式请参考[CPack 文档](../cpack/cpack)。内核读取 json 文件后，可以把数据存储到二进制文件中给生产环境进行读取。二进制文件存储时不会记录字段名，存储更加紧凑，读写性能更好。

其次是关于资源包的撰写。目前我们维护了 6 个资源包分支，分别是正式版、测试版、中国版以及它们的开启实验性玩法后的分支。这里非常感谢 ProjectXero 开发的[ID 生成工具](https://github.com/XeroAlpha/caidlist)，它真的帮助了我解决 ID 获取的难题，我在这个项目的基础上进行二次开发，使其支持导出 CHelper 的资源包格式。

最后是一些有待改进的地方，目前实在还没有空去处理这些事。目前目标选择器的具体实现是写死在源码中的，这其实不方便资源包的维护，但是如果要支持在资源包中声明某种节点的语法结构，又会为系统引入复杂度。

## 分词器

当我们得到一个命令的时候，我们使用 Lexer 将代码先分割成一个个 token。

比如输入的命令是`setblock ~~~ candle_cake[lit=`，那么分词的效果就是这样的：

```js
[STRING] 0 setblock
[WHITE_SPACE] 8
[SYMBOL] 9 ~
[SYMBOL] 10 ~
[SYMBOL] 11 ~
[WHITE_SPACE] 12
[STRING] 13 candle_cake
[SYMBOL] 24 [
[STRING] 25 lit
[SYMBOL] 28 =
```

这里的每一行代表一个 token 的数据，每个 token 包含类型、起始位置和文本。token 有 5 种类型，它们分别是字符串、数字、符号、空格、换行符。其中，字符串如果是双引号开头就要做到双引号结尾并且支持转义字符。

## AST 抽象语法树

首先是 AST 的设计。对于每个 AST 节点，我都加入了一个标志位用来标注子节点的关系是和关系还是或关系，如果是或关系，还要去判断哪个子节点才是最佳的。我还在每个节点中加入一个 ID 来标识特殊节点，方便了其它功能在使用 AST 节点时能快速识别是否是需要特殊处理的 AST 节点。

其次是关于 AST 的构建。通过遍历资源包的命令语法树，从根节点开始构建 AST，如果当前命令节点构建的 AST 没有语法错误，就继续传播下去，在子节点继续构建 AST，直到 tokens 被读取完毕或存在语法错误才停止遍历子节点。

## 基于 AST 的功能开发

通过遍历 AST，即可完成补全提示、命令结构、参数解释和语法高亮。所有功能采用模块化设计，方便了功能的维护和扩展。

## 命令上下文

内核本身不保存任何文本和光标状态，所有的命令相关功能都在`CommandContext`上执行。通过命令文本解析出AST并生成独立的命令上下文，它没有任何可变状态，所有的操作都通过参数传入位置：

- `getStructure()`：获取命令结构
- `getParamHint(index)`：获取指定位置的参数注释
- `getSuggestions(index)`：获取指定位置的补全建议
- `getSyntaxResult()`：获取语法高亮
- `getErrorReasons()`：获取错误原因
- `getNodeCount()`：获取最佳解析路径中已经匹配的命令语义节点数量
- `applySuggestion(index, which)`：把补全建议应用到命令文本，不修改自身状态

`CHelperCore`负责持有资源包(CPack)，通过`CHelperCore::createContext`可以创建`CommandContext`。由于`CommandContext`没有可变状态，同一条命令解析一次后，可以被多个线程同时读取；也可以基于同一个资源包为多条命令创建多个`CommandContext`并行工作。`CommandContext`持有资源包的共享引用，即使`CHelperCore`先被释放，`CommandContext`依然可用。Android 和 Web 接口中的`CommandContext`都是对这个类的封装。

## 编译

从仓库根目录运行 `mise install --locked`。内核使用 C++23，需要支持 `std::expected` 的 C++ 工具链。

```bash
mise run core:test       # 构建并运行宿主内核测试
mise run core:android    # 生成 Android ARM64 JNI 库
mise run core:wasm       # 生成浏览器 WASM 和 JavaScript 适配器
mise run core:qt         # 构建 Qt 调试应用
mise run resources:pack  # 生成各客户端使用的资源包
```

CMake Presets 定义宿主、Android 和 Web 的编译配置。Android 和 Web 任务会按需准备锁定版本的 NDK 或 Emscripten SDK。CMake 的构建目录位于仓库根目录下的 `build`。

Qt 构建需要 Qt 6。`mise run core:qt` 会自动准备 `build/artifacts/resources/cpack` 下的 CPack，并将其嵌入应用。`mise run core:qt:debug` 构建的应用在运行时直接读取源 JSON，便于调试资源数据。

## 加入我们

我们目前只是一个小团队，非常需要您的加入。如果您掌握以下任何一项技能或者对其感兴趣，都可以尝试加入我们：

1. <b>维护资源包：</b>我们的资源包使用 json 文件编写，易于扩展，仅需少量修改就可以适配新版本我的世界。
2. <b>编写网页：</b>使用 vue3 框架开发网页版命令助手。
3. <b>维护安卓界面代码：</b>为我们的安卓软件加入更多功能。
4. <b>维护内核代码：</b>提高代码质量，加入更多功能，对外提供更多接口。

我们可以提供的：

1. <b>友好的交流氛围：</b>如果对我们的代码有感到疑惑的地方，您可以随时问我们，我们很乐意解答。
2. <b>贡献署名：</b>我们可以在软件的贡献名单中加上您的名字。

## 二次开发

我们鼓励大家使用我们的内核进行二次的开发。因为我们使用 C++ 制作内核，所以它可以与任何一门编程语言进行交互。其中，C/C++、Java 和 JavaScript 有官方提供的接口，您可以使用这些接口进行二次开发。

## 代码贡献

<b>第一步：</b>把我们的 GitHub 仓库克隆到您的 GitHub 账号下。

<b>第二步：</b>对您克隆的仓库进行修改。

<b>第三步：</b>向我们的代码仓库发送一份 PR (Pull Request)，我们会审查代码，代码审核通过后您的代码将会合并到我们仓库中。
