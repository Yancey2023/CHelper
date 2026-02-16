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
import yancey.chelper.android.common.util.LocalLibraryManager
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.service.CommandLabUserService

class CPLUploadViewModel : ViewModel() {

    val name = TextFieldState()
    val version = TextFieldState()
    val author = TextFieldState()
    val description = TextFieldState()
    val tags = TextFieldState()
    val commands = TextFieldState() // The commands content

    var isPublic by mutableStateOf(true)
    var isLoading by mutableStateOf(false)

    fun loadFromLocal(id: Int) {
        viewModelScope.launch {
            try {
                LocalLibraryManager.INSTANCE!!.ensureInit()
                val function = LocalLibraryManager.INSTANCE!!.getFunctions()[id]
                name.setTextAndPlaceCursorAtEnd(function.name ?: "")
                version.setTextAndPlaceCursorAtEnd(function.version ?: "")
                author.setTextAndPlaceCursorAtEnd(function.author ?: "")
                description.setTextAndPlaceCursorAtEnd(function.note ?: "")
                tags.setTextAndPlaceCursorAtEnd(function.tags?.joinToString(",") ?: "")
                commands.setTextAndPlaceCursorAtEnd(function.content ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun upload(specialCode: String, onSuccess: () -> Unit) {
        if (name.text.isBlank() || commands.text.isBlank()) {
            Toaster.show("名称和内容不能为空")
            return
        }
        
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Construct LibraryFunction object
                val function = LibraryFunction().apply {
                    this.name = this@CPLUploadViewModel.name.text.toString()
                    this.version = this@CPLUploadViewModel.version.text.toString()
                    this.author = this@CPLUploadViewModel.author.text.toString()
                    this.note = this@CPLUploadViewModel.description.text.toString()
                    this.tags = this@CPLUploadViewModel.tags.text.toString()
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    this.content = this@CPLUploadViewModel.commands.text.toString()
                    // Other fields like id, uuid, created_at handled by server
                }
                
                // Serialize to JSON string for 'content' field
                val contentJson = ServiceManager.GSON!!.toJson(function)
                
                val request = CommandLabUserService.UploadLibraryRequest().apply {
                    this.content = contentJson
                    this.special_code = specialCode
                    this.is_publish = isPublic
                }
                
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.uploadLibrary(request).execute()
                
                withContext(Dispatchers.Main) {
                    if (response.body()?.isSuccess() == true) {
                        Toaster.show("上传成功")
                        onSuccess()
                    } else {
                        Toaster.show("上传失败: ${response.body()?.message}")
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
