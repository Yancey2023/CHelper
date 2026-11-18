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

package yancey.chelper.ui.feature.library.local

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.time.Clock
import yancey.chelper.data.library.document.localBody
import yancey.chelper.data.library.document.localLibraryEditDraftKey
import yancey.chelper.data.library.document.usesLocalMcdV2
import yancey.chelper.data.local.library.LocalLibraryEditDraft
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.validateLibraryEntry

enum class EditMode {
    ADD,
    UPDATE,
}

data class LocalLibraryEditorSnapshot(
    val name: String,
    val version: String,
    val description: String,
    val tags: String,
    val commands: String,
    val autoSync: Boolean,
    val useV2: Boolean,
)

data class LocalLibraryTemplate(
    val label: String,
    val content: String,
    val requiresV2: Boolean = false,
)

class LocalLibraryEditViewModel : ViewModel() {
    var id by mutableStateOf<Int?>(null)
    var localEntryId by mutableStateOf<String?>(null)
    val mode
        get() = if (localEntryId == null && id == null) EditMode.ADD else EditMode.UPDATE

    var name by mutableStateOf(TextFieldState())
    var version by mutableStateOf(TextFieldState())
    var description by mutableStateOf(TextFieldState())
    var tags by mutableStateOf(TextFieldState())
    var commands by mutableStateOf(TextFieldState())

    /** 保存时是否顺手把本条本地库同步到云端 */
    var autoSync by mutableStateOf(false)

    /** 是否使用 MCD V2 语法。 */
    var useV2 by mutableStateOf(true)
    var isShowDeleteDialog by mutableStateOf(false)
    var isShowLowCodeHelper by mutableStateOf(false)

    // 二次确认
    var isShowV2DowngradeConfirm by mutableStateOf(false)
    var isSyncing by mutableStateOf(false)
    var isShowExitConfirm by mutableStateOf(false)
    var isShowTemplateDialog by mutableStateOf(false)
    var isInitialized by mutableStateOf(false)
        private set

    var draftRestored by mutableStateOf(false)
        private set

    var draftWritesEnabled by mutableStateOf(true)
        private set

    var exitApproved by mutableStateOf(false)
        private set

    private var initialSnapshot: LocalLibraryEditorSnapshot? = null
    private var initializedEditKey: String? = null
    private var initializedAddMode = false

    fun ensureEditingTarget(localEntryId: String?, targetId: Int?, library: LibraryFunction?) {
        this.localEntryId = localEntryId ?: library?.localEntryId
        id = targetId
        if (localEntryId == null && targetId == null) {
            if (!initializedAddMode) {
                if (initializedEditKey != null) {
                    name.setTextAndPlaceCursorAtEnd("")
                    version.setTextAndPlaceCursorAtEnd("")
                    description.setTextAndPlaceCursorAtEnd("")
                    tags.setTextAndPlaceCursorAtEnd("")
                    commands.setTextAndPlaceCursorAtEnd("")
                    autoSync = false
                }
                // 默认偏好V2
                useV2 = true
                initializedEditKey = null
                initializedAddMode = true
                initialSnapshot = snapshot()
                isInitialized = true
            }
            return
        }
        initializedAddMode = false
        val targetKey = localEntryId ?: "index:$targetId"
        if (initializedEditKey == targetKey || library == null) return

        name.setTextAndPlaceCursorAtEnd(library.name ?: "")
        version.setTextAndPlaceCursorAtEnd(library.version ?: "")
        description.setTextAndPlaceCursorAtEnd(library.note ?: "")
        tags.setTextAndPlaceCursorAtEnd(library.tags?.joinToString(separator = ",") ?: "")

        commands.setTextAndPlaceCursorAtEnd(library.localBody().trim('\n', '\r'))
        autoSync = library.autoSync ?: false
        // 从原始 content 推断 V2 标记。容忍带空格写法
        useV2 = library.usesLocalMcdV2()
        initializedEditKey = targetKey
        initialSnapshot = snapshot()
        isInitialized = true
    }

    val draftKey: String
        get() = localLibraryEditDraftKey(localEntryId, id)

    val isDirty: Boolean
        get() = initialSnapshot?.let { snapshot() != it } == true

    fun snapshot(): LocalLibraryEditorSnapshot =
        LocalLibraryEditorSnapshot(
            name = name.text.toString(),
            version = version.text.toString(),
            description = description.text.toString(),
            tags = tags.text.toString(),
            commands = commands.text.toString().replace("\r\n", "\n"),
            autoSync = autoSync,
            useV2 = useV2,
        )

    fun restoreDraft(draft: LocalLibraryEditDraft?) {
        if (draftRestored) return
        draft?.let {
            name.setTextAndPlaceCursorAtEnd(it.name)
            version.setTextAndPlaceCursorAtEnd(it.version)
            description.setTextAndPlaceCursorAtEnd(it.description)
            tags.setTextAndPlaceCursorAtEnd(it.tags)
            commands.setTextAndPlaceCursorAtEnd(it.commands)
            autoSync = it.autoSync
            useV2 = it.useV2
        }
        draftRestored = true
    }

    fun toDraft(snapshot: LocalLibraryEditorSnapshot = snapshot()): LocalLibraryEditDraft =
        LocalLibraryEditDraft(
            name = snapshot.name,
            version = snapshot.version,
            description = snapshot.description,
            tags = snapshot.tags,
            commands = snapshot.commands,
            autoSync = snapshot.autoSync,
            useV2 = snapshot.useV2,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )

    fun buildLocalLibrary(
        existingLibrary: LibraryFunction?,
        requestedLocalEntryId: String?,
    ): LibraryFunction {
        val current = snapshot()
        val tagList = current.tags.split(",").map(String::trim).filter(String::isNotEmpty)
        return (existingLibrary ?: LibraryFunction()).copy(
            localEntryId = requestedLocalEntryId ?: existingLibrary?.localEntryId,
            localIsV2 = current.useV2,
            name = current.name,
            version = current.version,
            note = current.description,
            tags = tagList,
            content = current.commands,
            autoSync = current.autoSync,
            localUnsynced = !existingLibrary?.uuid.isNullOrEmpty(),
        )
    }

    fun markSaved() {
        draftWritesEnabled = false
        initialSnapshot = snapshot()
    }

    fun approveExit() {
        exitApproved = true
    }

    fun applyTemplate(template: LocalLibraryTemplate) {
        val current = commands.text.toString()
        val next = if (current.isBlank()) template.content else "$current\n\n${template.content}"
        commands.setTextAndPlaceCursorAtEnd(next)
    }

    fun availableTemplates(): List<LocalLibraryTemplate> = buildList {
        add(LocalLibraryTemplate("单条命令", "say hello"))
        add(LocalLibraryTemplate("注释 + 命令", "# 说明这条命令的用途\nsay hello"))
        if (useV2) {
            add(LocalLibraryTemplate("脉冲命令方块", "> I\nsay hello", requiresV2 = true))
            add(LocalLibraryTemplate("连锁命令链", "> I\nsay start\n> C!\nsay next", requiresV2 = true))
            add(LocalLibraryTemplate("循环执行", "> R!\nsay loop", requiresV2 = true))
            add(LocalLibraryTemplate("聊天文本", "> H\n这是一段聊天文本", requiresV2 = true))
        }
    }

    fun validationError(): String? =
        validateLibraryEntry(
            LibraryEntry(
                name = name.text.toString(),
                version = version.text.toString(),
                note = description.text.toString(),
                tags = tags.text.toString().split(','),
                body = commands.text.toString(),
                isV2 = useV2,
            )
        )

    /** 把用户输入的 content（不带 MCD 头）拼成完整 MCD 文本 */
    fun buildFullMCD(existingLibrary: LibraryFunction?, fallbackUuid: String?): String {
        val tagList = tags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val effectiveUuid = existingLibrary?.uuid?.takeIf { it.isNotEmpty() } ?: fallbackUuid
        val sb = StringBuilder()
        sb.append("@name=${name.text}\n")
        sb.append("@version=${version.text.toString().ifEmpty { "1.0.0" }}\n")
        if (tagList.isNotEmpty()) {
            sb.append("@tags=${tagList.joinToString(",")}\n")
        }
        sb.append("@note=${description.text}\n")
        if (useV2) {
            sb.append("@mcd_version=2\n")
        }
        if (!effectiveUuid.isNullOrEmpty()) {
            sb.append("@uuid=$effectiveUuid\n")
        }
        sb.append("\n")
        sb.append("###Function###\n")
        sb.append(commands.text.toString())
        sb.append("\n###End###")
        return sb.toString()
    }
}
