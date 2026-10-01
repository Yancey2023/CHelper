# 共享合成内核缓存（KernelCache）

> **状态**：方案定稿，代码完成（步骤 1 KernelCache、步骤 2 MCD 高亮、步骤 3 CompletionViewModel 均已切换并随 App 部署；步骤 4 Android 冒烟 + 内存观察**真机回归通过**——本文档于 2026 会话中定稿）
> 关联：[composer](./composer.md)（合成器/ComposeResult）、[segment-loader](./segment-loader.md)（启用段）、[clients-and-roadmap](./clients-and-roadmap.md)（安卓接入）。

## 1. 目标与非目标

**目标**：全应用只保留一份"按启用段合成的 `CHelperCore`"，同段跨页面复用（命令补全 / 库 MCD 高亮 / rawtext 编辑器值候选）；切段自动重建、旧段按策略驱逐；删除 `MCDHighlightCoreCache` 私有缓存与 `CompletionViewModel` 自建内核的重复合成。

**非目标**（本次不做）：多段同时活跃（远期 multi-segment）；合成结果的预编译缓存（P3）；Web/Qt 侧。

## 2. 现状与问题

| | CompletionViewModel | MCDHighlightCoreCache |
| --- | --- | --- |
| 持有 | `core: CHelperCore?`（ViewModel 私有） | 单例单内核（按 segment） |
| 合成时机 | 进命令页 / 切段（异步 IO + 代际防竞态） | 首次高亮、段变化（后台批内同步） |
| 释放 | `onCleared` / 切段时 `close()` | 换段时 `close()` |

问题：命令补全与库页高亮**各持一份同段内核**（双份 CPack 内存；切段时合成两次）；同段反复进出页面每次都重新合成（rawtext 编辑器接入后同样会再添一份）。

## 3. 设计

新增 `core/KernelCache.kt`（应用级单例，与 `MainPackProvider` 并列），按段缓存 + 引用计数 + 最近保留：

- **key** = `segment + '#' + extensionFingerprint`（P0 拓展包恒为空；未来启停拓展包 → key 变化 → 自然重建）；
- **租约 API**：`acquire(context, segment, fingerprint)` 返回内核（可能触发合成，**必须在后台线程调用**）；`release(...)` 配对归还；release 后调用方不得再触碰该内核；
- **驱逐**：无租约（refCount==0）且不在最近保留表（容量 2，LRU）的 key → `close()` 移除；
- 命中缓存 = 零成本复用，高频进出命令页不再重复合成。

```kotlin
object KernelCache {
    private class Entry(val core: CHelperCore, var refCount: Int)

    private val entries = LinkedHashMap<String, Entry>(4, 0.75f, true) // accessOrder
    private val maxCachedKeys = 2   // 无租约时最多保留的内核数（当前段 + 上一个段）

    @Synchronized fun acquire(context: Context, segment: String, extensionFingerprint: String = ""): CHelperCore?
    @Synchronized fun release(segment: String, extensionFingerprint: String = "")
    @Synchronized fun reset()   // MainPackProvider.reset() 联动
}
```

驱逐只发生在 release 内且 refCount==0，因此不存在"正在 createContext 却关内核"的窗口。合成沿用 `CHelperCore.compose`（内部全局锁保证 compose 串行）。

## 4. 使用方改造

### 4.1 MCD 高亮（MCDRenderer，步骤 2 ✓ 已完成）

删除 `MCDHighlightCoreCache` 对象；`applyMcdHighlightSync / applyMcdHighlightItemsAsync` 每批 acquire/release：

```kotlin
withContext(Dispatchers.Default) {
    val segment = cpackBranch.replace('-', '/')
    val core = KernelCache.acquire(context, segment) ?: return@withContext 0
    try {
        ... core.createContext(cmd).use { it.syntaxToken } ...   // 不再需要私有缓存锁
    } finally {
        KernelCache.release(segment)
    }
}
```

### 4.2 命令补全（CompletionViewModel，步骤 3 ✓ 已完成）

保留现有"异步 IO + 代际防竞态"骨架，把 `MainPackProvider.get + CHelperCore.compose` 换成 `KernelCache.acquire`；成功后先 close 旧 context、release 旧段租约，再挂新内核；`onCleared`/空分支只 release 不再直接 close。

### 4.3 rawtext 编辑器（✓ 已落地）

值候选补全（hasitem item/location、type/family/m、翻译键）经 `ui/rawtext/RawtextCompletionKernel`（按启用段从 KernelCache 租借，生命周期随 RawtextViewModel）走 FragmentContext，与命令补全同段复用内核（见 rawtext-convergence.md §7）。native `FragmentContext` 持有 `shared_ptr<const CPack>`，不依赖 CHelperCore 存活；片段打开后即可 release 租约。

## 5. 内存与时序

同一时刻最多 2 份 CPack；同段跨页只 1 份（现状恒为 2 份）。切段 A→B→A 时 A 仍在保留表 → 命中缓存零合成；切第三个段时才驱逐最久未用的旧内核。

## 6. 并发不变式

1. compose 串行：`CHelperCore.compose` 全局锁（已有）；
2. close 只发生在无租约时（驱逐路径在 release 内）；
3. release 后调用方不得再使用内核（API 契约）；
4. 缓存表 `@Synchronized`；跨线程共用内核只走只读 `createContext`（CommandContext 构造只读共享 CPack，桌面并发用例已覆盖同模式）。

## 7. 验证

1. 回归：命令补全（提示/高亮/错误）与库页高亮，在 release/experiment、beta/vanilla 下与改动前一致；
2. 复用：先进命令页再开库详情页 → 库页首批高亮无合成延迟（命中缓存）；
3. 切段：快速连续切换 5 次不崩溃、close/release 日志配对无泄漏；
4. 驱逐：切 3 个不同段后旧内核 close，常驻 ≤ 2；
5. 桌面 gtest 不动（纯 Kotlin 层改动），Android 冒烟覆盖 1-4。

## 8. 实施步骤与回退

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 新增 `core/KernelCache.kt` | ✅ 已实现（真机回归通过） |
| 2 | MCDRenderer 改租约，删 `MCDHighlightCoreCache` | ✅ 已实现（真机回归通过） |
| 3 | CompletionViewModel 改租约（保留代际/异步结构） | ✅ 已实现（真机回归通过） |
| 4 | Android 冒烟 + 内存观察 | ✅ 真机回归通过 |

回退：步骤 2/3 各自独立可还原；缓存容量上限可调回 1（最保守：只留当前段）。

### 步骤 3 补充说明（CompletionViewModel）

- `refreshCHelperCore` 改为：同段已就绪 → 只同步一次 UI 状态；异段 → 后台 `KernelCache.acquire`（串行 executor + 代际号），成功后在主线程归还旧段租约、挂新段内核；
- `core` 字段不再被 ViewModel `close()`（内核归属 KernelCache，页面只租借）；`onCleared` / 空段仅归还租约；
- 取消安全：协程取消路径按 `acquired` 标记归还本次租约（`release` 幂等），无泄漏；
- 删除 ViewModel 内 `MainPackProvider.get + CHelperCore.compose` 直接调用（唯一入口收敛到 KernelCache）。
