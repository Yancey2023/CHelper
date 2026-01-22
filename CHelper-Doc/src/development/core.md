# CHelper 内核文档

CHelper 内核包含了语法解析、错误检测、补全提示、命令结构、参数解释、语法高亮、旧命令语法等功能，为不同平台提供同一套内核。我们的理念是通过一个内核打通所有平台，甚至支持网页版。

## 资源包

首先讲一下工作流。我们使用 json 格式写资源包，其中详细的格式请参考[CPack 文档](../cpack/cpack)。内核读取 json 文件后，可以把数据存储到二进制文件中给生产环境进行读取。二进制文件存储时不会记录字段名，存储更加紧凑，读写性能更好。对了方便资源包的读写，我们还设计了一个[序列化框架](https://github.com/Yancey2023/serialization)，它同时支持 json 格式和二进制的读写，为以上工作流提供了统一的接口。

其次是关于资源包的撰写。目前我们维护了 6 个资源包分支，分别是正式版、测试版、中国版以及它们的开启实验性玩法后的分支。关于 ID 的获取，这里非常感谢 ProjectXero 开发的[ID 生成工具](https://github.com/XeroAlpha/caidlist)，它真的帮助了我解决 ID 获取的难题，我在这个项目的基础上进行二次开发，使其支持导出 CHelper 的资源包格式。关于命令的语法声明，我们采用的是语法树的设计，将每个参数当作一个节点，通过树的结构串在了一起。

最后是一些有待改进的地方，目前实在还没有空去处理这些事。目前目标选择器的具体实现是写死在源码中的，这其实不方便资源包的维护，但是如果要支持在资源包中声明某种节点的语法结构，又会为系统引入复杂度。命令语法的 json 结构设计不够直观，而且它完全使用人工编写，没有使用[ID 生成工具](https://github.com/XeroAlpha/caidlist)获取的命令结构，可能会出现由于开发者的粗心大意导致命令语法错误的情况。

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

## 编译

CHelper 使用 CMake 项目构建系统，同时支持 GCC、Clang、MSVC 等主流编译器，也同时支持 Windows、Linux、MacOS 等主流的操作系统。在你电脑安装好 CMake 和任意的 c++ 编译工具链后，即可通过下面的命令构建 CHelper，命令仅作参考：

```bash
git clone https://github.com/Yancey2023/CHelper.git
cd ./CHelper/CHelper-Core
cmake -S . -B ./build -DCMAKE_BUILD_TYPE=Release
cmake --build ./build ---target CHelperCmd
```

关于 Android 的支持，请下载官方的 NDK，用它的工具链编译 CHelper。以下是我使用的编译命令，仅作参考：

```sh
cmake
-D CMAKE_BUILD_TYPE=Release
-D CMAKE_TOOLCHAIN_FILE=D:\\AS\\sdk\ndk\\29.0.14206865\\build\\cmake\\android.toolchain.cmake
-D ANDROID_ABI=arm64-v8a
-D ANDROID_NDK=D:\\AS\sdk\\ndk\\29.0.14206865
-D ANDROID_PLATFORM=android-24
-D CMAKE_ANDROID_ARCH_ABI=arm64-v8a
-D CMAKE_ANDROID_NDK=D:\\AS\\sdk\\ndk\\29.0.14206865
-D CMAKE_EXPORT_COMPILE_COMMANDS=ON
-D CMAKE_MAKE_PROGRAM=D:\\AS\\sdk\\cmake\\3.22.1\\bin\\ninja.exe
-D CMAKE_SYSTEM_NAME=Android
-D CMAKE_SYSTEM_VERSION=24
-B .\\build
-G Ninja

cmake --build .\\build --target CHelperAndroid
```

关于 WebAsembly 的编译，请通过[emsdk](https://github.com/emscripten-core/emsdk)配置好环境，然后执行编译命令。以下是我使用的编译命令，仅作参考：

```sh
git clone https://github.com/emscripten-core/emsdk.git
cd ./emsdk
git pull
./emsdk install latest
./emsdk activate latest
source ./emsdk_env.sh
sudo apt-get install -y cmake ninja-build
cd ..

git clone https://github.com/Yancey2023/CHelper.git
cd ./CHelper/CHelper-Web

cmake -B build -D CMAKE_BUILD_TYPE=MinSizeRel -D CMAKE_TOOLCHAIN_FILE="./emsdk/upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake" -G "Ninja"
cd build
cmake --build . --target CHelperWeb --parallel
emcc libCHelperWeb.a libCHelperNoFilesystemCore.a 3rdparty/fmt/libfmt.a 3rdparty/spdlog/libspdlog.a 3rdparty/xxHash/cmake_unofficial/libxxhash.a -Os -o libCHelperWeb.js -s FILESYSTEM=0 -s DISABLE_EXCEPTION_CATCHING=1 -s ALLOW_MEMORY_GROWTH -s ENVIRONMENT="web" -s EXPORTED_FUNCTIONS="['_init','_release','_onTextChanged','_onSelectionChanged','_getParamHint','_getErrorReasons','_getSuggestionSize','_getSuggestion','_getAllSuggestions','_onSuggestionClick','_getSyntaxTokens','_malloc','_free']" -s WASM=1 -s "EXPORTED_RUNTIME_METHODS=[]"
cd ..

python ./script/patch-wasm.py
```

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
