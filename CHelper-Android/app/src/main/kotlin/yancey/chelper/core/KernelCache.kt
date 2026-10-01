/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.core

import android.content.Context
import android.util.Log

/**
 * 共享合成内核缓存：全应用按"启用段 + 已启用拓展包配置"只保留一份合成的 [CHelperCore]，
 * 命令补全 / 库 MCD 高亮 / rawtext 编辑器值候选共用同段内核，避免重复合成与重复驻留。
 *
 * 启用拓展包通过 [ExtensionPackBridge] 注入（应用层实现）：每次合成前读取
 * （指纹, 文件集）；指纹变化 → 缓存 key 变化 → 自动按新配置合成，旧配置内核按 LRU 驱逐。
 *
 * 生命周期语义（租约）：
 * - [acquire] 返回内核租约；可能触发合成（耗时），**必须在后台线程调用**；
 * - [release] 配对归还；归还后调用方不得再调用该内核的任何方法（含 createContext）；
 * - 驱逐（close）只发生在 release 且引用计数归零后，因此不存在"正在 createContext 却关内核"的窗口；
 * - 合成串行由 [CHelperCore.compose] 内部全局锁保证。
 *
 * 缓存策略：按 key（segment + 拓展包指纹）缓存；无租约时最多保留最近使用的
 * [maxCachedKeys] 个内核（当前段 + 上一个段，来回切版本可命中缓存零合成）。
 */

/** 内核合成时的启用拓展包源（应用层注册）。@return (指纹, 启用包文件集，顺序 = 合成顺序) */
fun interface ExtensionPackSource {
    fun load(context: Context): Pair<String, List<Map<String, ByteArray>>>
}

/** [ExtensionPackSource] 注册点：未注册 = 无启用拓展包（兼容测试/旧流程） */
object ExtensionPackBridge {
    @Volatile
    var source: ExtensionPackSource? = null
}

object KernelCache {

    private class Entry(val core: CHelperCore, var refCount: Int)

    /** accessOrder=true：迭代顺序 = 最近使用优先 */
    private val entries = LinkedHashMap<String, Entry>(4, 0.75f, true)

    /** 最近一次合成失败的原始原因（acquire 返回 null 时供 UI 展示定位；成功合成后清空） */
    @Volatile
    private var lastComposeFailure: String? = null

    /** 最近一次合成失败的原始原因（null = 无失败或已成功） */
    fun lastComposeFailure(): String? = lastComposeFailure

    /** 无租约时最多保留的内核数 */
    private const val maxCachedKeys = 2

    private fun key(segment: String, extensionFingerprint: String): String =
        "$segment#$extensionFingerprint"

    /** 读取当前启用拓展包配置（指纹与文件集，由 [ExtensionPackBridge] 提供；无注册返回空配置） */
    private fun currentExtensionConfig(context: Context): Pair<String, List<Map<String, ByteArray>>> =
        ExtensionPackBridge.source?.load(context) ?: ("" to emptyList())

    /**
     * 取内核租约。
     *
     * @param segment 主包启用段，如 "beta/vanilla"（空段返回 null）
     * @return 内核；主包缺失 / 合成失败返回 null（调用方自行降级提示）
     */
    @Synchronized
    fun acquire(context: Context, segment: String): CHelperCore? {
        if (segment.isEmpty()) {
            return null
        }
        val (fingerprint, packs) = currentExtensionConfig(context)
        rememberFingerprint(fingerprint)
        val k = key(segment, fingerprint)
        val cached = entries[k]
        if (cached != null) {
            cached.refCount++
            entries[k] = cached // 刷新 LRU（accessOrder 下 put 已存在 key 会移到末尾）
            return cached.core
        }
        val mainPack = MainPackProvider.get(context) ?: return null
        val core = try {
            CHelperCore.compose(mainPack, arrayOf(segment), packs)
        } catch (throwable: Throwable) {
            lastComposeFailure = throwable.message
            Log.w("KernelCache", "fail to compose core for segment $segment", throwable)
            null
        } ?: return null
        lastComposeFailure = null
        entries[k] = Entry(core, 1)
        return core
    }

    /** 当前启用拓展包指纹（供调用方判断"包配置是否变化"，不触发合成；读操作廉价） */
    fun currentFingerprint(context: Context): String {
        val fingerprint = currentExtensionConfig(context).first
        rememberFingerprint(fingerprint)
        return fingerprint
    }

    /**
     * 归还租约；调用方必须与 [acquire] 配对，且归还后不得再使用该内核。
     * 按"当前配置指纹"匹配（短租约场景与 acquire 同配置即精确命中）；
     * 页面在租约期间遇到包配置变化时，请用显式指纹重载 [release] 归还旧租约。
     */
    @Synchronized
    fun release(segment: String) {
        releaseLocked(segment, currentExtensionConfigPlaceholder)
    }

    /** 归还租约（显式指纹：适用于页面持有期间包配置已变化的场景） */
    @Synchronized
    fun release(segment: String, extensionFingerprint: String) {
        releaseLocked(segment, extensionFingerprint)
    }

    private fun releaseLocked(segment: String, fingerprint: String) {
        val k = key(segment, fingerprint)
        val entry = entries[k] ?: return
        entry.refCount = (entry.refCount - 1).coerceAtLeast(0)
        if (entry.refCount > 0) {
            return
        }
        // 驱逐：无租约项按 LRU（accessOrder：迭代从头 = 最旧）只保留最近 maxCachedKeys 个，
        // 其余关闭。刚 release 的 key 位于链表末尾，必然保留。
        val idle = entries.entries.filter { it.value.refCount == 0 }
        val excess = idle.size - maxCachedKeys
        if (excess <= 0) {
            return
        }
        for ((keyToEvict, entryToEvict) in idle.take(excess)) {
            entries.remove(keyToEvict)
            entryToEvict.core.close()
        }
    }

    /** 最近一次由 [acquire]/[currentFingerprint] 观察到的配置指纹（release 单参匹配用） */
    @Volatile
    private var currentExtensionConfigPlaceholder: String = ""

    private fun rememberFingerprint(fingerprint: String) {
        currentExtensionConfigPlaceholder = fingerprint
    }

    /**
     * 清空无租约的内核（如主包被重置/重新导入时联动调用；暂无触发点，属预留接口）。
     * 仍有活跃租约的条目保留在表中：其合成数据独立于旧 MainPack 字节，仍可安全使用，
     * 归还后自然走驱逐逻辑，避免 core 泄漏。
     */
    @Synchronized
    fun reset() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (cachedKey, entry) = iterator.next()
            if (entry.refCount == 0) {
                iterator.remove()
                entry.core.close()
            }
        }
    }
}
