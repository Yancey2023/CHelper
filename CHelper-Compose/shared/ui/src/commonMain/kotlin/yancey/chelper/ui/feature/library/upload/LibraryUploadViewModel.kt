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

package yancey.chelper.ui.feature.library.upload

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import yancey.chelper.data.library.document.localBody
import yancey.chelper.data.library.document.usesLocalMcdV2
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.model.*
import yancey.chelper.ui.platform.ClientDiagnostics as MonitorUtil
import yancey.chelper.ui.platform.ClientMessages

class LibraryUploadViewModel(private val libraries: LibraryContentRepository) : ViewModel() {

    val name = TextFieldState()
    val version = TextFieldState()
    val description = TextFieldState()
    val tags = TextFieldState()
    val commands = TextFieldState()

    var isLoading by mutableStateOf(false)
    var useV2 by mutableStateOf(false)
    var editId by mutableIntStateOf(-1)
    var editUuid by mutableStateOf("")
    var isCloudDraftReady by mutableStateOf(false)
        private set

    var cloudDraftError by mutableStateOf<String?>(null)
        private set

    private var cloudDraftJob: Job? = null
    private var cloudDraftId = -1

    fun ensureCloudDraftLoaded(id: Int) {
        if (
            id <= 0 ||
                (cloudDraftId == id && (isCloudDraftReady || cloudDraftJob?.isActive == true))
        )
            return
        cloudDraftJob?.cancel()
        cloudDraftId = id
        editId = id
        isCloudDraftReady = false
        cloudDraftError = null
        cloudDraftJob = viewModelScope.launch {
            try {
                val response = libraries.detail(id, private = true)
                val library = response.data
                if (!response.isSuccess() || library == null) error(response.message ?: "加载云端草稿失败")
                if (cloudDraftId != id) return@launch
                editUuid = library.uuid.orEmpty()
                loadFromLocal(library)
                isCloudDraftReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cloudDraftId == id) cloudDraftError = e.message ?: "加载云端草稿失败"
            }
        }
    }

    fun loadFromLocal(library: LibraryFunction) {
        useV2 = library.usesLocalMcdV2()
        viewModelScope.launch {
            try {
                name.setTextAndPlaceCursorAtEnd(library.name ?: "")
                version.setTextAndPlaceCursorAtEnd(library.version ?: "")
                description.setTextAndPlaceCursorAtEnd(library.note ?: "")
                tags.setTextAndPlaceCursorAtEnd(library.tags?.joinToString(",") ?: "")

                commands.setTextAndPlaceCursorAtEnd(library.localBody().trim('\n', '\r'))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("LibraryUploadViewModel", "加载本地内容失败", e)
                MonitorUtil.generateCustomLog(e, "LoadLocalLibraryError")
                ClientMessages.show("加载内容失败: ${e.message}")
            }
        }
    }

    /** 构建完整 MCD 文本（含元数据头 + 函数体），供预览和上传共用 */
    fun buildFullMCD(): String {
        val mcdBuilder = StringBuilder()
        mcdBuilder.append("@name=${name.text}\n")
        mcdBuilder.append("@version=${version.text}\n")

        val tagList = tags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tagList.isNotEmpty()) {
            mcdBuilder.append("@tags=${tagList.joinToString(",")}\n")
        }

        val mcdNote =
            description.text.toString().replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        mcdBuilder.append("@note=$mcdNote\n")
        if (useV2) {
            mcdBuilder.append("@mcd_version=2\n")
        }
        if (editUuid.isNotEmpty()) {
            mcdBuilder.append("@uuid=${editUuid}\n")
        }
        mcdBuilder.append("\n")
        mcdBuilder.append("###Function###\n")
        mcdBuilder.append(commands.text.toString())
        mcdBuilder.append("\n###End###")
        return mcdBuilder.toString()
    }

    fun upload(specialCode: String?, onSuccess: () -> Unit) {
        if (isLoading) return
        if (name.text.isBlank() || commands.text.isBlank()) {
            ClientMessages.show("名称和内容不能为空")
            return
        }
        val target = editId
        val content = buildFullMCD()
        val changes =
            LibraryChanges(
                name.text.toString(),
                content,
                description.text.toString(),
                tags.text.toString().split(",").map(String::trim).filter(String::isNotEmpty),
                version.text.toString().ifEmpty { "1.0.0" },
            )
        isLoading = true
        viewModelScope.launch {
            try {
                val succeeded =
                    if (target > 0) {
                        val result = libraries.update(target, changes)
                        if (!result.isSuccess()) error(result.message ?: "更新失败")
                        true
                    } else {
                        val result = libraries.upload(content)
                        if (!result.isSuccess()) error(result.message ?: "上传失败")
                        true
                    }
                ensureActive()
                if (succeeded) {
                    ClientMessages.show(if (target > 0) "更新成功" else "上传成功")
                    onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LibraryUploadViewModel", "上传命令库失败", e)
                MonitorUtil.generateCustomLog(e, "UploadLibraryError")
                ClientMessages.show(e.message ?: "上传失败")
            } finally {
                isLoading = false
            }
        }
    }
}
