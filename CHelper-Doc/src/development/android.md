# CHelper 安卓接口 / Java 接口文档

如果有不明白的地方，可以直接参考[CHelper 安卓版](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Android)的内核对接方式。

## 编译内核（可选）

在[CHelper 内核文档](./core.md)中已经包含了内核的编译步骤，你甚至可以根据需求定制化内核再进行编译。如果嫌麻烦或者实在不会编译，你也可以直接使用[编译好的内核](https://raw.githubusercontent.com/Yancey2023/CHelper/refs/heads/master/CHelper-Android/app/libs/arm64-v8a/libCHelperAndroid.so)。

在`build.gradle.kts`中添加以下内容：

```kt
sourceSets.all {
    jniLibs.srcDirs("libs")
}
```

然后在`libs`的目录下对应的架构目录添加编译好的动态库文件。

## 数据与主包（可选）

内核（core）已不直接读取"编译好的二进制 `.cpack` 散文件"：安卓把**分层主包** `main-pack.chepack`（zip 格式，内含 manifest.json 与 command/id/json/repeat/text 等 JSON 文件）放进 assets，由 `MainPackProvider` 启动后解包一次并常驻（`app/src/main/assets/main-pack.chepack`）。主包由 `CHelper-Resource/tools/build_main_pack.mjs` 从 `resources/` 六分支生成，并经 `scripts/build.py` 拷入安卓 assets；如果不想自己构建，可以直接使用仓库 assets 里现成的[主包文件](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Android/app/src/main/assets)。主包格式、段装载与合成器的设计详见 [design/extension-pack](../../design/extension-pack/pack-format.md)（[segment-loader](../../design/extension-pack/segment-loader.md)、[composer](../../design/extension-pack/composer.md)、[overview](../../design/extension-pack/overview.md)）。

## 与 java 代码交互

你可以直接使用 CHelper-Android 项目的内核交互相关代码：<https://github.com/Yancey2023/CHelper/tree/master/CHelper-Android/app/src/main/kotlin/yancey/chelper/core>

内核本身不保存任何文本和光标状态，所有的命令相关功能都在`CommandContext`上执行。通过`createContext(command)`把命令文本解析成AST生成独立的命令上下文，然后在没有可变状态的`CommandContext`上执行各种只读操作：

```kt
// 应用内标准路径：主包已由 MainPackProvider 常驻（启动时解包 assets/main-pack.chepack）
MainPackProvider.get(context)?.use { mainPack ->
    // 启用段取 UI 当前选择（如 "beta/vanilla"），可选列表见 mainPack.segments；无拓展包时传 emptyList()
    CHelperCore.compose(mainPack, arrayOf("beta/vanilla"), emptyList()).use { core ->
        core.createContext("give @s stone 12 1").use { context ->
            println(context.structure)
            println(context.getParamHint(8))
            println(context.getSuggestions(context.command!!.length)?.size)
            println(context.syntaxToken?.contentToString())
        }
    }
}
// 自行加载主包（如从文件导入 .chepack）：先解包 zip 为 relPaths/contents 文件集合，
// 再 MainPack.open(relPaths, contents) 打开；切段/换分支只需改 compose 的 enabledSegments 重新合成
```

由于`CommandContext`没有可变状态，同一条命令解析一次后，可以被多个线程同时读取；也可以基于同一个内核为多条命令创建多个`CommandContext`并行工作。`CommandContext`持有资源包的共享引用，即使`CHelperCore`先被`close()`，`CommandContext`依然可用。

需要注意的是，如果你开启了代码混淆，那么你需要在代码混淆配置文件中添加以下内容：

```plain
-keep class yancey.chelper.core.Suggestion{ *; }
-keep class yancey.chelper.core.ErrorReason{ *; }
-keep class yancey.chelper.core.ClickSuggestionResult{ *; }
```
