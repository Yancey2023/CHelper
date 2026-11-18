CHelper 是一个我的世界命令助手，包含了：

- CHelper-Core - 命令的语法分析、补全提示、语法高亮等核心 IDE 功能，使用 c++
- CHelper-Compose - 接入 CHelper-Core 的 Kotlin / Compose Multiplatform 应用，支持 Android 和 Windows；apps/android、apps/desktop 负责平台入口和依赖组装；shared/domain 定义业务模型、仓库接口和同步规则，shared/data 实现共享存储、网络和文档编解码，platform/jvm 保存两端复用的 JNI、Crunch 与 OkHttp 引擎适配，shared/ui 提供复用的功能界面、ViewModel、设计组件和导航；build-logic 维护统一构建约定，resources 保存两端共用资产。模块依赖和包归属由 scripts/check_compose_architecture.py 检查，结构说明见 CHelper-Compose/docs/architecture.md。
- CHelper-Web - 接入了 CHelper-Core 的网页应用，同时包含了更多实用功能，使用 pnpm + vite + vue + typescript
- CHelper-Doc - 用于给大众阅读的项目文档，使用 vitepress
- CHelper-Resource - 用于 CHelper-Core 读取的资源包，包含了一些 json 文件
- scripts - 一些 python 脚本

你必须：

1. 使用 gradle 和 pnpm 等包管理器要提权运行，避免遇到权限问题
2. 完成改动后，使用 fmt 等相关工具进行代码格式化
3. 理清楚项目层级，注重项目的可维护性
4. 共享生产代码使用 commonMain，测试使用 commonTest 与 kotlin.test，禁止重新引入自定义 JVM 共享源码目录或在公共源码中引用 Java、JUnit、Retrofit、OkHttp、JNI 类型；真实平台互操作放在 apps 或 platform/jvm，通过领域接口注入。
5. 网络服务、账号会话和浏览器验证状态由应用容器持有并注入，不重新引入可变全局 ServiceManager、LoginUtil、GuestAuthUtil 或 WafHelper。优先使用 Ktor 官方序列化、日志和 WebSocket 插件；认证来源隔离、限流、WAF 单次重放和宿主资源清理由项目维护，保留 HTTP/2、Brotli 与有界磁盘缓存。
6. Kotlin 源码和 Gradle Kotlin DSL 必须通过 import 引入类型、注解、顶层函数和属性，禁止在声明、类型签名或表达式中直接写完整包名。名称冲突使用 `import ... as ...` 别名，不用全限定名绕过；同样适用于测试和文件级注解。package/import 声明以及确实需要类名的字符串（如 JNI、反射、主类配置）保留原值，不把变量成员访问或嵌套类型访问当成包名清理。
7. UI 的远端业务通过 domain 定义的职责明确的 Repository 接口访问，不引用 data.remote / data.auth 中的网络图、会话、服务或 DTO。Repository 负责协议映射、认证归属、缓存及失效、访客与旧收藏迁移；返回不可变业务快照。ViewModel 在主线程更新状态，传播 CancellationException，刷新与切换目标时取消过时请求，分页成功后才推进页码，写操作防止重复提交。

CHelper-Compose 的 UI 约束（用户明确要求）：

- 不使用 Material 或 Material 3，包括 `androidx.compose.material.*`、`androidx.compose.material3.*`、对应的主题、控件、图标与 Gradle 依赖。不得把现有自定义控件换成 Material，也不要通过别名或包装间接引入。桌面 compose.desktop.currentOs 聚合依赖默认带入的 Material 也须显式排除，并检查解析依赖和分发包。
- Compose 官方能力优先指 Runtime、UI、Foundation、Animation、Navigation 等基础能力；保留项目自己的设计系统、视觉风格和共享控件，Android 与 Windows 复用同一套 UI。
- 命令编辑器的 InputTransformation / OutputTransformation 必须保持实例稳定；动态高亮、错误样式和主题通过 rememberUpdatedState 等快照状态读取更新，不能按文本或 token 重建输出转换器，否则 BasicTextField 会重建内部文本状态并重启 IME 会话。验证时检查输入会话连续性及样式更新，不能只检查焦点还在。
- 页面切换动画集中在共享 `ClientNavHost`，普通返回与预测性返回使用同一反向路径、时长和缓动。Navigation 2.10 的 `predictivePopEnterTransition` / `predictivePopExitTransition` 必须显式配置，不能只配置普通 pop 动画；手势进度、取消恢复和提交由官方 NavHost 处理，避免手动重复 pop。
- shared/ui 的 Android 目标必须启用 `androidResources.enable = true`，由 Compose 插件打包共享资源；Android 原生 `res` 目录映射不能替代 Compose assets。发布前用 `scripts/check_android_resources.py` 检查 APK 中的实际资源内容。
- 弹窗使用共享 `AppDialog` / `AppDialogHost` 在当前 Compose 宿主内绘制。系统悬浮窗不能依赖 Activity Dialog 的窗口条件；不使用创建额外系统窗口的 Dialog、AlertDialog 或 BasicAlertDialog，也不通过 windowType 适配来替代自定义弹窗。保留遮罩拦截、嵌套返回、禁止关闭和焦点隔离、恢复能力。
- 保留 Android 的 Toaster，在 Application 启动时初始化，并通过共享 `ClientMessages` 入口提供反馈。它承担不依赖 Compose Host 的消息呈现及 Android Toast 兼容处理；不要擅自用 Snackbar、自制界面消息或系统 Toast 替换，也不要只因“官方能力优先”或“跨平台”而删除。Windows 使用共享的非 Material 消息组件。
- 移除或替换已有库前，先查明调用场景、线程与生命周期要求、平台兼容职责，并验证替代行为；不要仅凭编译通过认定替换等价。相关 UI 约束由 `scripts/check_compose_architecture.py` 检查，变更时同步维护说明。
