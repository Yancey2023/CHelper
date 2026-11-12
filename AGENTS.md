CHelper 是一个我的世界命令助手，包含了：
- CHelper-Core - 命令的语法分析、补全提示、语法高亮等核心 IDE 功能，使用 c++
- CHelper-Compose - 接入 CHelper-Core 的 Kotlin / Compose Multiplatform 应用，支持 Android 和 Windows；apps/android、apps/desktop 负责平台入口和依赖组装；shared/domain 定义业务模型、仓库接口和同步规则，shared/data 实现共享存储、网络和文档编解码，shared/native 实现 JNI 绑定和命令引擎，shared/ui 提供复用的功能界面、ViewModel、设计组件和导航；build-logic 维护统一构建约定，resources 保存两端共用资产。模块依赖和包归属由 scripts/check_compose_architecture.py 检查，结构说明见 CHelper-Compose/docs/architecture.md。
- CHelper-Web - 接入了 CHelper-Core 的网页应用，同时包含了更多实用功能，使用 pnpm + vite + vue + typescript
- CHelper-Doc - 用于给大众阅读的项目文档，使用 vitepress
- CHelper-Resource - 用于 CHelper-Core 读取的资源包，包含了一些 json 文件
- scripts - 一些 python 脚本

你必须：
1. 使用 gradle 和 pnpm 等包管理器要提权运行，避免遇到权限问题
2. 完成改动后，使用 fmt 等相关工具进行代码格式化
3. 理清楚项目层级，注重项目的可维护性
