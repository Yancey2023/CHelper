package yancey.chelper.ui.library

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.service.CommandLabUserService

class CPLUploadViewModel : ViewModel() {

    val name = TextFieldState()
    val version = TextFieldState()
    val description = TextFieldState()
    val tags = TextFieldState()
    val commands = TextFieldState() // The commands content

    var isLoading by mutableStateOf(false)

    fun loadFromLocal(library: LibraryFunction) {
        viewModelScope.launch {
            try {
                name.setTextAndPlaceCursorAtEnd(library.name ?: "")
                version.setTextAndPlaceCursorAtEnd(library.version ?: "")
                description.setTextAndPlaceCursorAtEnd(library.note ?: "")
                tags.setTextAndPlaceCursorAtEnd(library.tags?.joinToString(",") ?: "")
                commands.setTextAndPlaceCursorAtEnd(library.content ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun upload(specialCode: String?, onSuccess: () -> Unit) {
        if (name.text.isBlank() || commands.text.isBlank()) {
            Toaster.show("名称和内容不能为空")
            return
        }

        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 拼接 MCD 格式，@author 由服务器自动填充用户昵称
                val mcdBuilder = StringBuilder()
                mcdBuilder.append("@name=${this@CPLUploadViewModel.name.text}\n")
                mcdBuilder.append("@version=${this@CPLUploadViewModel.version.text}\n")

                val tagList = this@CPLUploadViewModel.tags.text.toString()
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagList.isNotEmpty()) {
                    mcdBuilder.append("@tags=${tagList.joinToString(",")}\n")
                }

                mcdBuilder.append("@note=${this@CPLUploadViewModel.description.text}\n")
                mcdBuilder.append("\n")
                mcdBuilder.append("###Function###\n")
                mcdBuilder.append(this@CPLUploadViewModel.commands.text.toString())
                mcdBuilder.append("\n###End###")

                val finalContent = mcdBuilder.toString()

                // 上传固定为私有草稿
                val request = CommandLabUserService.UploadLibraryRequest().apply {
                    this.content = finalContent
                }

                val response = ServiceManager.COMMAND_LAB_USER_SERVICE.uploadLibrary(request)

                if (response.isSuccess()) {
                    withContext(Dispatchers.Main) {
                        Toaster.show("上传成功，请在我的云端库中发布到公开市场")
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toaster.show("上传失败: ${response.message}")
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
