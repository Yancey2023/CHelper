/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

package yancey.chelper.ui.feature.rawtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import yancey.chelper.domain.rawtext.RawtextDocumentSnapshot
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot
import yancey.chelper.domain.rawtext.RawtextSegmentType

data class RawtextOutput(
    val formattedJson: String = "{\n  \"rawtext\": [{\"text\": \"\"}]\n}",
    val compressedJson: String = "{\"rawtext\":[{\"text\":\"\"}]}",
    val preview: AnnotatedString = AnnotatedString(""),
    val validation: List<String> = emptyList(),
    val isEmpty: Boolean = true,
)

class RawtextRenderer(document: RawtextDocumentSnapshot) {
    private val segments = document.segments
    private val mockPlayerP = document.mock.p
    private val mockPlayerR = document.mock.r
    private val mockPlayerA = document.mock.a
    private val mockPlayerS = document.mock.s
    private val mockScore = document.mock.score
    private val mockTranslate = document.mock.translate
    private val mockCondition = document.mock.condition
    private val parserJson = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compactJson = Json.Default

    fun render(): RawtextOutput {
        val root = buildJsonObject { put("rawtext", buildRawtextArray()) }
        return RawtextOutput(
            encodeJson(root, true),
            encodeJson(root, false),
            buildPreviewText(),
            validationMessages(),
            isPreviewEmpty(),
        )
    }

    fun getRawJson(pretty: Boolean = true): String {
        val root = buildJsonObject { put("rawtext", buildRawtextArray()) }
        return encodeJson(root, pretty)
    }

    fun validationMessages(): List<String> {
        val messages = mutableListOf<String>()
        for ((index, segment) in segments.withIndex()) {
            when (segment.kind) {
                RawtextSegmentType.Translate -> {
                    if (
                        segment.translateWithJson.isNotBlank() &&
                            parseJsonElementOrNull(segment.translateWithJson) == null
                    ) {
                        messages.add("第 ${index + 1} 段翻译参数不是合法 JSON，将按空数组处理。")
                    }
                }

                RawtextSegmentType.Conditional -> {
                    if (parseJsonElementOrNull(segment.conditionJson) == null) {
                        messages.add("第 ${index + 1} 段条件不是合法 JSON，将使用默认选择器条件。")
                    }
                    if (parseJsonElementOrNull(segment.thenJson) == null) {
                        messages.add("第 ${index + 1} 段条件输出不是合法 JSON，将使用空 rawtext。")
                    }
                }

                else -> Unit
            }
        }
        return messages
    }

    fun buildPreviewText(): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val style = MinecraftTextStyle()
        for (segment in segments) {
            when (segment.kind) {
                RawtextSegmentType.Text ->
                    appendFormattedMinecraftText(builder, segment.text, style)

                RawtextSegmentType.LineBreak -> builder.append("\n")
                RawtextSegmentType.Score -> appendPreviewChip(builder, mockScore, Color(0xFFFF5555))
                RawtextSegmentType.Selector ->
                    appendPreviewChip(
                        builder,
                        mockSelectorName(segment.selector),
                        Color(0xFF55FF55),
                    )

                RawtextSegmentType.Translate ->
                    appendPreviewChip(
                        builder,
                        mockTranslate.ifBlank { "[${segment.translateKey}]" },
                        Color(0xFFFFFF55),
                    )

                RawtextSegmentType.Conditional ->
                    appendPreviewChip(builder, mockCondition, Color(0xFFFF55FF))
            }
        }
        return builder.toAnnotatedString()
    }

    fun isPreviewEmpty(): Boolean =
        segments.all { segment ->
            segment.kind == RawtextSegmentType.Text && segment.text.isBlank()
        } && segments.none { it.kind == RawtextSegmentType.LineBreak }

    private fun buildRawtextArray(): JsonArray {
        val items = mutableListOf<JsonElement>()
        for (segment in segments) {
            when (segment.kind) {
                RawtextSegmentType.Text -> appendTextItem(items, segment.text)
                RawtextSegmentType.LineBreak -> appendTextItem(items, "\n")
                RawtextSegmentType.Score ->
                    items.add(
                        buildJsonObject {
                            putJsonObject("score") {
                                put("name", segment.scoreName)
                                put("objective", segment.scoreObjective)
                            }
                        }
                    )

                RawtextSegmentType.Selector ->
                    items.add(buildJsonObject { put("selector", segment.selector) })

                RawtextSegmentType.Translate ->
                    items.add(
                        buildJsonObject {
                            put("translate", segment.translateKey)
                            put(
                                "with",
                                parseJsonElementOrNull(segment.translateWithJson)
                                    ?: JsonArray(emptyList()),
                            )
                        }
                    )

                RawtextSegmentType.Conditional -> items.add(buildConditionalJson(segment))
            }
        }
        if (items.isEmpty()) {
            items.add(buildJsonObject { put("text", "") })
        }
        return JsonArray(items)
    }

    private fun buildConditionalJson(segment: RawtextSegmentSnapshot): JsonObject {
        val condition =
            parseJsonElementOrNull(segment.conditionJson)
                ?: buildJsonObject { put("selector", "@p") }
        val thenElement = parseJsonElementOrNull(segment.thenJson) ?: JsonArray(emptyList())
        val thenArray =
            when (thenElement) {
                is JsonArray -> thenElement
                else -> JsonArray(listOf(thenElement))
            }
        return buildJsonObject {
            put("translate", "%%2")
            putJsonObject("with") {
                putJsonArray("rawtext") {
                    add(condition)
                    addJsonObject { put("rawtext", thenArray) }
                }
            }
        }
    }

    private fun appendTextItem(items: MutableList<JsonElement>, text: String) {
        if (text.isEmpty()) return
        val last = items.lastOrNull() as? JsonObject
        val lastText = last?.get("text")?.jsonPrimitive?.contentOrNull
        if (lastText != null && last.size == 1) {
            items[items.lastIndex] = buildJsonObject { put("text", lastText + text) }
        } else {
            items.add(buildJsonObject { put("text", text) })
        }
    }

    private fun parseJsonElementOrNull(value: String): JsonElement? =
        try {
            parserJson.parseToJsonElement(value)
        } catch (_: Exception) {
            null
        }

    private fun encodeJson(element: JsonElement, pretty: Boolean): String =
        if (pretty) {
            prettyJson.encodeToString(element)
        } else {
            compactJson.encodeToString(element)
        }

    private fun appendFormattedMinecraftText(
        builder: AnnotatedString.Builder,
        text: String,
        style: MinecraftTextStyle,
    ) {
        var buffer = StringBuilder()
        fun flush() {
            if (buffer.isEmpty()) return
            val start = builder.length
            builder.append(buffer.toString())
            builder.addStyle(style.toSpanStyle(), start, builder.length)
            buffer = StringBuilder()
        }

        var index = 0
        while (index < text.length) {
            if (text[index] == '§' && index + 1 < text.length) {
                flush()
                style.applyCode(text[index + 1].lowercaseChar())
                index += 2
            } else {
                buffer.append(text[index])
                index++
            }
        }
        flush()
    }

    private fun appendPreviewChip(builder: AnnotatedString.Builder, text: String, color: Color) {
        val display = text.ifBlank { "..." }
        val start = builder.length
        builder.append(display)
        builder.addStyle(
            SpanStyle(
                color = color,
                fontStyle = FontStyle.Italic,
                background = color.copy(alpha = 0.18f),
            ),
            start,
            builder.length,
        )
    }

    private fun mockSelectorName(selector: String): String =
        when {
            selector.startsWith("@p") -> mockPlayerP
            selector.startsWith("@r") -> mockPlayerR
            selector.startsWith("@a") -> mockPlayerA
            selector.startsWith("@s") -> mockPlayerS
            selector.startsWith("@e") -> "实体"
            selector.startsWith("@n") -> "最近实体"
            else -> selector
        }

    private class MinecraftTextStyle {
        private var color: Color = Color.White
        private var bold: Boolean = false
        private var italic: Boolean = false
        private var underline: Boolean = false
        private var strikeThrough: Boolean = false

        fun applyCode(code: Char) {
            colorMap[code]?.let { nextColor ->
                color = nextColor
                bold = false
                italic = false
                underline = false
                strikeThrough = false
                return
            }
            when (code) {
                'l' -> bold = true
                'o' -> italic = true
                'n' -> underline = true
                'm' -> strikeThrough = true
                'r' -> reset()
                'k' -> italic = true
            }
        }

        fun toSpanStyle(): SpanStyle {
            val decorations = mutableListOf<TextDecoration>()
            if (underline) decorations.add(TextDecoration.Underline)
            if (strikeThrough) decorations.add(TextDecoration.LineThrough)
            return SpanStyle(
                color = color,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration =
                    if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            )
        }

        private fun reset() {
            color = Color.White
            bold = false
            italic = false
            underline = false
            strikeThrough = false
        }
    }

    companion object {
        private val colorMap =
            mapOf(
                '0' to Color(0xFF000000),
                '1' to Color(0xFF0000AA),
                '2' to Color(0xFF00AA00),
                '3' to Color(0xFF00AAAA),
                '4' to Color(0xFFAA0000),
                '5' to Color(0xFFAA00AA),
                '6' to Color(0xFFFFAA00),
                '7' to Color(0xFFAAAAAA),
                '8' to Color(0xFF555555),
                '9' to Color(0xFF5555FF),
                'a' to Color(0xFF55FF55),
                'b' to Color(0xFF55FFFF),
                'c' to Color(0xFFFF5555),
                'd' to Color(0xFFFF55FF),
                'e' to Color(0xFFFFFF55),
                'f' to Color(0xFFFFFFFF),
                'g' to Color(0xFFDDD605),
                'h' to Color(0xFFE3D4D1),
                'i' to Color(0xFFCECACA),
                'j' to Color(0xFF443A3B),
                'm' to Color(0xFF971607),
                'n' to Color(0xFFB4684D),
                'p' to Color(0xFFDEB12D),
                'q' to Color(0xFF47A036),
                's' to Color(0xFF2CBAA8),
                't' to Color(0xFF21497B),
                'u' to Color(0xFF9A5CC6),
            )
    }
}
