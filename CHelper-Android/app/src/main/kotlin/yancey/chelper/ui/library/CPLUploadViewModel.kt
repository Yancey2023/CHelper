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

package yancey.chelper.ui.library

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.util.CloudLibraryCache

class CPLUploadViewModel : ViewModel() {

    val name = TextFieldState()
    val version = TextFieldState()
    val description = TextFieldState()
    val tags = TextFieldState()
    val commands = TextFieldState()

    var isLoading by mutableStateOf(false)
    var useV2 by mutableStateOf(false)
    var editId by mutableIntStateOf(-1)
    var editUuid by mutableStateOf("")
    private var initializedCloudDraftKey: String? = null

    fun ensureCloudDraftLoaded(json: String?, id: Int) {
        if (json.isNullOrEmpty() || id <= 0) return
        val key = "$id:${json.hashCode()}"
        if (initializedCloudDraftKey == key) return
        initializedCloudDraftKey = key
        loadFromCloudJson(json, id)
    }

    fun loadFromCloudJson(json: String?, id: Int) {
        if (json.isNullOrEmpty() || id <= 0) return
        this.editId = id
        try {
            val lib = Json.decodeFromString<LibraryFunction>(json)
            this.editUuid = lib.uuid ?: ""
            loadFromLocal(lib)
            // v2 开关状态回显，若 content 包含 @mcd_version=2
            useV2 =
                lib.content?.contains("@mcd_version=2") == true || lib.content?.contains("@mcd_version= 2") == true
        } catch (e: Exception) {
            // 云端草稿格式异常时之前完全静默——用户点编辑没反应还以为没生效。
            // 这里至少 logcat 留底 + 远端上报，便于反查
            Log.e("CPLUploadViewModel", "解析云端草稿失败", e)
            MonitorUtil.generateCustomLog(e, "CloudDraftParseError")
        }
    }

    fun loadFromLocal(library: LibraryFunction) {
        viewModelScope.launch {
            try {
                name.setTextAndPlaceCursorAtEnd(library.name ?: "")
                version.setTextAndPlaceCursorAtEnd(library.version ?: "")
                description.setTextAndPlaceCursorAtEnd(library.note ?: "")
                tags.setTextAndPlaceCursorAtEnd(library.tags?.joinToString(",") ?: "")

                // 剔除已存在的元数据头，只保留命令体
                val rawContent = library.content ?: ""
                var body = ""
                try {
                    val functionStartIdx = rawContent.indexOf("###Function###")
                    if (functionStartIdx != -1) {
                        body = rawContent.substring(functionStartIdx + "###Function###".length).trimStart('\n', '\r')
                        val functionEndIdx = body.indexOf("###End###")
                        if (functionEndIdx != -1) {
                            body = body.substring(0, functionEndIdx).trimEnd('\n', '\r')
                        }
                    } else {
                        body = rawContent
                    }
                } catch (e: Exception) {
                    // 元数据头剥离失败时回退用整段原始内容，仍然继续。
                    // 不阻断流程，但要把异常上报，否则就是"看似正常但 body 没剥干净"
                    Log.w("CPLUploadViewModel", "元数据头剥离失败，回退原始内容", e)
                    MonitorUtil.generateCustomLog(e, "MCDStripHeaderError")
                    body = rawContent
                }
                
                val cleanLines = body.lines().filter { line ->
                    val t = line.trim()
                    !(t.startsWith("@name=") || t.startsWith("@version=") || 
                      t.startsWith("@tags=") || t.startsWith("@note=") || 
                      t.startsWith("@mcd_version=") || t.startsWith("@uuid="))
                }
                commands.setTextAndPlaceCursorAtEnd(cleanLines.joinToString("\n").trim())
            } catch (e: Exception) {
                Log.e("CPLUploadViewModel", "加载本地内容失败", e)
                MonitorUtil.generateCustomLog(e, "LoadLocalLibraryError")
                Toaster.show("加载内容失败: ${e.message}")
            }
        }
    }

    /**
     * 构建完整 MCD 文本（含元数据头 + 函数体），供预览和上传共用
     */
    fun buildFullMCD(): String {
        val mcdBuilder = StringBuilder()
        mcdBuilder.append("@name=${name.text}\n")
        mcdBuilder.append("@version=${version.text}\n")

        val tagList = tags.text.toString()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tagList.isNotEmpty()) {
            mcdBuilder.append("@tags=${tagList.joinToString(",")}\n")
        }

        mcdBuilder.append("@note=${description.text}\n")
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
                val tagList = tags.text.toString()
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (editId > 0) {
                    val request = CommandLabUserService.UpdateLibraryRequest().apply {
                        this.name = this@CPLUploadViewModel.name.text.toString()
                        this.version =
                            this@CPLUploadViewModel.version.text.toString().ifEmpty { "1.0.0" }
                        this.note = this@CPLUploadViewModel.description.text.toString()
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
                    val request = CommandLabUserService.UploadLibraryRequest().apply {
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
                    Log.e("CPLUploadViewModel", "上传命令库失败", e)
                    MonitorUtil.generateCustomLog(e, "UploadLibraryError")
                    Toaster.show("网络错误: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
}
