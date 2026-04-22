package yancey.chelper.ui.library

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
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.service.CommandLabUserService

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
            // 简单处理 v2 开关状态回显：若 content 包含 @mcd_version=2
            useV2 =
                lib.content?.contains("@mcd_version=2") == true || lib.content?.contains("@mcd_version= 2") == true
        } catch (e: Exception) {
            e.printStackTrace()
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
                
                // 深度清理可能在 Fallback 中被误留、或本身就存在于正文中的旧版本元数据标签
                // (注意：不能把 @a 这种 MC 原生目标选择器滤掉)
                val cleanLines = body.lines().filter { line ->
                    val t = line.trim()
                    !(t.startsWith("@name=") || t.startsWith("@version=") || 
                      t.startsWith("@tags=") || t.startsWith("@note=") || 
                      t.startsWith("@mcd_version=") || t.startsWith("@uuid="))
                }
                commands.setTextAndPlaceCursorAtEnd(cleanLines.joinToString("\n").trim())
            } catch (e: Exception) {
                e.printStackTrace()
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
                            Toaster.show("上传成功")
                            onSuccess()
                        } else {
                            Toaster.show(result.message ?: "上传失败")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("网络错误: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
}
