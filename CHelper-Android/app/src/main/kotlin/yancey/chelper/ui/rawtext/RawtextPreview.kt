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

import androidx.compose.ui.text.AnnotatedString

/** 预览渲染引擎：把元素树渲染成「玩家实际看到的内容」 */
object RawtextPreviewEngine {

    fun render(elements: List<RawtextElement>, dbg: RawtextDebugState): AnnotatedString {
        val state = StyleState()
        val sb = AnnotatedString.Builder()
        elements.forEach { renderEl(sb, it, dbg, state) }
        return sb.toAnnotatedString()
    }

    private fun renderEl(sb: AnnotatedString.Builder, e: RawtextElement, dbg: RawtextDebugState, state: StyleState) {
        when (e) {
            is RawtextElement.Text -> sb.append(styledText(e.text, state))
            is RawtextElement.Translate -> renderKeyed(sb, e.key, e.withMode, e.args, e.raw, dbg, state)
            is RawtextElement.Selector -> {
                val raw = e.rawSelector.trim()
                if (!dbg.hiddenSelectors.contains(raw) &&
                    RawtextDebugEngine.selectorMatches(RawtextSelectorParser.parse(raw), dbg)
                ) {
                    sb.append(styledText("⟦选择器⟧ " + raw, state))
                }
            }

            is RawtextElement.Score -> {
                val v = if (dbg.arrayScores.contains(e.objective)) {
                    val expr = dbg.arrayScoreText[e.objective] ?: ""
                    RawtextScoreArray.firstActiveValue(expr) ?: expr.ifEmpty { "0" }
                } else {
                    dbg.scores[e.objective] ?: "0"
                }
                sb.append(styledText(v.ifEmpty { "0" }, state))
            }

            is RawtextElement.Condition -> {
                if (e.mode == RawtextConditionMode.Sequence) {
                    val key = e.seqKey.trim().let {
                        if (it.isNotEmpty() && Regex("%%[1-9]").containsMatchIn(it)) it
                        else e.seqSlots.indices.joinToString("") { i -> "%%" + (i + 1) }
                    }
                    renderKeyed(sb, key, RawtextWithMode.Rawtext, emptyList(), e.seqSlots, dbg, state)
                } else {
                    renderKeyed(sb, conditionKey(e), RawtextWithMode.Rawtext, emptyList(), conditionChildren(e), dbg, state)
                }
            }
        }
    }

    private fun conditionKey(e: RawtextElement.Condition): String {
        val n = e.branches.size - (if (e.hasBlank) 1 else 0)
        return "%%" + (n + 1).coerceAtLeast(1)
    }

    private fun conditionChildren(e: RawtextElement.Condition): List<RawtextElement> {
        val out = mutableListOf<RawtextElement>()
        val entries = if (e.hasBlank) e.branches.dropLast(1) else e.branches
        entries.forEach { b ->
            out.add(RawtextElement.Selector(b.selectorRaw?.trim() ?: RawtextSelectorParser.string(b.sel ?: RawtextParsedSelector(e.target.ifEmpty { "@s" }))))
        }
        entries.forEach { b -> out.add(branchElement(b)) }
        if (e.hasBlank) e.branches.lastOrNull()?.let { out.add(branchElement(it)) }
        return out
    }

    private fun branchElement(b: RawtextBranch): RawtextElement {
        val items = b.items
        return if (items.isNotEmpty()) {
            if (items.size == 1) items[0]
            else RawtextElement.Translate("", RawtextWithMode.Rawtext, mutableListOf(), items)
        } else {
            RawtextElement.Text("")
        }
    }

    private fun renderKeyed(
        sb: AnnotatedString.Builder,
        key: String,
        mode: RawtextWithMode,
        args: List<String>,
        raw: List<RawtextElement>,
        dbg: RawtextDebugState,
        state: StyleState,
    ) {
        if (mode == RawtextWithMode.Rawtext && Regex("^%%[2-9]$").matches(key)) {
            renderConditional(sb, key, raw, dbg, state)
            return
        }
        if (mode == RawtextWithMode.Rawtext && Regex("^(?:%%[1-9]){2,}$").matches(key)) {
            raw.forEach { renderEl(sb, it, dbg, state) }
            return
        }
        if (mode == RawtextWithMode.Rawtext && Regex("%%[1-9]").containsMatchIn(key)) {
            renderMixed(sb, key, raw, dbg, state)
            return
        }
        if (mode == RawtextWithMode.Array && Regex("%%[1-9]").containsMatchIn(key)) {
            var pos = 0
            for (m in Regex("%%[1-9]").findAll(key)) {
                if (m.range.first > pos) sb.append(styledText(key.substring(pos, m.range.first), state))
                val idx = m.value.substring(2).toInt() - 1
                if (idx in args.indices) sb.append(styledText(args[idx], state))
                pos = m.range.last + 1
            }
            if (pos < key.length) sb.append(styledText(key.substring(pos), state))
            return
        }
        val zh = RawtextDatasets.translateMap[key]
        if (zh != null) {
            var text = zh
            if (mode == RawtextWithMode.Array && args.isNotEmpty()) {
                var ai = 0
                text = Regex("%\\d+\\$?[sd]|%[sd]").replace(text) {
                    if (ai < args.size) args[ai++] else "…"
                }
            } else if (mode == RawtextWithMode.Rawtext && raw.isNotEmpty()) {
                text = Regex("%[sd]").replace(text) { "…" }
            }
            sb.append(styledText(text, state))
        } else {
            sb.append(styledText("⟦翻译⟧ " + key.ifEmpty { "..." }, state))
        }
    }

    private fun renderConditional(sb: AnnotatedString.Builder, key: String, arr: List<RawtextElement>, dbg: RawtextDebugState, state: StyleState) {
        val n = key.removePrefix("%%").toIntOrNull() ?: 0
        val condCount = (n - 1).coerceAtLeast(0)
        val conds = arr.take(condCount)
        val opts = arr.drop(condCount)
        // Minecraft %%N 条件语义：过滤掉未命中的选择器后，顺位显示第 N 个（1 起）
        val matched = conds.filter { c -> c is RawtextElement.Selector && RawtextDebugEngine.selectorMatches(RawtextSelectorParser.parse(c.rawSelector), dbg) }
        val remaining = matched + opts
        remaining.getOrNull(n - 1)?.let { renderEl(sb, it, dbg, state) }
    }

    private fun renderMixed(sb: AnnotatedString.Builder, key: String, arr: List<RawtextElement>, dbg: RawtextDebugState, state: StyleState) {
        var pos = 0
        for (m in Regex("%%[1-9]").findAll(key)) {
            if (m.range.first > pos) sb.append(styledText(key.substring(pos, m.range.first), state))
            val idx = m.value.substring(2).toInt() - 1
            arr.getOrNull(idx)?.let { renderEl(sb, it, dbg, state) }
            pos = m.range.last + 1
        }
        if (pos < key.length) sb.append(styledText(key.substring(pos), state))
    }
}
