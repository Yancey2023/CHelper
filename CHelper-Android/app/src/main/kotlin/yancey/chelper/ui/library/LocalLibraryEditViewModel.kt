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

package yancey.chelper.ui.library

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import yancey.chelper.network.library.data.LibraryFunction

enum class EditMode {
    ADD,
    UPDATE
}

class LocalLibraryEditViewModel : ViewModel() {
    var id by mutableStateOf<Int?>(null)
    val mode get() = if (id == null) EditMode.ADD else EditMode.UPDATE
    var name by mutableStateOf(TextFieldState())
    var version by mutableStateOf(TextFieldState())
    var description by mutableStateOf(TextFieldState())
    var tags by mutableStateOf(TextFieldState())
    var commands by mutableStateOf(TextFieldState())
    /**
     * 保存时是否顺手把本条本地库同步到云端。
     * 以前叫"自动同步"——但当时要求"本地库必须先有 uuid"，
     * 用户根本不知道 uuid 哪里来，相当于这个开关只对已经手动上传过的库生效。
     *
     * 现在改成"自动生成 UUID 并同步"：
     * - 本地库还没绑定云端 id：保存时调 upload，让后端分配 uuid，
     *   响应里的 uuid + id 回写到本地，下次起就是普通的"已绑定"状态。
     * - 已经绑过云端：保存时调 update。
     * 用户不再需要关心 uuid 从哪来。
     */
    var autoSync by mutableStateOf(false)
    /**
     * 是否使用 MCD V2 语法。
     * - 新建本地库（ADD）默认开启：V2 才支持命令链 / 方块状态等完整可视化，新内容没必要再绑老语法。
     * - 编辑已有库（UPDATE）根据 content 里是否带 `@mcd_version=2` 头自动推断，
     *   避免误把存量 V1 库标记成 V2 反而渲染异常。
     * - 这个开关只影响 syncToCloud 构建 MCD 头部时是否带 `@mcd_version=2`；
     *   本地存储仍保留用户输入的原始 content，不擅自塞头进去，免得来回切换时丢字段。
     */
    var useV2 by mutableStateOf(true)
    var isShowDeleteDialog by mutableStateOf(false)
    var isShowLowCodeHelper by mutableStateOf(false)
    // V2 → V1 降级二次确认：切回去会丢命令链/方块状态可视化，得让用户先确认
    var isShowV2DowngradeConfirm by mutableStateOf(false)
    var isSyncing by mutableStateOf(false)
    private var initializedEditId: Int? = null
    private var initializedAddMode = false

    fun ensureEditingTarget(targetId: Int?, library: LibraryFunction?) {
        id = targetId
        if (targetId == null) {
            if (!initializedAddMode) {
                if (initializedEditId != null) {
                    name.setTextAndPlaceCursorAtEnd("")
                    version.setTextAndPlaceCursorAtEnd("")
                    description.setTextAndPlaceCursorAtEnd("")
                    tags.setTextAndPlaceCursorAtEnd("")
                    commands.setTextAndPlaceCursorAtEnd("")
                    autoSync = false
                }
                // 新建模式：V2 是默认偏好
                useV2 = true
                initializedEditId = null
                initializedAddMode = true
            }
            return
        }
        initializedAddMode = false
        if (initializedEditId == targetId || library == null) return

        name.setTextAndPlaceCursorAtEnd(library.name ?: "")
        version.setTextAndPlaceCursorAtEnd(library.version ?: "")
        description.setTextAndPlaceCursorAtEnd(library.note ?: "")
        tags.setTextAndPlaceCursorAtEnd(library.tags?.joinToString(separator = ",") ?: "")
        commands.setTextAndPlaceCursorAtEnd(library.content ?: "")
        autoSync = library.autoSync ?: false
        // 编辑存量库：从原始 content 推断 V2 标记。容忍 `@mcd_version= 2` 这种带空格的写法
        val content = library.content ?: ""
        useV2 = content.contains("@mcd_version=2") || content.contains("@mcd_version= 2")
        initializedEditId = targetId
    }

    /**
     * 把用户输入的 content（不带 MCD 头）拼成完整 MCD 文本：
     * 头部（@name/@version/@tags/@note/@mcd_version/@uuid）+ Function 段。
     * 给"自动同步"接口拼上传载荷用。
     *
     * 提供 fallbackUuid 是为了"自动生成 UUID 并同步"场景——本地库还没 uuid 时，
     * 先在 ViewModel 外层 `UUID.randomUUID()` 生成一个塞进来，确保头部一定有 uuid，
     * 避免后端按"新建"再分配一个，导致下次再保存找不到对应记录。
     */
    fun buildFullMCD(
        existingLibrary: LibraryFunction?,
        fallbackUuid: String?
    ): String {
        val tagList = tags.text.toString()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
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
