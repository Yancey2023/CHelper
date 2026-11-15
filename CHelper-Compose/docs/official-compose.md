# Compose 基础能力与 UI 约束

用户明确要求：不使用 Material 或 Material 3。Compose 官方能力优先适用于 Runtime、UI、Foundation、Animation、Navigation 等基础 API；项目自己的视觉设计和共享控件继续保留。约束以仓库根目录 [AGENTS.md](../../AGENTS.md) 为准。

## 当前实现

| 能力                | 实现                                                   | 维护边界                                                                                                     |
| ------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| 主题与基础控件      | CHelperTheme、BasicText、Image、Canvas、布局与选择语义 | 自有按钮、开关、单选框、复选框、分隔线、表面和图标；无 Material 主题或组件依赖                               |
| 普通表单            | BasicTextField / TextFieldState                        | 共用输入装饰器、占位文字、前后图标槽位与品牌样式                                                             |
| 密码输入            | Foundation BasicSecureTextField                        | 复制、剪切保护和密码输入语义交给 Foundation，不手工改写密码或模拟掩码                                        |
| 命令编辑            | BasicTextField / 输入输出转换                          | 两端共用；项目维护命令单行存储、高亮及 UTF-16 错误区间规则                                                   |
| 弹窗与菜单          | AppDialog / AppDialogHost / AppAlertDialog             | 当前 Compose 宿主内绘制；标准和复杂弹窗共用，不创建系统 Dialog 窗口                                          |
| Android 消息        | Toaster 15.2                                           | Application 初始化，通过 ClientMessages 共享入口调用，不依赖 Compose Host                                    |
| Desktop 消息        | Foundation 共享消息组件                                | STARTED 时收集瞬时事件，新消息替换旧消息；取消收集即清理显示；支持关闭、live region 和系统建议的可访问性超时 |
| 返回                | NavigationBackHandler / NavigationEventDispatcher      | 项目只维护业务守卫；标题栏使用 DirectNavigationEventInput，Escape 使用窗口默认分发                           |
| 剪贴板              | LocalClipboard / ClipEntry                             | 官方挂起读写，保留 Android/JVM 纯文本载荷转换；成功提示等待写入完成                                          |
| 链接                | LocalUriHandler                                        | 共享 HTTP/HTTPS 限制与打开失败反馈                                                                           |
| 滚动与拖动          | Foundation 默认滚动、列表与手势                        | 保留领域分页、重排和拖动规则                                                                                 |
| Activity 内容与预览 | activity-compose.setContent / commonMain Preview       | 13 个纯 Content 预览不依赖真实仓库或业务 ViewModel                                                           |

Android 与 Windows 共用界面、控件和 ViewModel，不为 Windows 设计另一套 UI。Material / Material 3 的主题、控件、图标和 Gradle 依赖均不使用。桌面 compose.desktop.currentOs 聚合依赖默认会带入旧版 Material，apps/desktop 在所有配置中显式排除 Material / Material 3 分组，覆盖运行和测试依赖；同时检查解析依赖和最终分发包。

Android 的 debug ui-tooling 也默认带入 Material 3。已核对缓存中的 1.12.1 工件：Material 引用位于参数化 PreviewActivity 的工具控件，ComposeViewAdapter 不引用 Material；对该调试依赖显式排除 Material / Material 3。项目现有 13 个预览都是无参 Content，继续保留。参数化预览的设备运行器不属于已验证能力，后续引入 PreviewParameter 时须重新评估，不能重新带入 Material；IDE/实机预览仍需在对应环境验证。

## 自定义弹窗保留依据

系统悬浮窗不能依赖普通 Activity Dialog 的窗口条件。弹窗通过 AppDialogHost 绘制在现有宿主内，不使用系统 Dialog、AlertDialog 或 BasicAlertDialog，也不通过 windowType 适配绕过此约束。AppAlertDialog 是项目自己的内容槽位组合。

从小尺寸嵌套布局请求的弹窗仍覆盖整个宿主，保留调用点的 CompositionLocal 和内容状态。顶层弹窗负责遮罩拦截、嵌套返回、禁止关闭属性及输入焦点隔离，关闭后恢复原页面焦点。Android 普通页面、系统浮窗与 Desktop 复用相同实现。

## Toaster 保留依据

Git 历史中，Toaster 随 `5a0f6d43` 的 Android 模块引入；未找到最初选库原因的独立设计记录，不能把推断当成原作者意图。已有使用事实包括 Application 初始化、底部 20 dp 偏移，以及复制、表单、验证码、网络回调和背景加载失败反馈；项目还包含脱离 Activity 的系统浮窗。

[维护者说明](https://github.com/getActivity/Toaster)列出了任意线程调用、连续提示替换、Android 7.1 崩溃处理、通知关闭和 Android 11 前后台差异适配。平台限制仍适用，保留库不代表承诺后台提示总能显示。无重放 SharedFlow 在没有订阅者时会丢消息，因此依赖可见组合树的消息组件不能据此认定与 Toaster 等价。

Android 在 Application 启动时安装平台呈现器。共享功能仅调用 ClientMessages；同一消息不会同时送给平台呈现器和 Desktop 消息 Host。未经明确要求不移除 Toaster，不擅自替换为 Snackbar、自制界面消息或系统 Toast。替换任何现有库前，先核实调用场景、线程、生命周期和兼容职责并验证行为。

## 保留的系统适配

系统分享、照片/文件选择、WebView、跨应用系统悬浮窗、Android IME Service、后台调度、安全密钥库和 JNI 引擎继续由宿主适配。独立窗口模型在组合树创建前注入的剪贴板回调与分享回退保留宿主 API；普通界面使用 LocalClipboard。DataStore 使用共享 OkioStorage；网络协议、认证和 WAF 策略使用 common Ktor，保留宿主传输缓存、Brotli、浏览器验证与凭据保护职责。

## 验证与防回归

- `scripts/check_compose_architecture.py` 检查 Material 依赖/引用、系统 Dialog 使用和 Android Toaster 依赖，以及原有模块归属和依赖方向。
- 真实 ComposeWindow 的 Escape 在编辑框聚焦前后和弹层打开时按正确顺序处理，弹层不创建额外子窗口。
- 自定义弹窗覆盖单一根、CompositionLocal/状态保留、点击不穿透、禁止关闭、嵌套返回、焦点隔离与恢复。
- 共享控件覆盖禁用按钮、开关/复选框语义、安全密码输入；消息路由覆盖无 Host、工作线程、重复消息和不重复分发。
- 两端共享测试、Android lint/Debug/未签名 Release 与 Windows 分发构建。Android 仪器测试在没有设备时只编译，真实 IME、系统浮窗和预测返回仍需实机验证。

## 官方依据

- [Foundation API](https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary)
- [状态式文本输入](https://developer.android.com/develop/ui/compose/text/user-input)
- [统一 Preview 与 Navigation Event](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html)
- [Compose Clipboard 源码](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/Clipboard.kt)
