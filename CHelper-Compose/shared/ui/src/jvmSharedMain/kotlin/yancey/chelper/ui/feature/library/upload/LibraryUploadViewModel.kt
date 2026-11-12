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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.library.document.localBody
import yancey.chelper.data.library.document.usesLocalMcdV2
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.cache.CloudLibraryCache
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.platform.ClientDiagnostics as MonitorUtil
import yancey.chelper.ui.platform.ClientToast as Toaster

class LibraryUploadViewModel : ViewModel() {

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

    private var cloudDraftJob: kotlinx.coroutines.Job? = null
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
        cloudDraftJob =
            viewModelScope.launch {
                try {
                    val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getPrivateFunction(id)
                    val library = response.data
                    if (!response.isSuccess() || library == null)
                        error(response.message ?: "加载云端草稿失败")
                    if (cloudDraftId != id) return@launch
                    editUuid = library.uuid.orEmpty()
                    loadFromLocal(library)
                    isCloudDraftReady = true
                } catch (e: kotlinx.coroutines.CancellationException) {
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
            } catch (e: Exception) {
                Log.e("LibraryUploadViewModel", "加载本地内容失败", e)
                MonitorUtil.generateCustomLog(e, "LoadLocalLibraryError")
                Toaster.show("加载内容失败: ${e.message}")
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
        if (name.text.isBlank() || commands.text.isBlank()) {
            Toaster.show("名称和内容不能为空")
            return
        }

        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalContent = buildFullMCD()
                val tagList =
                    tags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (editId > 0) {
                    val request =
                        CommandLabUserService.UpdateLibraryRequest().apply {
                            this.name = this@LibraryUploadViewModel.name.text.toString()
                            this.version =
                                this@LibraryUploadViewModel.version.text.toString().ifEmpty {
                                    "1.0.0"
                                }
                            this.note = this@LibraryUploadViewModel.description.text.toString()
                            this.tags = tagList
                            this.content = finalContent
                        }
                    val result =
                        ServiceManager.COMMAND_LAB_USER_SERVICE.updateLibrary(editId, request)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        if (result.isSuccess()) {
                            // 云端库内容变了：把"我的云端库"缓存清掉，
                            // 用户返回 LocalLibraryListScreen 时会重新拉一次，看到刚改完的数据
                            CloudLibraryCache.invalidateLibraries()
                            Toaster.show("更新成功")
                            onSuccess()
                        } else {
                            Toaster.show(result.message ?: "更新失败")
                        }
                    }
                } else {
                    // 上传固定为私有草稿
                    val request =
                        CommandLabUserService.UploadLibraryRequest().apply {
                            this.content = finalContent
                            this.isPublish = false
                        }
                    val result = ServiceManager.COMMAND_LAB_USER_SERVICE.uploadLibrary(request)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        if (result.isSuccess()) {
                            // 同上：新建一条云端库也属于列表变更
                            CloudLibraryCache.invalidateLibraries()
                            Toaster.show("上传成功")
                            onSuccess()
                        } else {
                            Toaster.show(result.message ?: "上传失败")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("LibraryUploadViewModel", "上传命令库失败", e)
                    MonitorUtil.generateCustomLog(e, "UploadLibraryError")
                    Toaster.show("网络错误: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }
}
