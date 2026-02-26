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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.LibraryFunction

class PublicLibraryShowViewModel : ViewModel() {
    var library by mutableStateOf(LibraryFunction())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var actionMessage by mutableStateOf<String?>(null)
    var isPrivate by mutableStateOf(false)

    fun loadFunction(id: Int, isPrivate: Boolean) {
        this.isPrivate = isPrivate
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val response: BaseResult<LibraryFunction?>? = withContext(Dispatchers.IO) {
                    if (isPrivate) {
                        ServiceManager.COMMAND_LAB_USER_SERVICE?.getPrivateFunction(id)
                    } else {
                        ServiceManager.COMMAND_LAB_PUBLIC_SERVICE?.getFunction(id)
                    }
                }

                if (response?.isSuccess() == true && response.data != null) {
                    library = response.data!!
                } else {
                    errorMessage = response?.message ?: "加载失败"
                }
            } catch (e: Exception) {
                errorMessage = "网络错误: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun releaseToPublic(id: Int, specialCode: String = "") {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE?.releaseToPublic(
                        id,
                        mapOf("special_code" to specialCode)
                    )
                }
                actionMessage = if (result?.isSuccess() == true) {
                    "已提交发布申请"
                } else {
                    result?.message ?: "操作失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    fun syncToPublic(id: Int) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE?.syncToPublic(id)
                }
                actionMessage = if (result?.isSuccess() == true) {
                    "已提交同步申请"
                } else {
                    result?.message ?: "同步失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    fun deleteLibrary(id: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE?.deleteLibrary(id)
                }
                actionMessage = if (result?.isSuccess() == true) {
                    onSuccess()
                    "删除成功"
                } else {
                    result?.message ?: "删除失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }
}
