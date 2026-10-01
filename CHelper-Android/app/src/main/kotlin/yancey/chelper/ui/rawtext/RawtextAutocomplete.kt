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

import yancey.chelper.core.ClickSuggestionResult

/**
 * 字段补全建议（纯展示）：文本 + 说明。真正"应用"统一走 [RawtextSuggestions.apply]：
 * 引擎字段由内核直接返回成品文本与光标；本地字段（记分板目标名）由闭包实现同一语义。
 */
data class RawtextSuggestion(
    val text: String,
    val hint: String = "",
    val hintOnly: Boolean = false,
)

/** 一次补全查询的结果：候选列表 + 按序号应用（统一产出成品文本与光标，无手工拼串特判） */
class RawtextSuggestions(
    val items: List<RawtextSuggestion>,
    private val applyAt: (Int) -> ClickSuggestionResult?,
) {
    fun apply(which: Int): ClickSuggestionResult? = applyAt(which)
}

/** 光标前正在输入的"词"（含中文/字母/数字/._）：返回 (词起点, 词前缀) */
fun wordPrefixEnd(value: String, caret: Int): Pair<Int, String> {
    val c = caret.coerceIn(0, value.length)
    val before = value.substring(0, c)
    val m = Regex("([\u4e00-\u9fa5a-zA-Z0-9_.]*)$").find(before)
    val prefix = m?.groupValues?.get(1) ?: ""
    return (c - prefix.length) to prefix
}

/** 仅保留"记分板目标名"一项本地数据（来自文档/调试目标，内核不提供） */
object RawtextAutocomplete {

    private fun hintOnly(text: String) = listOf(RawtextSuggestion(text, hintOnly = true))

    /** 记分板目标补全：候选来自调试文档目标；应用 = 替换光标前输入词 */
    fun objective(value: String, caret: Int, targets: RawtextDebugTargets): RawtextSuggestions {
        val c = caret.coerceIn(0, value.length)
        val (start, prefix) = wordPrefixEnd(value, caret)
        val items = mutableListOf<RawtextSuggestion>()
        (targets.condScores + targets.displayScores).sorted()
            .filter { it.startsWith(prefix) }
            .forEach { items.add(RawtextSuggestion(it, "记分板目标")) }
        if (items.isEmpty()) {
            items.addAll(hintOnly("输入记分板目标，如 kills / 金币"))
        }
        return RawtextSuggestions(items) { which ->
            val name = items.getOrNull(which)?.text ?: return@RawtextSuggestions null
            if (name.isEmpty()) return@RawtextSuggestions null
            ClickSuggestionResult().apply {
                text = value.substring(0, start.coerceIn(0, c)) + name + value.substring(c)
                selection = start.coerceIn(0, c) + name.length
            }
        }
    }
}
