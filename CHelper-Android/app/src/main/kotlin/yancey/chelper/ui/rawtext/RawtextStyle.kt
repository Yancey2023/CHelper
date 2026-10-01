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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** 基岩版 § 经典颜色表（0-f） */
val CLASSIC_COLORS: Map<Char, Color> = mapOf(
    '0' to Color(0xFF000000), '1' to Color(0xFF0000AA), '2' to Color(0xFF00AA00),
    '3' to Color(0xFF00AAAA), '4' to Color(0xFFAA0000), '5' to Color(0xFFAA00AA),
    '6' to Color(0xFFFFAA00), '7' to Color(0xFFAAAAAA), '8' to Color(0xFF555555),
    '9' to Color(0xFF5555FF), 'a' to Color(0xFF55FF55), 'b' to Color(0xFF55FFFF),
    'c' to Color(0xFFFF5555), 'd' to Color(0xFFFF55FF), 'e' to Color(0xFFFFFF55),
    'f' to Color(0xFFFFFFFF),
)

/** 基岩版 § 扩展材质色表（g-u，官方定义） */
val EXTENDED_COLORS: Map<Char, Color> = mapOf(
    'g' to Color(0xFFDDD605), 'h' to Color(0xFFE3D4D1), 'i' to Color(0xFFCECACA),
    'j' to Color(0xFF443A3B), 'm' to Color(0xFF971607), 'n' to Color(0xFFB4684D),
    'p' to Color(0xFFDEB12D), 'q' to Color(0xFF47A036), 's' to Color(0xFF2CBAA8),
    't' to Color(0xFF21497B), 'u' to Color(0xFF9A5CC6),
)

val ALL_COLORS: Map<Char, Color> = CLASSIC_COLORS + EXTENDED_COLORS

class StyleState {
    var color: Color? = null
    var bold: Boolean = false
    var italic: Boolean = false
}

/** § 代码 → AnnotatedString（换行保留，颜色/粗体/斜体着色） */
fun styledText(text: String, state: StyleState = StyleState()): AnnotatedString {
    val sb = StringBuilder()
    val spans = mutableListOf<Pair<IntRange, SpanStyle>>()
    var start = 0

    fun flush() {
        // 无样式段落不加 span（避免 Color.Unspecified 在真机上渲染成透明/黑）
        if (sb.length > start && (state.color != null || state.bold || state.italic)) {
            val style = SpanStyle(
                color = state.color ?: Color.Unspecified,
                fontWeight = if (state.bold) FontWeight.Bold else null,
                fontStyle = if (state.italic) FontStyle.Italic else null,
            )
            spans.add(start until sb.length to style)
        }
        start = sb.length
    }

    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '\n') {
            flush()
            sb.append('\n')
            start = sb.length
            i++
            continue
        }
        if (ch == '§' && i + 1 < text.length) {
            val code = text[i + 1].lowercaseChar()
            flush()
            when (code) {
                'r' -> {
                    state.color = null
                    state.bold = false
                    state.italic = false
                }

                'l' -> state.bold = true
                'o' -> state.italic = true
                'k' -> {} // 乱码效果在静态预览中无法模拟，忽略
                else -> ALL_COLORS[code]?.let { state.color = it }
            }
            i += 2
            continue
        }
        sb.append(ch)
        i++
    }
    flush()

    return buildAnnotatedString {
        append(sb.toString())
        spans.forEach { (range, style) ->
            if (!range.isEmpty()) addStyle(style, range.first, range.last + 1)
        }
    }
}

/** 摘要文本：换行显示为字面 \n 标识符 */
fun summaryText(text: String): String = text.replace("\n", "\\n").ifEmpty { "（空）" }
