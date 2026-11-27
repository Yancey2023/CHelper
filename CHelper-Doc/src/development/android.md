# CHelper Compose / JVM 接口文档

如果有不明白的地方，可以直接参考[CHelper 安卓版](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose)的内核对接方式。

## 构建 Android 客户端

从仓库根目录运行 `mise run android:gradle :apps:android:assembleDebug`。mise 会自动安装锁定版本的 NDK，并用 CMake 和 Ninja 从当前 Core 源码生成 ARM64 JNI 库。Gradle 随后打包 JNI 库和资源包。单独构建时可以运行 `mise run core:android` 或 `mise run resources:pack`。

## 与 java 代码交互

Android 和 Windows 共享同一份 JVM 内核绑定：<https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose/platform/jvm/src/main/kotlin/yancey/chelper/core>。Android 从 assets 加载资源包，Windows 从文件加载资源包。项目构建和打包方式见 [CHelper-Compose README](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose)。

内核本身不保存任何文本和光标状态，所有的命令相关功能都在`CommandContext`上执行。通过`createContext(command)`把命令文本解析成AST生成独立的命令上下文，然后在没有可变状态的`CommandContext`上执行各种只读操作：

```kt
CHelperCore.fromAssets(assetManager, cpackPath).use { core ->
    core.createContext("give @s stone 12 1").use { context ->
        println(context.structure)
        println(context.getParamHint(8))
        println(context.getSuggestions(context.command!!.length)?.size)
        println(context.syntaxToken?.contentToString())
    }
}
```

由于`CommandContext`没有可变状态，同一条命令解析一次后，可以被多个线程同时读取；也可以基于同一个内核为多条命令创建多个`CommandContext`并行工作。`CommandContext`持有资源包的共享引用，即使`CHelperCore`先被`close()`，`CommandContext`依然可用。

需要注意的是，如果你开启了代码混淆，那么你需要在代码混淆配置文件中添加以下内容：

```plain
-keep class yancey.chelper.core.Suggestion{ *; }
-keep class yancey.chelper.core.ErrorReason{ *; }
-keep class yancey.chelper.core.ClickSuggestionResult{ *; }
```
