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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import yancey.chelper.domain.rawtext.RawtextDocumentSnapshot
import yancey.chelper.domain.rawtext.RawtextEditToken
import yancey.chelper.domain.rawtext.RawtextField
import yancey.chelper.domain.rawtext.RawtextMockSnapshot
import yancey.chelper.domain.rawtext.RawtextRepository
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot
import yancey.chelper.domain.rawtext.RawtextSegmentType

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
        get() =
            when (type) {
                RawtextSegmentType.Text -> "文本"
                RawtextSegmentType.LineBreak -> "换行"
                RawtextSegmentType.Score -> "计分板"
                RawtextSegmentType.Selector -> "选择器"
                RawtextSegmentType.Translate -> "翻译"
                RawtextSegmentType.Conditional -> "条件"
            }

    fun summary(): String =
        when (type) {
            RawtextSegmentType.Text -> textValue.text.ifBlank { "空文本段" }
            RawtextSegmentType.LineBreak -> "换行"
            RawtextSegmentType.Score ->
                "${scoreName.ifBlank { "@p" }} : ${scoreObjective.ifBlank { "score" }}"
            RawtextSegmentType.Selector -> selector.ifBlank { "@p" }
            RawtextSegmentType.Translate -> translateKey.ifBlank { "key.example" }
            RawtextSegmentType.Conditional ->
                "IF ${conditionJson.ifBlank { "{}" }} THEN ${thenJson.ifBlank { "[]" }}"
        }
}

class RawtextViewModel
constructor(
    private val dataStore: RawtextRepository,
    private val requestScope: CoroutineScope? = null,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val saveDelayMillis: Long = 300,
) : ViewModel() {
    private val scope
        get() = requestScope ?: viewModelScope

    private val persistenceScope =
        CoroutineScope(
            SupervisorJob() +
                (requestScope?.coroutineContext?.minusKey(Job) ?: Dispatchers.Main.immediate)
        )
    private val _output = MutableStateFlow(RawtextOutput())
    val output = _output.asStateFlow()
    var draftError by mutableStateOf<String?>(null)
        private set

    private var pendingEdits = RawtextEditToken()
    private var editRevision = 0L
    private var copyFormatEdited = false
    private var autoSaveEdited = false
    private var autoSavePrompted = false
    private var saver: RawtextAutosaver? = null
    private val parserJson = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compactJson = Json.Default
    private val draftJson = Json { ignoreUnknownKeys = true }

    private var nextId by mutableLongStateOf(1L)

    val segments = mutableStateListOf<RawtextSegment>()

    var activeTextSegmentId by mutableStateOf<Long?>(null)
    var copyFormat by mutableStateOf("formatted")
    var autoSaveEnabled by mutableStateOf(false)
    var showAutoSavePrompt by mutableStateOf(false)
    private var mock by mutableStateOf(RawtextMockSnapshot())
    var mockPlayerP: String
        get() = mock.p
        set(value) {
            updateMock(mock.copy(p = value))
        }

    var mockPlayerR: String
        get() = mock.r
        set(value) {
            updateMock(mock.copy(r = value))
        }

    var mockPlayerA: String
        get() = mock.a
        set(value) {
            updateMock(mock.copy(a = value))
        }

    var mockPlayerS: String
        get() = mock.s
        set(value) {
            updateMock(mock.copy(s = value))
        }

    var mockScore: String
        get() = mock.score
        set(value) {
            updateMock(mock.copy(score = value))
        }

    var mockTranslate: String
        get() = mock.translate
        set(value) {
            updateMock(mock.copy(translate = value))
        }

    var mockCondition: String
        get() = mock.condition
        set(value) {
            updateMock(mock.copy(condition = value))
        }

    private var restored = false
    private var restoration: Job? = null
    private var restoreCompleted = false
    private lateinit var restoreBefore: RawtextDocumentSnapshot
    private var restoreRevision = 0L
    private var cleared = false

    init {
        val first = createTextSegment()
        segments.add(first)
        activeTextSegmentId = first.id
        saver =
            RawtextAutosaver(persistenceScope, dataStore, worker, saveDelayMillis) {
                draftError = it
            }
        restorePreferences()
        scope.launch {
            snapshotFlow { documentSnapshot() }
                .collectLatest { snapshot ->
                    val result = withContext(worker) { RawtextRenderer(snapshot).render() }
                    if (documentSnapshot() == snapshot) _output.value = result
                }
        }
    }

    /** 启动时读偏好：复制格式、自动保存开关、草稿、mock 值。 首次进入（autoSavePrompted == false）会触发一次"开启动态保存？"询问。 */
    private fun restorePreferences() {
        restoreBefore = documentSnapshot()
        restoreRevision = editRevision
        restoration = persistenceScope.launch {
            try {
                restorePreferencesNow()
                if (pendingEdits.revisions.isNotEmpty()) requestSave()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                draftError = "草稿读取失败，暂时无法恢复上次内容"
            } finally {
                restoreCompleted = true
                if (cleared) finishPersistence()
            }
        }
    }

    private suspend fun restorePreferencesNow() {
        val before = restoreBefore
        val revision = restoreRevision
        val prefs = dataStore.preferences().first()
        val decoded =
            withContext(worker) {
                val draft =
                    if (prefs.autoSaveEnabled && !prefs.draft.isNullOrBlank())
                        runCatching {
                            draftJson.decodeFromString<List<RawtextSegmentSnapshot>>(
                                requireNotNull(prefs.draft)
                            )
                        }
                            .getOrNull()
                    else null
                val mock =
                    prefs.mockSettings?.let {
                        runCatching { draftJson.decodeFromString<RawtextMockSnapshot>(it) }
                            .getOrNull()
                    }
                draft to mock
            }
        if (!copyFormatEdited) copyFormat = prefs.copyFormat ?: "formatted"
        if (!autoSaveEdited) {
            autoSaveEnabled = prefs.autoSaveEnabled
            autoSavePrompted = prefs.autoSavePrompted
            showAutoSavePrompt = !prefs.autoSavePrompted
        }
        val mockEdited = RawtextField.MOCK in pendingEdits.revisions
        if (!mockEdited) decoded.second?.let { applyMock(it) }
        if (
            autoSaveEnabled &&
                editRevision == revision &&
                documentSnapshot().segments == before.segments
        )
            decoded.first?.let { applyDraft(it) }
        restored = true
        draftError = null
    }

    fun updateCopyFormat(format: String) {
        copyFormatEdited = true
        pendingEdits += dataStore.edits.claim(RawtextField.COPY_FORMAT)
        copyFormat = format
        requestSave(immediately = true)
    }

    fun confirmAutoSave(enabled: Boolean) = setAutoSave(enabled)

    fun setAutoSave(enabled: Boolean) {
        autoSaveEdited = true
        pendingEdits += dataStore.edits.claim(RawtextField.AUTO_SAVE, RawtextField.DRAFT)
        autoSavePrompted = true
        autoSaveEnabled = enabled
        showAutoSavePrompt = false
        requestSave(immediately = true)
    }

    fun persistMockSettings() {
        requestSave()
    }

    private fun onContentChanged() {
        editRevision++
        if (autoSaveEnabled || !restored) pendingEdits += dataStore.edits.claim(RawtextField.DRAFT)
        if (autoSaveEnabled) requestSave()
    }

    private fun requestSave(immediately: Boolean = false) {
        if (restored) saver?.submit(persistenceSnapshot(), pendingEdits, immediately)
    }

    fun flushDraft() {
        if (cleared) return
        requestSave(immediately = true)
    }

    internal suspend fun awaitDraftSaved() {
        saver?.awaitIdle()
    }

    suspend fun flushAndAwait() {
        restoration?.join()
        if (!restored) restorePreferencesNow()
        saver?.flush(persistenceSnapshot(), pendingEdits)
        check(draftError == null) { draftError.orEmpty() }
    }

    override fun onCleared() {
        cleared = true
        // Complete an in-flight restore before flushing, so a quick close cannot lose new input.
        if (restoreCompleted) finishPersistence()
        super.onCleared()
    }

    private fun finishPersistence() {
        requestSave(immediately = true)
        saver?.finish() ?: persistenceScope.cancel()
    }

    internal fun documentSnapshot() =
        RawtextDocumentSnapshot(
            segments.map { segment ->
                RawtextSegmentSnapshot(
                    segment.type.name,
                    segment.textValue.text,
                    segment.scoreName,
                    segment.scoreObjective,
                    segment.selector,
                    segment.translateKey,
                    segment.translateWithJson,
                    segment.conditionJson,
                    segment.thenJson,
                )
            },
            mockSnapshot(),
        )

    private fun mockSnapshot() = mock

    private fun updateMock(value: RawtextMockSnapshot) {
        if (mock == value) return
        mock = value
        pendingEdits += dataStore.edits.claim(RawtextField.MOCK)
        requestSave()
    }

    private fun persistenceSnapshot() =
        RawtextPersistenceSnapshot(
            copyFormat,
            autoSavePrompted,
            autoSaveEnabled,
            documentSnapshot(),
        )

    private fun applyMock(value: RawtextMockSnapshot) {
        mock = value
    }

    suspend fun copyJson(pretty: Boolean): String {
        val snapshot = documentSnapshot()
        return withContext(worker) { RawtextRenderer(snapshot).getRawJson(pretty) }
    }

    /** 文本段内容变化。若文本里出现换行符，就地拆成 文本 / LineBreak / 文本… 让换行变成文档流里的真实换行段（标签也能跟着换行）。 */
    fun updateTextSegment(id: Long, value: TextFieldValue) {
        val segment = findSegment(id) ?: return
        activeTextSegmentId = id
        if (!value.text.contains('\n')) {
            val textChanged = segment.textValue.text != value.text
            segment.textValue = value
            if (textChanged) onContentChanged()
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
        val focus =
            textSegmentsInserted.getOrNull(newlinesBeforeCaret) ?: textSegmentsInserted.lastOrNull()
        focus?.let {
            val offset = parts.getOrNull(newlinesBeforeCaret)?.length ?: 0
            it.textValue =
                it.textValue.copy(
                    selection = TextRange(offset.coerceAtMost(it.textValue.text.length))
                )
            activeTextSegmentId = it.id
        }
        normalize()
        onContentChanged()
    }

    /** 文本段最前面按退格：吞掉前一个段（换行或功能标签），实现"退格删标签/合并行"。 返回 true 表示已处理（拦截这次退格）。 */
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

        val feature =
            if (type == RawtextSegmentType.LineBreak) createLineBreakSegment()
            else createFeatureSegment(type)
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
        activeTextSegmentId =
            segments.getOrNull(index)?.takeIf { it.type == RawtextSegmentType.Text }?.id
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
        val replacement =
            when {
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
     * 1. 开头和结尾都是文本段（光标有处可落）
     * 2. 任意两个"非文本段"（功能/换行）之间一定夹着一个文本段 这样标签之间、标签和换行之间都能直接打字，是"内联混排"的关键不变量。
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
                    scoreObjective = score["objective"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            )
        }

        obj["selector"]?.jsonPrimitive?.contentOrNull?.let { selector ->
            return listOf(
                createFeatureSegment(type = RawtextSegmentType.Selector, selector = selector)
            )
        }

        if (translate != null) {
            return listOf(
                createFeatureSegment(
                    type = RawtextSegmentType.Translate,
                    translateKey = translate,
                    translateWithJson = obj["with"]?.let { encodeJson(it, pretty = false) } ?: "[]",
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
        val rawtext =
            when (with) {
                is JsonObject -> with["rawtext"] as? JsonArray
                is JsonArray -> with
                else -> null
            }
        val condition = rawtext?.getOrNull(0) ?: buildJsonObject { put("selector", "@p") }
        val thenObject = rawtext?.getOrNull(1)
        val thenRawtext =
            (thenObject as? JsonObject)?.get("rawtext") ?: thenObject ?: JsonArray(emptyList())
        return createFeatureSegment(
            type = RawtextSegmentType.Conditional,
            conditionJson = encodeJson(condition, pretty = false),
            thenJson = encodeJson(thenRawtext, pretty = false),
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
    ): RawtextSegment =
        RawtextSegment(
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
        segments
            .firstOrNull { it.type == RawtextSegmentType.Text }
            ?.let {
                activeTextSegmentId = it.id
                return it
            }
        val segment = createTextSegment()
        segments.add(segment)
        activeTextSegmentId = segment.id
        return segment
    }

    private fun parseJsonElementOrNull(value: String): JsonElement? =
        try {
            parserJson.parseToJsonElement(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private fun encodeJson(element: JsonElement, pretty: Boolean): String =
        if (pretty) {
            prettyJson.encodeToString(element)
        } else {
            compactJson.encodeToString(element)
        }

    private fun applyDraft(dtos: List<RawtextSegmentSnapshot>) {
        if (dtos.isEmpty()) return
        val restoredSegments = dtos.map { dto ->
            val type = runCatching {
                RawtextSegmentType.valueOf(dto.type)
            }.getOrDefault(RawtextSegmentType.Text)
            when (type) {
                RawtextSegmentType.Text ->
                    createTextSegment(TextFieldValue(dto.text, TextRange(dto.text.length)))

                RawtextSegmentType.LineBreak -> createLineBreakSegment()
                else ->
                    createFeatureSegment(
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

    companion object {
        val colorFormats =
            listOf(
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

        val styleFormats =
            listOf(
                RawtextFormat("§r", "清除", Color(0xFF607D8B), false, isFormat = true),
                RawtextFormat("§l", "粗体", Color(0xFFEA5947), false, isFormat = true, bold = true),
                RawtextFormat("§o", "斜体", Color(0xFF7E57C2), false, isFormat = true, italic = true),
                RawtextFormat("§k", "随机", Color(0xFF455A64), false, isFormat = true),
            )
    }
}
