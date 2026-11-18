# 平台差异盘点与迁移调研

调研日期：2026-11-12。代码基线：`4fe6b095`。目标平台是 Android 与 Windows **Desktop JVM**，不是 Windows Kotlin/Native。当前使用 Kotlin 2.4.21、Compose Multiplatform 1.12.2，JVM 字节码目标 17；本机打包 JDK 为 25。

本文区分代码盘点、官方资料已声明的能力和项目适配判断。候选库尚未进行集成、包体、真实输入法或真实验证码验证；不把文档中的“支持 KMP”当成已经满足本项目要求。

## 已实施的 Compose 官方能力迁移

返回、预览和链接已采用 Compose 基础 API，命令编辑器及普通界面剪贴板分别使用 BasicTextField 和 LocalClipboard。界面保留自有设计系统，禁止 Material / Material 3 组件和依赖。消息共享调用入口，Android 保留 Toaster，Desktop 使用 Foundation 消息组件；弹窗根据系统悬浮窗约束统一使用宿主内 AppDialog/AppAlertDialog。详见[基础能力与 UI 约束](official-compose.md)。

下文盘点数字及“当前实现”描述保留为 `4fe6b095` 基线。FileKit、KSafe、WebView、Nucleus 等第三方候选仍未集成；这批工作优先采用项目已有的 Compose 官方依赖。

## 盘点结论

目前有 35 个宿主 Kotlin 源文件：Android 24 个、Windows 11 个，合计 5,193 行；另有 UI 模块中的 4 个平台 actual 文件，共 38 行。统计只读取 Git 跟踪的生产源码，包含注释、入口、预览和构建变体，不含测试与 build 输出。这些行数用于定位维护成本，不能等同于可删除代码量。

七项 DataStore、普通界面、ViewModel、命令库协议、文档编解码、命令草稿和领域同步规则已经共享。Ktor 服务、认证/限流/WAF 策略与命令会话均在 commonMain，网络实例由宿主应用容器持有；JNI、Crunch 和 OkHttp 传输适配集中于 platform/jvm，由两端宿主组装。Windows CDP 使用 Ktor WebSockets，Android 浏览器验证使用挂起接口并在请求结束时清理 WebView。共享测试统一在 commonTest，使用 kotlin.test。

进一步收敛的重点是：命令输入控件、文件/图片入口、背景处理、浏览器承载和少量重复的调度/初始化胶水。Android 系统悬浮窗、输入法服务与 Windows 进程/窗口入口仍需宿主参与。

## 逐项清单

下列路径以 `CHelper-Compose` 为根。

| 能力               | 当前自行维护的位置                                                                                                                  | 可迁移方案                                                                        | 判断                                                                          |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| 命令输入           | Android `widget/CommandEditText.kt` 355 行、`ui/completion/AndroidCommandEditor.kt` 108 行；Windows 调用共享 `ComposeCommandEditor` | 两端统一使用已存在的 Compose `BasicTextField` / `TextFieldState`                  | 高收益，先补齐输入与错误展示行为，再移除 Android 原生控件                     |
| 图片选择与上传入口 | `AndroidClientServices` 中的 Activity Result、pending 请求；`DesktopClientPlatform` 中的 Swing 文件选择器                           | FileKit Core + Dialogs + Dialogs Compose                                          | 最值得优先引入的外部库；确认 Android 照片选择器体验与 Windows JVM 发布包可用  |
| 自定义背景         | Android `BackgroundStore.kt` 148 行；Windows `DesktopAppearance.kt` 102 行                                                          | FileKit 图片处理 / Compose 图像转换，现有 Coil 3 用于展示；共享背景仓库与提交协调 | 把选择、编解码和仓库流程收敛；库不能替代“新请求胜出”和原子提交规则            |
| 剪贴板             | Android ClipboardManager 与 AWT Toolkit 分别实现读写；游龙窗口另有注入                                                              | Compose `LocalClipboard`，仅保留必要的纯文本 ClipEntry 转换                       | 可以部分统一，不能声称现有版本零平台转换；不要新用已弃用 ClipboardManager API |
| 打开链接           | Android ACTION_VIEW、Windows Desktop.browse                                                                                         | Compose `LocalUriHandler`                                                         | 可直接统一调用入口，HTTP/HTTPS 白名单及异常反馈留在共享代码                   |
| 消息反馈           | Android Toaster / Toast 与共享 ClientToastOverlay 并存                                                                              | 保留 Android Toaster，Desktop 使用共享 Foundation 消息组件                        | 不使用 Material Snackbar；先核实平台兼容职责再决定依赖替换                    |
| 返回与手势         | `AppBackDispatcher`、Android/JVM BackHandler actual、桌面 Escape 桥接                                                               | 官方 Navigation Event / NavigationBackHandler                                     | 可替换自行维护的回调栈；桌面按键、Activity/浮窗 Owner 仍需接入                |
| 预览               | Android `ImePreviews.kt` 四个函数，共 31 行                                                                                         | commonMain 的统一 Preview 注解                                                    | 这些函数没有系统操作，本可直接共享；现有 Compose 版本已支持                   |
| 登录凭据           | Android Keystore + AES-GCM 104 行；Windows DPAPI 23 行                                                                              | KSafe 等具有 Android 和 JVM 实现的安全存储                                        | 仅列验证候选；软件密钥回退与现有保护语义不同，不宜直接替换                    |
| 验证码与 WAF       | Android WebView / JS Bridge、AndroidWafHelper；Windows 外部浏览器轮询、Edge 进程与 CDP                                              | compose-webview-multiplatform / KCEF；先抽共享验证码状态机                        | 维护收益较大，但引入浏览器运行时和网站兼容成本，需要独立验证                  |
| 同步调度           | `LibrarySyncWorker/Scheduler` 与 `DesktopLibrarySync`                                                                               | 先共享 LocalLibrarySyncStore 和调度策略；Backgrounder 可作为统一调度候选          | WorkManager 的持久工作与 JVM 进程内协程保证不同，不能仅靠相同 API 等同        |
| 游龙与命令浮窗     | Android WindowManager / EasyWindow / ComposeLifecycleOwner；Windows Compose Window                                                  | 共享会话、关闭、最小化与尺寸状态；保留薄窗口宿主                                  | 现有 UI 已共享。Activity 内 Dialog/Popup 不能替代 Android 系统级悬浮窗        |
| 单实例与分享链接   | `DesktopInstance.kt` 151 行，锁文件、认证 IPC、参数转发；Android singleTask/onNewIntent                                             | Nucleus core-runtime 的单实例/深链接能力                                          | 值得做 Windows 模块级验证；必须保持按数据目录隔离、URI 转发和安全边界         |
| 应用信息与启动     | DesktopContainer 和 Gradle 中多处重复版本；两端重复认证/网络初始化                                                                  | BuildKonfig + 单一版本来源；共享启动装配函数                                      | 可减少人工同步。不必为两个入口单独引入完整 DI 框架                            |
| 日志               | ClientLog、Android Log、ClientDiagnostics、MonitorUtil                                                                              | Kermit + 按需的 UMeng LogWriter                                                   | 一般日志可以统一；Android 厂商 SDK、同意隐私政策后的初始化仍属于宿主          |
| 安装包与协议注册   | Windows 自定义 WiX 模板生成、jpackage 调用                                                                                          | Nucleus 打包插件；Conveyor 是另一分发方案                                         | 与运行时单实例分开评估。当前打包已有验证，不将整套分发工具替换列为第一批      |
| JNI 加载与资源提取 | Android 包内 .so；Windows DLL/运行库提取                                                                                            | 保留现有共享 JNI；文件工具可复用 Okio / FileKit                                   | 不为统一加载入口改成两套 FFI；不同架构的二进制和系统加载入口仍然必要          |
| Android 系统能力   | IME Service、前台服务、悬浮窗授权、系统栏、监控 SDK                                                                                 | 继续依赖 AndroidX / 现有权限与窗口库                                              | 没有 Windows 对等能力，不应为了形式一致增加空壳实现                           |

## 优先迁移的方案

### FileKit：统一文件与图片入口

[FileKit 官方资料](https://filekit.mintlify.app/introduction)明确覆盖 Android 与 Desktop JVM Windows，提供平台文件、选择器和 Compose 集成；[图片工具](https://filekit.mintlify.app/core/image-and-video-utils)提供缩放、压缩及图像转换。项目可用它替代重复的照片/文件选择和图片处理适配。其[源码许可证为 MIT](https://github.com/vinceglb/FileKit/blob/main/LICENSE)。

范围建议是头像和背景两个入口，不替换现有 DataStore。统一得到所选文件的字节、名称和 MIME，再由共享上传/背景仓库使用。Android 的照片选择行为与通用文件选择行为应分别核实，不能因函数同名就认定与现有 Photo Picker 体验相同。

背景处理仍由项目维护一份共享协调器：限制尺寸/大小，串行提交，拒绝过时解码结果，并在退出前等待保存。FileKit 负责底层文件与图像操作；已有的请求版本测试继续保留。

保留已有目录：Android 的 `files/datastore`、`dataDir/theme/background.png`，Windows 的 `%LOCALAPPDATA%/CHelper` 和 `background.image`。继续支持 `CHELPER_DATA_DIR` / `chelper.dataDir`，不因改用默认 appId/目录 API 而让旧数据看起来消失。

重要限制：FileKit 的[分享文件 API](https://filekit.mintlify.app/dialogs/share-file)明确仅用于 Android/iOS，不能据此承诺 Windows 系统分享，也不能把 `shareFile` 当成通用文本分享。Windows 当前“分享即复制”仍是功能差异，需要单独定义产品行为。

FileKit 的[发布记录](https://github.com/vinceglb/FileKit/releases)包含针对 Compose 1.12 / Skiko 编码签名和桌面发布压缩的修复。正式接入前锁定含相关修复的已发布版本，并验证项目的 jlink / MSI / EXE；本次不根据 main 文档猜测版本号或直接使用动态版本。

### Compose 官方能力：减少重复适配

命令编辑器已有共享实现。官方[状态式文本输入 API](https://developer.android.com/develop/ui/compose/text/user-input)提供 TextFieldState、输入/输出转换等能力，适合继续完善现有控件，而不是再引入完整 IDE 或富文本编辑框架。

这不是简单换掉 AndroidView：原生控件会把零长度错误区间扩展到邻近字符，绘制跨行错误下划线，并处理横向/折行模式。当前共享实现跳过 `start == end` 的错误区间，其他展示和输入行为也需要对齐。迁移门槛包括：中文 IME 组合、候选词提交、选区与撤销、补全后光标、错误点击定位、零长度与跨行错误、超长命令、两种显示模式，以及 IME/浮窗中的焦点恢复。通过后删除 Android 的两份编辑实现，并移除 `ClientPlatform.CommandEditor` 分发。

[Clipboard 官方接口](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/Clipboard.kt)已经统一异步传输，但[ClipEntry 仍是平台协议](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/ClipboardManager.kt)。本次还用 javap 检查了本地 Compose 1.12.2 JAR；不能假设存在未核实的 common 纯文本构造/读取 API。可以共享调用方和完成/失败反馈，文本转换保留最小桥接，避免复制成功提示早于异步写入完成。

URL 打开可复用[官方 UriHandler](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/UriHandler.kt)。返回事件和预览则可采用官方[Navigation Event 与统一 Preview 方案](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html)。采用 Navigation Event 不要求顺带重写全部路由为 Navigation 3，现有路由兼容标识应继续保留。

### 应用信息与日志

[BuildKonfig](https://github.com/yshrsmz/BuildKonfig)提供 KMP 配置代码生成。将版本名、版本码放到一个 Gradle 来源，再生成运行时元数据，可消除 Android Gradle、Windows 打包参数和 DesktopContainer 的重复常量。DEBUG、Android buildType 和平台权限仍由宿主提供，生成文件不放密钥或登录凭据。

[Kermit 官方文档](https://kermit.touchlab.co/docs/)提供共享 Logger 与可组合 LogWriter，适合替代一般日志的多套调用。UMeng 集成只保留必要 writer/初始化桥，并继续受隐私政策同意状态控制。现有 Android WAF 有输出 Cookie 的语句，统一日志时应删除或脱敏，而不是原样转发给新日志后端。

## 需要验证后再决定的方案

### 安全存储：KSafe 不是直接等价替换

[KSafe](https://github.com/ioannisa/KSafe)的官方资料覆盖 Android Keystore 与 JVM/Windows DPAPI，源码采用 [Apache-2.0](https://github.com/ioannisa/KSafe/blob/main/LICENSE)。但是其[JVM 密钥保护文档](https://github.com/ioannisa/KSafe/blob/main/docs/JVM_PROTECTION.md)描述了在原生库无法链接、模块不完整等场景中的软件密钥回退；部分 OS 密钥库故障则走 fail-closed。不能笼统概括成“总会安全失败”或“总会回退”。

项目目前 Android 使用 Keystore AES-GCM，Windows 使用当前用户 DPAPI。要迁移，需要验证候选版本是否能拒绝软件保护等级、发布包包含必要模块，并在旧存储成功读取、新存储写入与读回成功后才清理旧文件。不能只配置一个名为 Strict 的策略就假定已禁用所有回退路径。

若无法证明这些条件，继续保留现有共 127 行适配，比维护安全库分支或弱化已有凭据保护更合适。这里的结论是“具备候选资格，暂不直接替换”，不是宣称 KSafe 已验证可用。

### WebView：统一浏览器的收益与代价

[compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)提供共享 WebView、状态和 JS/原生交互能力，Android 使用系统 WebView，Desktop 使用 KCEF；其[Desktop 配置](https://github.com/KevinnZou/compose-webview-multiplatform/blob/main/README.desktop.md)要求处理初始化、运行时、JVM 参数与发布配置。

可先把获取验证码 token、超时、状态轮询和结果处理提取成共享状态机，保留薄浏览器承载。若进一步引入统一 WebView，再验证实际站点的验证码和 WAF、Cookie 来源/路径/有效期、浏览器与 HTTP 请求的 User-Agent 一致性、取消与窗口销毁、离线启动及发布包大小。

它不会自动实现项目的 WAF 协议，也不能在未测量前承诺包体变化。当前 Temurin/jlink 分发也不能直接等同于官方文档中已经附带 JCEF 的 JBR。需要选择随包携带浏览器还是首次初始化下载，并核查对应第三方声明。

### 单实例、深链接与打包：Nucleus

[Nucleus 官方仓库](https://github.com/NucleusFramework/Nucleus)列出可独立使用的 runtime 模块，其中 core-runtime 包含平台信息、单实例和深链接，打包插件覆盖原生安装器与协议关联。

优先研究独立 core-runtime 能否替代 DesktopInstance 的锁与参数转发，不把换窗口后端、主题或 GraalVM 作为前置。必须证明：同一数据目录互斥，不同数据目录可独立运行，后续启动的 URI 能送达已有窗口并唤起窗口，异常退出能恢复，IPC 不接受未授权输入。

该项目演进较快，官方 main 文档与 [3.0 草稿 PR](https://github.com/NucleusFramework/Nucleus/pull/776)存在阶段差异。需要按正式发布标签核对 Maven 坐标、API 和 JVM 兼容性，不能把网站或草稿能力当成已发布版本保证。Nucleus 处理的是桌面基础设施，不会统一 Android 系统悬浮窗。

打包还可调研 [Conveyor 的 OS 协议集成](https://conveyor.hydraulic.dev/latest/configs/os-integration/)。它能减少自行维护的分发配置，但不能代替运行中的进程互斥与消息转发。保留当前已验证的 jpackage/WiX 作为基线，分别比较工具许可、CI、离线安装、包体和旧安装升级。

### 后台任务：不把相同函数当成相同保证

[Backgrounder](https://github.com/happycodelucky/backgrounder-kmp)声明 Android 使用 WorkManager，JVM 21+ 使用进程内协程，后者不会在进程退出后继续运行。这与本项目现有调度差异相近，可以列入候选，但仍需验证 JVM 运行时/目标字节码组合、重试、账户切换、去重及旧任务迁移。

更直接的第一步是共享两端重复的 LocalLibrarySyncStore 适配和任务业务策略，继续由 WorkManager / 协程负责触发。领域处理器已经共享，不值得仅为 API 外形一致而重写这部分。

## 不推荐为了“多平台”而替换的内容

- 普通设置、历史、收藏等继续使用已共享的 DataStore，不迁到另一套键值库。
- 网络协议与命令会话已迁入 commonMain；Ktor 引擎和 JNI 适配集中于 platform/jvm。替换平台适配时仍须验证缓存、Brotli、认证、WAF 与引擎生命周期行为。
- 系统悬浮窗、前台服务、Android IME、系统栏和 Activity/Window 入口保留最小系统适配；共享状态、内容和业务流程。
- 不用移动端权限库承诺支持 Windows，也不用 Activity 内的 Popup 冒充跨应用系统悬浮窗。
- PlatformTools 的[Clipboard Manager 模块](https://github.com/kdroidFilter/Platform-Tools#-clipboard-manager-module)主要是 JVM Windows/macOS/Linux 的剪贴板监控，不是已核实的 Android＋Windows 通用文本读写替代。
- 4 个 actual 文件仅 38 行；滚动效果可接受平台默认值时再统一，若需保留 Android 专属颜色参数，少量 actual 比引入新框架更简单。

## 迁移顺序与验收

1. **基础收敛**：统一版本来源、共享纯预览和 URL 入口、统一可见 UI 的消息 Host；提取共同启动/同步适配。不新增 Windows 专用功能界面。
2. **文件与图片**：用 FileKit 做头像/背景的小范围集成验证；验证取消、权限、MIME/文件名、EXIF、透明 PNG、尺寸和内存限制、快速连续替换、进程退出保存、旧目录恢复、Windows 发布包。
3. **编辑器**：完善共享 BasicTextField，完成 Android/Windows 的输入、选区、撤销、高亮与错误展示回归后，删除 Android 原生编辑控件。这个阶段对减少后续双端行为修复的价值最高。
4. **框架事件与日志**：迁移 Navigation Event、共享生命周期内的输入/消息入口，按需要引入 Kermit；仍保留必要的 OS 事件桥。
5. **高成本候选**：分别验证 Nucleus、统一 WebView、安全存储与后台调度。每个候选明确锁定版本、能删除的自维护职责及不能承担的职责，再决定接入。

验收关注的是“同一行为以后是否只需改一处”，而不是把差异藏到一个巨大的 PlatformServices 中。新依赖必须确实覆盖本项目的两个目标，保持原 UI 和已有数据/路由/签名/安装升级关系，并通过 Android 与 Windows 实际发布产物的验证。
