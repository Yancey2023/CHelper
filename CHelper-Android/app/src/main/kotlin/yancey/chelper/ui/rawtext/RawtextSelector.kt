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

import kotlinx.serialization.Serializable

/** 中文输入法全角字符 → 半角（1:1 替换，索引不变） */
fun normalizeFullwidth(s: String): String = s
    .replace('［', '[').replace('】', ']').replace('【', '[').replace('］', ']')
    .replace('｛', '{').replace('｝', '}')
    .replace('，', ',').replace('、', ',').replace('。', ',')
    .replace('＝', '=').replace('：', ':')
    .replace('（', '(').replace('）', ')')
    .replace('！', '!').replace('～', '~')

/** 选择器单个参数 */
@Serializable
data class RawtextSelectorArg(
    var key: String,
    var negate: Boolean = false,
    var value: String = "",
    var objective: String = "",
    var range: String = "",
    var items: MutableList<RawtextHasItem> = mutableListOf(),
)

/** hasitem 单条件 */
@Serializable
data class RawtextHasItem(
    var item: String = "",
    var quantity: String = "",
    var data: String = "",
    var location: String = "",
    var slot: String = "",
)

/** 解析后的选择器：变量 + 参数列表 */
@Serializable
data class RawtextParsedSelector(var variable: String = "@s", var args: MutableList<RawtextSelectorArg> = mutableListOf())

object RawtextSelectorParser {

    private fun extractBracket(s: String, open: Char, close: Char): String? {
        val i = s.indexOf(open)
        if (i < 0) return null
        var depth = 0
        for (j in i until s.length) {
            when (s[j]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return s.substring(i, j + 1)
                }
            }
        }
        return null
    }

    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '[', '{' -> {
                    depth++
                    sb.append(c)
                }

                ']', '}' -> {
                    depth--
                    sb.append(c)
                }

                sep -> if (depth == 0) {
                    out.add(sb.toString())
                    sb.clear()
                } else {
                    sb.append(c)
                }

                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun findTopLevel(s: String, ch: Char): Int {
        var depth = 0
        for ((i, c) in s.withIndex()) {
            when (c) {
                '[', '{' -> depth++
                ']', '}' -> depth--
                ch -> if (depth == 0) return i
            }
        }
        return -1
    }

    fun parse(str: String): RawtextParsedSelector {
        return try {
            parseInternal(str)
        } catch (e: Exception) {
            // 半成品选择器（如 "[", "]", ","）绝不能导致崩溃
            RawtextParsedSelector("@s", mutableListOf())
        }
    }

    private fun parseInternal(str: String): RawtextParsedSelector {
        // 中文输入法全角 → 半角（1:1 替换）
        val s = normalizeFullwidth(str.trim())
        // 变量：@字母开头（兼容自定义变量 @x/@x2/@my_var 等；不做内置名单限制）
        val m = Regex("^@[A-Za-z_][A-Za-z0-9_]*").find(s)
        val variable = m?.value ?: "@s"
        val rest = if (s.length > variable.length) s.substring(variable.length) else ""
        val sel = RawtextParsedSelector(variable, mutableListOf())
        val inner = extractBracket(rest, '[', ']')
        if (inner != null) {
            val body = inner.substring(1, inner.length - 1)
            for (pair in splitTopLevel(body, ',')) {
                val eq = findTopLevel(pair, '=')
                if (eq < 0) continue
                val key = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                when (key) {
                    "scores" -> {
                        val b = extractBracket(value, '{', '}')
                        if (b != null) {
                            val inner2 = b.substring(1, b.length - 1)
                            val eq2 = findTopLevel(inner2, '=')
                            if (eq2 >= 0) {
                                var range = inner2.substring(eq2 + 1).trim()
                                var negate = false
                                if (range.startsWith("!")) {
                                    negate = true
                                    range = range.substring(1)
                                }
                                sel.args.add(
                                    RawtextSelectorArg(
                                        "scores",
                                        negate,
                                        "",
                                        inner2.substring(0, eq2).trim().trim('"'),
                                        range
                                    )
                                )
                            }
                        }
                    }

                    "hasitem" -> sel.args.add(
                        RawtextSelectorArg("hasitem", false, "", "", "", parseHasitem(value).toMutableList())
                    )

                    else -> {
                        var negate = false
                        var v = value
                        if (v.startsWith("!")) {
                            negate = true
                            v = v.substring(1)
                        }
                        v = v.trim('"')
                        if (key == "m") {
                            v = when (v.lowercase()) {
                                "0", "s" -> "survival"
                                "1", "c" -> "creative"
                                "2", "a" -> "adventure"
                                "3" -> "spectator"
                                else -> v
                            }
                        }
                        sel.args.add(RawtextSelectorArg(key, negate, v))
                    }
                }
            }
        }
        return sel
    }

    private fun parseHasitem(value: String): List<RawtextHasItem> {
        val out = mutableListOf<RawtextHasItem>()
        val v = value.trim()
        if (v.isEmpty()) return out
        val bodies = if (v.startsWith("[") && v.length >= 2) {
            val inner = v.substring(1, v.length - 1)
            splitTopLevel(inner, ',').map { it.trim() }
        } else {
            listOf(v)
        }
        for (b in bodies) {
            val body = if (b.startsWith("{") && b.length >= 2) b.substring(1, b.length - 1) else b
            val it = RawtextHasItem()
            for (pair in splitTopLevel(body, ',')) {
                val eq = findTopLevel(pair, '=')
                if (eq < 0) continue
                val k = pair.substring(0, eq).trim()
                val vl = pair.substring(eq + 1).trim()
                when (k) {
                    "item" -> it.item = vl
                    "quantity" -> it.quantity = vl
                    "data" -> it.data = vl
                    "location" -> it.location = vl
                    "slot" -> it.slot = vl
                }
            }
            out.add(it)
        }
        return out
    }

    private fun hasitemToString(c: RawtextHasItem): String {
        val parts = mutableListOf<String>()
        if (c.item.isNotEmpty()) parts.add("item=" + c.item)
        if (c.quantity.isNotEmpty()) parts.add("quantity=" + c.quantity)
        if (c.data.isNotEmpty()) parts.add("data=" + c.data)
        if (c.location.isNotEmpty()) parts.add("location=" + c.location)
        if (c.slot.isNotEmpty()) parts.add("slot=" + c.slot)
        return "{" + parts.joinToString(",") + "}"
    }

    private fun argToString(a: RawtextSelectorArg): String {
        return when (a.key) {
            "scores" -> {
                if (a.objective.isEmpty()) ""
                else "scores={" + a.objective + "=" + (if (a.negate) "!" else "") + a.range + "}"
            }

            "hasitem" -> {
                val conds = a.items.filter { it.item.isNotEmpty() }.map { hasitemToString(it) }
                if (conds.isEmpty()) ""
                else "hasitem=" + (if (conds.size == 1) conds[0] else "[" + conds.joinToString(",") + "]")
            }

            else -> {
                if (a.value.isEmpty()) ""
                else a.key + "=" + (if (a.negate) "!" else "") + a.value
            }
        }
    }

    fun string(sel: RawtextParsedSelector): String {
        val parts = sel.args.map { argToString(it) }.filter { it.isNotEmpty() }
        return sel.variable + (if (parts.isNotEmpty()) "[" + parts.joinToString(",") + "]" else "")
    }
}
