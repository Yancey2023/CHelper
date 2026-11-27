# CHelper Compose

CHelper 的 Kotlin / Compose Multiplatform 客户端，支持 Android 7.0+ 和 Windows 10/11 x64。两端复用同一套功能界面、ViewModel、导航、持久化仓库、网络协议和 JNI 命令引擎。

## 项目结构

```text
CHelper-Compose/
├── apps/
│   ├── android/        # Application / Activity / Service、系统适配与 Android 打包
│   └── desktop/        # Window 入口、Windows 系统适配与安装包
├── shared/
│   ├── domain/         # 业务模型、仓库接口、编辑版本与同步规则
│   ├── data/           # DataStore、迁移、文档编解码、网络与认证实现
│   └── ui/             # app / navigation / platform / designsystem / feature
├── platform/jvm/       # JNI ABI 与适配、Crunch、OkHttp 引擎（两宿主复用）
├── build-logic/        # Kotlin / Android 构建约定
├── resources/          # 内核资源包、文档、Rawtext 索引
├── docs/architecture.md
└── gradle/libs.versions.toml
```

[结构对比、模块依赖与代码归属规则](docs/architecture.md)记录了原 Android 单模块、首次跨平台迁移和本次目录重构之间的变化。

[平台差异盘点与迁移调研](docs/platform-differences.md)列出了当前宿主实现、可交给框架或库的能力、迁移限制和建议顺序。

[官方 Compose 能力采用记录](docs/official-compose.md)说明组件替换范围、保留的业务逻辑与必要系统适配。

## 构建与检查

在仓库根目录运行 `mise install --locked`。Android 需要 SDK 37（含相应的平台扩展版本）和已接受的 SDK 许可，可通过 Android Studio 的 `local.properties` 或 `ANDROID_HOME` 指定 SDK。首次运行 Android 构建时，mise 会下载并缓存锁定版本的 NDK。Windows JNI 需要 Visual Studio C++ Build Tools。

```powershell
mise run android:gradle :apps:android:testDebugUnitTest :apps:android:lintDebug
mise run android:gradle :apps:android:assembleDebug
# 使用正式签名配置时，省略 -PunsignedRelease=true
mise run android:apk -PunsignedRelease=true
mise run windows:gradle :apps:desktop:test
mise run windows:gradle :apps:desktop:run
mise run windows:package
```

Android 构建由 mise 先生成 `build/artifacts/android/jni` 和 `build/artifacts/resources`，Gradle 随后将它们打进 APK。使用 Android Studio 的 Run 或直接调用 Gradle 前，先在根目录运行 `mise run core:android` 和 `mise run resources:pack`。Core 或资源源文件变更后重新运行相应任务。Android Studio 使用与 mise 一致的 Gradle JDK，可用 `mise where java` 查看路径。直接构建桌面 Gradle 任务前，先运行 `mise run core:windows` 和 `mise run resources:pack`。

构建默认使用官方 Google Maven 和 Maven Central。需要为项目依赖指定 Google Maven 镜像时，加 `-PgoogleMavenMirror=https://maven.aliyun.com/repository/google`。

Android 产物位于 `apps/android/build/outputs/apk`，Windows 产物位于 `apps/desktop/build/compose/binaries`。Windows 首次安装包构建会下载 WiX 工具。

在仓库根目录运行以下命令，用 IntelliJ IDEA 格式化 Kotlin 文件。

```powershell
# 将 IDEA_HOME 指向 IntelliJ IDEA 的安装目录
mise run compose:format CHelper-Compose/apps/android/build.gradle.kts
```

## 运行与兼容

Android 应用 ID 仍为 `yancey.chelper`，入口组件、JNI 类名和原数据文件路径保持兼容。根目录的签名配置继续使用，并兼容迁移前的相对密钥路径。共享生产代码位于 commonMain，测试位于 commonTest；公共实现不依赖 JVM API。七项持久化存储由同一个 `ClientDataStores` 创建：设置、复制历史、收藏、本地命令库、命令库编辑草稿、命令输入草稿和 Rawtext。Android 适配器只提供旧目录和应用级实例；Windows 使用同一仓库集合，数据默认位于 `%LOCALAPPDATA%/CHelper`。

旧 Android 和旧桌面数据由共享迁移器读取。解析失败时保留源文件；已有新存储时避免重复导入旧文档。凭据分别使用 Android Keystore 和 Windows DPAPI 保护。网络使用应用级 NetworkServices 实例，认证和业务客户端分离，共享限流与 WAF 状态；JSON、日志、CDP 通信使用 Ktor 官方插件，底层保留两端共用的 OkHttp 引擎。后台同步复用同一领域规则：Android 由 WorkManager 调度，Windows 在应用存活期间观察队列并重试。关闭窗口前等待编辑器、设置和背景写入完成。

`shared/ui` 包含原首页、命令编辑、历史、转换、枚举、Rawtext、设置、账号、个人资料、公有库、私有库、本地库、编辑上传、搜索收藏、消息活动和游龙界面。Android / Windows 均使用共享游龙面板和迷你面板。Android 原生命令编辑器、悬浮窗、输入法与系统权限保留在 Android 宿主；Windows 在相同命令页中使用共享 Compose 编辑器。

验证码使用原 token / 状态轮询协议，Windows 系统浏览器负责渲染。WAF JS 质询由 Android WebView 或 Windows 隔离配置的 Microsoft Edge 执行；Windows 需要已安装 Edge。MSI / EXE 注册 `chelper://commandlab/...` 分享协议，同一数据目录只允许一个写入进程，后续启动将链接转交已有窗口。

测试可通过 `CHELPER_DATA_DIR` 或 `-Dchelper.dataDir=...` 使用隔离目录。JNI 与资源包按内容哈希提取，避免替换仍被进程加载的 DLL。Windows 当前代码页无法表示数据目录路径时，DLL 会提取到默认的 `%LOCALAPPDATA%/CHelper/bundled`，资源包和用户数据仍读写原目录。

## 第三方声明

依赖版本统一在版本目录中维护，内置声明见 `resources/about/open_source_terms.txt`。DPAPI 调用使用 [JNA](https://github.com/java-native-access/jna) 的 Apache-2.0 许可选项；MSVC 运行库遵循微软再分发条款。项目源代码继续遵循 GPL-3.0-or-later。
