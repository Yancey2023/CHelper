# CHelper Compose / JVM 接口文档

如果有不明白的地方，可以直接参考[CHelper 安卓版](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose)的内核对接方式。

## 编译内核（可选）

在[CHelper 内核文档](./core.md)中已经包含了内核的编译步骤，你甚至可以根据需求定制化内核再进行编译。如果嫌麻烦或者实在不会编译，你也可以直接使用[编译好的内核](https://raw.githubusercontent.com/Yancey2023/CHelper/refs/heads/master/CHelper-Compose/apps/android/libs/arm64-v8a/libCHelperAndroid.so)。

在`build.gradle.kts`中添加以下内容：

```kt
sourceSets.all {
    jniLibs.srcDirs("libs")
}
```

然后在`libs`的目录下对应的架构目录添加编译好的动态库文件。

## 资源包生成（可选）

通过运行代码可以读取 json 文件生成更加利于程序读写的二进制文件资源包。如果嫌麻烦或者不会生成，你也可以直接使用[生成好的资源包](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose/resources/cpack)。

## 与 java 代码交互

Android 和 Windows 共享同一份 JVM 内核绑定：<https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose/shared/native/src/main/kotlin/yancey/chelper/core>。Android 从 assets 加载资源包，Windows 从文件加载资源包。项目构建和打包方式见 [CHelper-Compose README](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Compose)。

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
