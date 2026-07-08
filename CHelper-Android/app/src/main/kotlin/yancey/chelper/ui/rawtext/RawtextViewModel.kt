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

package yancey.chelper.ui.rawtext

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import yancey.chelper.data.RawtextDataStore

enum class RawtextSegmentType {
    Text,
    LineBreak,
    Score,
    Selector,
    Translate,
    Conditional
}

class RawtextFormat(
    val code: String,
    val name: String,
    val color: Color,
    val isLight: Boolean,
    val isFormat: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

class RawtextSegment(
    val id: Long,
    val type: RawtextSegmentType,
    textValue: TextFieldValue = TextFieldValue(),
    scoreName: String = "@p",
    scoreObjective: String = "score",
    selector: String = "@p",
    translateKey: String = "key.example",
    translateWithJson: String = "[{\"text\":\"example\"}]",
    conditionJson: String = "{\"selector\":\"@p\"}",
    thenJson: String = "[{\"text\":\"Success!\"}]",
) {
    var textValue by mutableStateOf(textValue)
    var scoreName by mutableStateOf(scoreName)
    var scoreObjective by mutableStateOf(scoreObjective)
    var selector by mutableStateOf(selector)
    var translateKey by mutableStateOf(translateKey)
    var translateWithJson by mutableStateOf(translateWithJson)
    var conditionJson by mutableStateOf(conditionJson)
    var thenJson by mutableStateOf(thenJson)

    val title: String
        get() = when (type) {
            RawtextSegmentType.Text -> "文本"
            RawtextSegmentType.LineBreak -> "换行"
            RawtextSegmentType.Score -> "计分板"
            RawtextSegmentType.Selector -> "选择器"
            RawtextSegmentType.Translate -> "翻译"
            RawtextSegmentType.Conditional -> "条件"
        }

    fun summary(): String = when (type) {
        RawtextSegmentType.Text -> textValue.text.ifBlank { "空文本段" }
        RawtextSegmentType.LineBreak -> "换行"
        RawtextSegmentType.Score -> "${scoreName.ifBlank { "@p" }} : ${scoreObjective.ifBlank { "score" }}"
        RawtextSegmentType.Selector -> selector.ifBlank { "@p" }
        RawtextSegmentType.Translate -> translateKey.ifBlank { "key.example" }
        RawtextSegmentType.Conditional -> "IF ${conditionJson.ifBlank { "{}" }} THEN ${thenJson.ifBlank { "[]" }}"
    }
}

class RawtextViewModel(application: Application) : AndroidViewModel(application) {
    private val parserJson = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compactJson = Json.Default
    private val draftJson = Json { ignoreUnknownKeys = true }

    private val dataStore = RawtextDataStore(application.applicationContext)

    private var nextId by mutableLongStateOf(1L)

    val segments = mutableStateListOf<RawtextSegment>()

    var activeTextSegmentId by mutableStateOf<Long?>(null)
    var copyFormat by mutableStateOf("formatted")
    var autoSaveEnabled by mutableStateOf(false)
    var showAutoSavePrompt by mutableStateOf(false)
    var mockPlayerP by mutableStateOf("Steve")
    var mockPlayerR by mutableStateOf("Alex")
    var mockPlayerA by mutableStateOf("Steve, Alex, ...")
    var mockPlayerS by mutableStateOf("执行者")
    var mockScore by mutableStateOf("100")
    var mockTranslate by mutableStateOf("翻译文本")
    var mockCondition by mutableStateOf("条件内容")

    private var restored = false

    init {
        val first = createTextSegment()
        segments.add(first)
        activeTextSegmentId = first.id
        restorePreferences()
    }

    /**
     * 启动时读偏好：复制格式、自动保存开关、草稿、mock 值。
     * 首次进入（autoSavePrompted == false）会触发一次"开启动态保存？"询问。
     */
    private fun restorePreferences() {
        viewModelScope.launch {
            val prefs = dataStore.preferences().first()
            copyFormat = prefs.copyFormat ?: "formatted"
            autoSaveEnabled = prefs.autoSaveEnabled
            prefs.mockSettings?.let { applyMockJson(it) }
            if (prefs.autoSaveEnabled && !prefs.draft.isNullOrBlank()) {
                runCatching { applyDraftJson(prefs.draft) }
            }
            showAutoSavePrompt = !prefs.autoSavePrompted
            restored = true
        }
    }

    fun updateCopyFormat(format: String) {
        copyFormat = format
        viewModelScope.launch { dataStore.setCopyFormat(format) }
    }

    fun confirmAutoSave(enabled: Boolean) {
        autoSaveEnabled = enabled
        showAutoSavePrompt = false
        viewModelScope.launch {
            dataStore.setAutoSave(prompted = true, enabled = enabled)
            if (enabled) dataStore.saveDraft(serializeDraft())
        }
    }

    fun setAutoSave(enabled: Boolean) {
        autoSaveEnabled = enabled
        viewModelScope.launch {
            dataStore.setAutoSave(prompted = true, enabled = enabled)
            if (enabled) dataStore.saveDraft(serializeDraft()) else dataStore.saveDraft(null)
        }
    }

    fun persistMockSettings() {
        viewModelScope.launch { dataStore.saveMockSettings(serializeMock()) }
    }

    /** 内容变化后调用：开了自动保存才落盘，避免无意义 IO。 */
    private fun onContentChanged() {
        if (!restored || !autoSaveEnabled) return
        viewModelScope.launch { dataStore.saveDraft(serializeDraft()) }
    }

    /**
     * 文本段内容变化。若文本里出现换行符，就地拆成 文本 / LineBreak / 文本…
     * 让换行变成文档流里的真实换行段（标签也能跟着换行）。
     */
    fun updateTextSegment(id: Long, value: TextFieldValue) {
        val segment = findSegment(id) ?: return
        activeTextSegmentId = id
        if (!value.text.contains('\n')) {
            segment.textValue = value
            onContentChanged()
            return
        }

        val index = segments.indexOf(segment)
        // 光标前的字符里有几个换行，决定拆分后焦点落在第几段
        val caret = value.selection.start.coerceIn(0, value.text.length)
        val newlinesBeforeCaret = value.text.substring(0, caret).count { it == '\n' }
        val parts = value.text.split("\n")

        val inserted = mutableListOf<RawtextSegment>()
        parts.forEachIndexed { i, part ->
            if (i > 0) inserted.add(createLineBreakSegment())
            inserted.add(createTextSegment(TextFieldValue(part)))
        }
        segments.removeAt(index)
        segments.addAll(index, inserted)

        // 焦点落到光标所在的那段文本，光标移到该段开头对应位置
        val textSegmentsInserted = inserted.filter { it.type == RawtextSegmentType.Text }
        val focus = textSegmentsInserted.getOrNull(newlinesBeforeCaret) ?: textSegmentsInserted.lastOrNull()
        focus?.let {
            val offset = parts.getOrNull(newlinesBeforeCaret)?.length ?: 0
            it.textValue = it.textValue.copy(selection = TextRange(offset.coerceAtMost(it.textValue.text.length)))
            activeTextSegmentId = it.id
        }
        normalize()
        onContentChanged()
    }

    /**
     * 文本段最前面按退格：吞掉前一个段（换行或功能标签），实现"退格删标签/合并行"。
     * 返回 true 表示已处理（拦截这次退格）。
     */
    fun onBackspaceAtStart(id: Long): Boolean {
        val index = segments.indexOfFirst { it.id == id }
        if (index <= 0) return false
        val prev = segments[index - 1]
        when (prev.type) {
            RawtextSegmentType.Text -> {
                // 前面是文本段（一般出现在标签之间），合并两段，光标落在接缝
                val merged = prev.textValue.text
                val current = segments[index]
                val combined = merged + current.textValue.text
                current.textValue = TextFieldValue(combined, TextRange(merged.length))
                segments.removeAt(index - 1)
                activeTextSegmentId = current.id
            }

            else -> {
                // 前面是换行 / 功能标签，直接删掉
                segments.removeAt(index - 1)
            }
        }
        normalize()
        onContentChanged()
        return true
    }

    fun findSegment(id: Long): RawtextSegment? = segments.firstOrNull { it.id == id }

    fun addTextSegment(afterId: Long? = activeTextSegmentId): Long {
        val segment = createTextSegment()
        insertAfter(afterId, segment)
        activeTextSegmentId = segment.id
        normalize()
        onContentChanged()
        return segment.id
    }

    fun addFeature(type: RawtextSegmentType): Long? {
        if (type == RawtextSegmentType.Text) {
            return addTextSegment()
        }

        val feature = if (type == RawtextSegmentType.LineBreak) createLineBreakSegment() else createFeatureSegment(type)
        val activeSegment = activeTextSegmentId?.let { findSegment(it) }
        if (activeSegment?.type != RawtextSegmentType.Text) {
            insertAfter(activeTextSegmentId, feature)
            normalize()
            onContentChanged()
            return feature.id.takeIf { type != RawtextSegmentType.LineBreak }
        }

        val index = segments.indexOf(activeSegment)
        val value = activeSegment.textValue
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val before = value.text.substring(0, start)
        val after = value.text.substring(end)

        activeSegment.textValue = TextFieldValue(before, TextRange(before.length))
        segments.add(index + 1, feature)
        val nextText = createTextSegment(TextFieldValue(after, TextRange(0)))
        segments.add(index + 2, nextText)
        activeTextSegmentId = nextText.id
        normalize()
        onContentChanged()
        return feature.id.takeIf { type != RawtextSegmentType.LineBreak }
    }

    /** 功能段弹窗保存后调用，触发草稿落盘。 */
    fun commitSegmentEdit() {
        onContentChanged()
    }

    fun removeSegment(id: Long) {
        val index = segments.indexOfFirst { it.id == id }
        if (index < 0) return
        segments.removeAt(index)
        // 焦点尽量落到相邻文本段
        activeTextSegmentId = segments.getOrNull(index)?.takeIf { it.type == RawtextSegmentType.Text }?.id
            ?: segments.getOrNull(index - 1)?.takeIf { it.type == RawtextSegmentType.Text }?.id
                    ?: activeTextSegmentId
        normalize()
        onContentChanged()
    }

    fun moveSegment(id: Long, offset: Int) {
        val currentIndex = segments.indexOfFirst { it.id == id }
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + offset).coerceIn(0, segments.lastIndex)
        if (currentIndex == targetIndex) return
        val item = segments.removeAt(currentIndex)
        segments.add(targetIndex, item)
        normalize()
        onContentChanged()
    }

    fun clearAll() {
        segments.clear()
        val first = createTextSegment()
        segments.add(first)
        activeTextSegmentId = first.id
        onContentChanged()
    }

    fun applyFormat(format: RawtextFormat) {
        val segment = getOrCreateActiveTextSegment()
        val value = segment.textValue
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
        val selected = value.text.substring(start, end)
        val replacement = when {
            start != end && format.code != "§r" -> "${format.code}${selected}§r"
            start != end -> "${format.code}${selected}"
            else -> format.code
        }
        val newText = value.text.replaceRange(start, end, replacement)
        val cursor = start + replacement.length
        segment.textValue = TextFieldValue(newText, TextRange(cursor))
        activeTextSegmentId = segment.id
        onContentChanged()
    }

    /**
     * 归一化文档流，保证：
     *   1. 开头和结尾都是文本段（光标有处可落）
     *   2. 任意两个"非文本段"（功能/换行）之间一定夹着一个文本段
     * 这样标签之间、标签和换行之间都能直接打字，是"内联混排"的关键不变量。
     */
    private fun normalize() {
        if (segments.isEmpty()) {
            val first = createTextSegment()
            segments.add(first)
            activeTextSegmentId = first.id
            return
        }
        // 开头补文本
        if (segments.first().type != RawtextSegmentType.Text) {
            segments.add(0, createTextSegment())
        }
        // 结尾补文本
        if (segments.last().type != RawtextSegmentType.Text) {
            segments.add(createTextSegment())
        }
        // 相邻非文本之间补文本
        var i = 0
        while (i < segments.size - 1) {
            val cur = segments[i]
            val next = segments[i + 1]
            if (cur.type != RawtextSegmentType.Text && next.type != RawtextSegmentType.Text) {
                segments.add(i + 1, createTextSegment())
            }
            i++
        }
        if (activeTextSegmentId == null || findSegment(activeTextSegmentId!!) == null) {
            activeTextSegmentId = segments.firstOrNull { it.type == RawtextSegmentType.Text }?.id
        }
    }

    fun getRawJson(pretty: Boolean = true): String {
        val root = buildJsonObject {
            put("rawtext", buildRawtextArray())
        }
        return encodeJson(root, pretty)
    }

    fun validationMessages(): List<String> {
        val messages = mutableListOf<String>()
        for ((index, segment) in segments.withIndex()) {
            when (segment.type) {
                RawtextSegmentType.Translate -> {
                    if (segment.translateWithJson.isNotBlank() && parseJsonElementOrNull(segment.translateWithJson) == null) {
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

    fun decodeRawJson(input: String): Result<Unit> = runCatching {
        val root = parserJson.parseToJsonElement(input).jsonObject
        val rawtext = root["rawtext"]?.jsonArray ?: error("缺少 rawtext 数组")
        val decodedSegments = rawtext.flatMap { decodeRawtextItem(it) }.toMutableList()
        if (decodedSegments.isEmpty()) {
            decodedSegments.add(createTextSegment())
        }
        segments.clear()
        segments.addAll(decodedSegments)
        normalize()
        activeTextSegmentId = segments.firstOrNull { it.type == RawtextSegmentType.Text }?.id
        onContentChanged()
    }

    fun buildPreviewText(): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val style = MinecraftTextStyle()
        for (segment in segments) {
            when (segment.type) {
                RawtextSegmentType.Text -> appendFormattedMinecraftText(builder, segment.textValue.text, style)
                RawtextSegmentType.LineBreak -> builder.append("\n")
                RawtextSegmentType.Score -> appendPreviewChip(builder, mockScore, Color(0xFFFF5555))
                RawtextSegmentType.Selector -> appendPreviewChip(builder, mockSelectorName(segment.selector), Color(0xFF55FF55))
                RawtextSegmentType.Translate -> appendPreviewChip(builder, mockTranslate.ifBlank { "[${segment.translateKey}]" }, Color(0xFFFFFF55))
                RawtextSegmentType.Conditional -> appendPreviewChip(builder, mockCondition, Color(0xFFFF55FF))
            }
        }
        return builder.toAnnotatedString()
    }

    fun isPreviewEmpty(): Boolean = segments.all { segment ->
        segment.type == RawtextSegmentType.Text && segment.textValue.text.isBlank()
    } && segments.none { it.type == RawtextSegmentType.LineBreak }

    private fun buildRawtextArray(): JsonArray {
        val items = mutableListOf<JsonElement>()
        for (segment in segments) {
            when (segment.type) {
                RawtextSegmentType.Text -> appendTextItem(items, segment.textValue.text)
                RawtextSegmentType.LineBreak -> appendTextItem(items, "\n")
                RawtextSegmentType.Score -> items.add(
                    buildJsonObject {
                        putJsonObject("score") {
                            put("name", segment.scoreName)
                            put("objective", segment.scoreObjective)
                        }
                    }
                )

                RawtextSegmentType.Selector -> items.add(
                    buildJsonObject {
                        put("selector", segment.selector)
                    }
                )

                RawtextSegmentType.Translate -> items.add(
                    buildJsonObject {
                        put("translate", segment.translateKey)
                        put("with", parseJsonElementOrNull(segment.translateWithJson) ?: JsonArray(emptyList()))
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

    private fun buildConditionalJson(segment: RawtextSegment): JsonObject {
        val condition = parseJsonElementOrNull(segment.conditionJson)
            ?: buildJsonObject { put("selector", "@p") }
        val thenElement = parseJsonElementOrNull(segment.thenJson) ?: JsonArray(emptyList())
        val thenArray = when (thenElement) {
            is JsonArray -> thenElement
            else -> JsonArray(listOf(thenElement))
        }
        return buildJsonObject {
            put("translate", "%%2")
            putJsonObject("with") {
                putJsonArray("rawtext") {
                    add(condition)
                    addJsonObject {
                        put("rawtext", thenArray)
                    }
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

    private fun decodeRawtextItem(element: JsonElement): List<RawtextSegment> {
        val obj = element as? JsonObject ?: return emptyList()
        obj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
            return decodeTextWithNewlines(text)
        }

        val translate = obj["translate"]?.jsonPrimitive?.contentOrNull
        if (translate == "%%2") {
            return listOf(decodeConditionalSegment(obj))
        }

        (obj["score"] as? JsonObject)?.let { score ->
            return listOf(
                createFeatureSegment(
                    type = RawtextSegmentType.Score,
                    scoreName = score["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    scoreObjective = score["objective"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            )
        }

        obj["selector"]?.jsonPrimitive?.contentOrNull?.let { selector ->
            return listOf(createFeatureSegment(type = RawtextSegmentType.Selector, selector = selector))
        }

        if (translate != null) {
            return listOf(
                createFeatureSegment(
                    type = RawtextSegmentType.Translate,
                    translateKey = translate,
                    translateWithJson = obj["with"]?.let { encodeJson(it, pretty = false) } ?: "[]"
                )
            )
        }
        return emptyList()
    }

    /** 含换行的文本拆成 文本 / LineBreak / 文本…，让换行成为文档流里的真实换行段。 */
    private fun decodeTextWithNewlines(text: String): List<RawtextSegment> {
        if (!text.contains('\n')) {
            return listOf(createTextSegment(TextFieldValue(text, TextRange(text.length))))
        }
        val result = mutableListOf<RawtextSegment>()
        val parts = text.split("\n")
        parts.forEachIndexed { i, part ->
            if (i > 0) result.add(createLineBreakSegment())
            result.add(createTextSegment(TextFieldValue(part)))
        }
        return result
    }

    private fun decodeConditionalSegment(obj: JsonObject): RawtextSegment {
        val with = obj["with"]
        val rawtext = when (with) {
            is JsonObject -> with["rawtext"] as? JsonArray
            is JsonArray -> with
            else -> null
        }
        val condition = rawtext?.getOrNull(0) ?: buildJsonObject { put("selector", "@p") }
        val thenObject = rawtext?.getOrNull(1)
        val thenRawtext = (thenObject as? JsonObject)?.get("rawtext") ?: thenObject ?: JsonArray(emptyList())
        return createFeatureSegment(
            type = RawtextSegmentType.Conditional,
            conditionJson = encodeJson(condition, pretty = false),
            thenJson = encodeJson(thenRawtext, pretty = false)
        )
    }

    private fun createTextSegment(textValue: TextFieldValue = TextFieldValue()): RawtextSegment =
        RawtextSegment(id = nextId++, type = RawtextSegmentType.Text, textValue = textValue)

    private fun createLineBreakSegment(): RawtextSegment =
        RawtextSegment(id = nextId++, type = RawtextSegmentType.LineBreak)

    private fun createFeatureSegment(
        type: RawtextSegmentType,
        scoreName: String = "@p",
        scoreObjective: String = "score",
        selector: String = "@p",
        translateKey: String = "key.example",
        translateWithJson: String = "[{\"text\":\"example\"}]",
        conditionJson: String = "{\"selector\":\"@p\"}",
        thenJson: String = "[{\"text\":\"Success!\"}]",
    ): RawtextSegment = RawtextSegment(
        id = nextId++,
        type = type,
        scoreName = scoreName,
        scoreObjective = scoreObjective,
        selector = selector,
        translateKey = translateKey,
        translateWithJson = translateWithJson,
        conditionJson = conditionJson,
        thenJson = thenJson,
    )

    private fun insertAfter(afterId: Long?, segment: RawtextSegment) {
        val index = afterId?.let { id -> segments.indexOfFirst { it.id == id } } ?: -1
        if (index >= 0) {
            segments.add(index + 1, segment)
        } else {
            segments.add(segment)
        }
    }

    private fun getOrCreateActiveTextSegment(): RawtextSegment {
        activeTextSegmentId?.let { id ->
            val current = findSegment(id)
            if (current?.type == RawtextSegmentType.Text) return current
        }
        segments.firstOrNull { it.type == RawtextSegmentType.Text }?.let {
            activeTextSegmentId = it.id
            return it
        }
        val segment = createTextSegment()
        segments.add(segment)
        activeTextSegmentId = segment.id
        return segment
    }

    private fun parseJsonElementOrNull(value: String): JsonElement? = try {
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
        style: MinecraftTextStyle
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
                background = color.copy(alpha = 0.18f)
            ),
            start,
            builder.length
        )
    }

    private fun mockSelectorName(selector: String): String = when {
        selector.startsWith("@p") -> mockPlayerP
        selector.startsWith("@r") -> mockPlayerR
        selector.startsWith("@a") -> mockPlayerA
        selector.startsWith("@s") -> mockPlayerS
        selector.startsWith("@e") -> "实体"
        selector.startsWith("@n") -> "最近实体"
        else -> selector
    }

    // ---- 草稿 / mock 持久化序列化 ----

    @Serializable
    private data class SegmentDto(
        val type: String,
        val text: String = "",
        val scoreName: String = "@p",
        val scoreObjective: String = "score",
        val selector: String = "@p",
        val translateKey: String = "key.example",
        val translateWithJson: String = "[]",
        val conditionJson: String = "{}",
        val thenJson: String = "[]",
    )

    @Serializable
    private data class MockDto(
        val p: String,
        val r: String,
        val a: String,
        val s: String,
        val score: String,
        val translate: String,
        val condition: String,
    )

    private fun serializeDraft(): String {
        val dtos = segments.map { segment ->
            SegmentDto(
                type = segment.type.name,
                text = segment.textValue.text,
                scoreName = segment.scoreName,
                scoreObjective = segment.scoreObjective,
                selector = segment.selector,
                translateKey = segment.translateKey,
                translateWithJson = segment.translateWithJson,
                conditionJson = segment.conditionJson,
                thenJson = segment.thenJson,
            )
        }
        return draftJson.encodeToString(dtos)
    }

    private fun applyDraftJson(draft: String) {
        val dtos = draftJson.decodeFromString<List<SegmentDto>>(draft)
        if (dtos.isEmpty()) return
        val restoredSegments = dtos.map { dto ->
            val type = runCatching { RawtextSegmentType.valueOf(dto.type) }.getOrDefault(RawtextSegmentType.Text)
            when (type) {
                RawtextSegmentType.Text -> createTextSegment(TextFieldValue(dto.text, TextRange(dto.text.length)))
                RawtextSegmentType.LineBreak -> createLineBreakSegment()
                else -> createFeatureSegment(
                    type = type,
                    scoreName = dto.scoreName,
                    scoreObjective = dto.scoreObjective,
                    selector = dto.selector,
                    translateKey = dto.translateKey,
                    translateWithJson = dto.translateWithJson,
                    conditionJson = dto.conditionJson,
                    thenJson = dto.thenJson,
                )
            }
        }
        segments.clear()
        segments.addAll(restoredSegments)
        normalize()
        activeTextSegmentId = segments.firstOrNull { it.type == RawtextSegmentType.Text }?.id
    }

    private fun serializeMock(): String = draftJson.encodeToString(
        MockDto(mockPlayerP, mockPlayerR, mockPlayerA, mockPlayerS, mockScore, mockTranslate, mockCondition)
    )

    private fun applyMockJson(mock: String) {
        val dto = runCatching { draftJson.decodeFromString<MockDto>(mock) }.getOrNull() ?: return
        mockPlayerP = dto.p
        mockPlayerR = dto.r
        mockPlayerA = dto.a
        mockPlayerS = dto.s
        mockScore = dto.score
        mockTranslate = dto.translate
        mockCondition = dto.condition
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
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations)
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
        private val colorMap = mapOf(
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

        val colorFormats = listOf(
            RawtextFormat("§0", "黑色", Color(0xFF000000), false),
            RawtextFormat("§1", "深蓝", Color(0xFF0000AA), false),
            RawtextFormat("§2", "深绿", Color(0xFF00AA00), true),
            RawtextFormat("§3", "深青", Color(0xFF00AAAA), true),
            RawtextFormat("§4", "深红", Color(0xFFAA0000), false),
            RawtextFormat("§5", "深紫", Color(0xFFAA00AA), false),
            RawtextFormat("§6", "金色", Color(0xFFFFAA00), true),
            RawtextFormat("§7", "灰色", Color(0xFFAAAAAA), true),
            RawtextFormat("§8", "深灰", Color(0xFF555555), false),
            RawtextFormat("§9", "蓝色", Color(0xFF5555FF), false),
            RawtextFormat("§a", "绿色", Color(0xFF55FF55), true),
            RawtextFormat("§b", "青色", Color(0xFF55FFFF), true),
            RawtextFormat("§c", "红色", Color(0xFFFF5555), true),
            RawtextFormat("§d", "品红", Color(0xFFFF55FF), true),
            RawtextFormat("§e", "黄色", Color(0xFFFFFF55), true),
            RawtextFormat("§f", "白色", Color(0xFFFFFFFF), true),
            RawtextFormat("§g", "硬币金", Color(0xFFDDD605), true),
            RawtextFormat("§h", "石英", Color(0xFFE3D4D1), true),
            RawtextFormat("§i", "铁", Color(0xFFCECACA), true),
            RawtextFormat("§j", "下界合金", Color(0xFF443A3B), false),
            RawtextFormat("§m", "红石", Color(0xFF971607), false),
            RawtextFormat("§n", "铜", Color(0xFFB4684D), true),
            RawtextFormat("§p", "金", Color(0xFFDEB12D), true),
            RawtextFormat("§q", "绿宝石", Color(0xFF47A036), true),
            RawtextFormat("§s", "钻石", Color(0xFF2CBAA8), true),
            RawtextFormat("§t", "青金石", Color(0xFF21497B), false),
            RawtextFormat("§u", "紫水晶", Color(0xFF9A5CC6), true),
        )

        val styleFormats = listOf(
            RawtextFormat("§r", "清除", Color(0xFF607D8B), false, isFormat = true),
            RawtextFormat("§l", "粗体", Color(0xFFEA5947), false, isFormat = true, bold = true),
            RawtextFormat("§o", "斜体", Color(0xFF7E57C2), false, isFormat = true, italic = true),
            RawtextFormat("§k", "随机", Color(0xFF455A64), false, isFormat = true),
        )
    }
}
