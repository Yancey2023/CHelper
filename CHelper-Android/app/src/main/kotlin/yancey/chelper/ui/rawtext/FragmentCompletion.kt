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

package yancey.chelper.ui.rawtext

import yancey.chelper.core.CHelperCore
import yancey.chelper.core.FragmentContext

/**
 * rawtext 编辑器补全入口：统一走内核（Parser/AutoSuggestion）。
 * 候选展示只读 name/描述；点选时通过内核 applySuggestion 直接取得成品文本与光标——
 * 不再手工拼串/特判（结构符号、scores/hasitem 光标引导均由内核返回）。
 * 内核未就绪或无候选 → 空列表。仅"记分板目标名"（文档数据）在本地提供。
 */
object FragmentCompletion {

    private const val MAX_SUGGESTIONS = 256

    private fun empty(): RawtextSuggestions = RawtextSuggestions(emptyList()) { null }

    private fun booleanHint(n: String, desc: String): String =
        if (desc.isNotEmpty()) desc
        else when (n) {
            "true" -> "开（是）"
            "false" -> "关（否）"
            else -> ""
        }

    /** 选择器字段补全：变量/@x、参数名、值、结构符号全部来自内核 */
    fun selector(
        value: String,
        caret: Int,
        @Suppress("UNUSED_PARAMETER") targets: RawtextDebugTargets,
    ): RawtextSuggestions {
        val core = RawtextCompletionKernel.core() ?: return empty()
        val c = caret.coerceIn(0, value.length)
        val items = withFragment(core, value, openSelector = true) { fragment ->
            val out = mutableListOf<RawtextSuggestion>()
            val size = fragment.getSuggestionsSize(c)
            for (which in 0 until size) {
                val s = fragment.getSuggestion(c, which) ?: continue
                val n = s.name ?: ""
                if (n.isEmpty()) continue
                out.add(RawtextSuggestion(n, booleanHint(n, s.description ?: "")))
                if (out.size >= MAX_SUGGESTIONS) break
            }
            out
        } ?: return empty()
        return RawtextSuggestions(items) { which ->
            withFragment(core, value, openSelector = true) { fragment ->
                fragment.applySuggestion(c, which)
            }
        }
    }

    /** 翻译识别符补全：内核 translate 键表（含中文名） */
    fun translate(value: String, caret: Int): RawtextSuggestions {
        val c = caret.coerceIn(0, value.length)
        // 空前缀不刷全量键表（与旧行为一致）
        val (_, prefix) = wordPrefixEnd(value, caret)
        if (prefix.isEmpty()) {
            return empty()
        }
        val core = RawtextCompletionKernel.core() ?: return empty()
        val items = withFragment(core, value, openSelector = false) { fragment ->
            val out = mutableListOf<RawtextSuggestion>()
            val size = fragment.getSuggestionsSize(c)
            for (which in 0 until size) {
                val s = fragment.getSuggestion(c, which) ?: continue
                val n = s.name ?: ""
                if (n.isEmpty()) continue
                out.add(RawtextSuggestion(n, s.description ?: ""))
                if (out.size >= MAX_SUGGESTIONS) break
            }
            out
        } ?: return empty()
        return RawtextSuggestions(items) { which ->
            withFragment(core, value, openSelector = false) { fragment ->
                fragment.applySuggestion(c, which)
            }
        }
    }

    /** 打开引擎片段并执行 block；异常/内核不可用返回 null */
    private inline fun <T> withFragment(
        core: CHelperCore,
        value: String,
        openSelector: Boolean,
        block: (FragmentContext) -> T?,
    ): T? {
        val fragment = try {
            if (openSelector) FragmentContext.openSelector(core, value)
            else FragmentContext.openId(core, "translate", value)
        } catch (_: Throwable) {
            null
        } ?: return null
        return try {
            block(fragment)
        } catch (_: Throwable) {
            null
        } finally {
            fragment.close()
        }
    }
}
