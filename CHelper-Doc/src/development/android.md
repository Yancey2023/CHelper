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

## 资源包生成（可选）

通过运行代码可以读取 json 文件生成更加利于程序读写的二进制文件资源包。如果嫌麻烦或者不会生成，你也可以直接使用[生成好的资源包](https://github.com/Yancey2023/CHelper/tree/master/CHelper-Android/app/src/main/assets/cpack)。

## 与 java 代码交互

你可以直接使用 CHelper-Android 项目的内核交互相关代码：<https://github.com/Yancey2023/CHelper/tree/master/CHelper-Android/app/src/main/java/yancey/chelper/core>

需要注意的是，如果你开启了代码混淆，那么你需要在代码混淆配置文件中添加以下内容：

```plain
-keep class yancey.chelper.core.Suggestion{ *; }
-keep class yancey.chelper.core.ErrorReason{ *; }
-keep class yancey.chelper.core.ClickSuggestionResult{ *; }
```
