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

package yancey.chelper.ui.library.profile

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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.data.UpdateProfileRequest
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.network.library.util.CloudLibraryCache
import yancey.chelper.network.library.util.LoginUtil

class UserProfileViewModel : ViewModel() {
    var userProfile by mutableStateOf<UserProfileData?>(null)
        private set

    var currentUserId by mutableStateOf<Int?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var publicLibraries by mutableStateOf<List<LibraryFunction>>(emptyList())
        private set

    var isLoadingPublic by mutableStateOf(false)
        private set

    var publicPageNum by mutableIntStateOf(1)
        private set

    var hasMorePublic by mutableStateOf(true)
        private set

    var privateLibraries by mutableStateOf<List<LibraryFunction>>(emptyList())
        private set

    var isLoadingPrivate by mutableStateOf(false)
        private set

    var privatePageNum by mutableIntStateOf(1)
        private set

    var hasMorePrivate by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var viewUserId by mutableStateOf(-1)
        private set

    var isUpdating by mutableStateOf(false)
        private set

    // 头像上传：跟"编辑资料"对话框内嵌的"从相册选择"按钮联动，
    // 不再让用户为了换头像跑到账户中心
    var isUploadingAvatar by mutableStateOf(false)
        private set

    var updateErrorMessage by mutableStateOf<String?>(null)
        private set

    var updateSuccessMessage by mutableStateOf<String?>(null)
        private set
    private var initializedProfileId: Int? = null

    var quotaUsed by mutableIntStateOf(0)
        private set

    var quotaLimit by mutableIntStateOf(0)
        private set

    init {
        currentUserId = LoginUtil.currentUser?.id
    }

    fun ensureProfileLoaded(id: Int) {
        if (initializedProfileId == id) return
        initializedProfileId = id
        loadProfile(id)
    }

    fun loadProfile(id: Int) {
        initializedProfileId = id
        viewUserId = id
        if (isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getUserProfile(id)
                if (res.status == 0 && res.data != null) {
                    userProfile = res.data
                } else {
                    errorMessage = res.message ?: "拉取主页失败"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "网络错误"
            } finally {
                isLoading = false
            }

            publicLibraries = emptyList()
            publicPageNum = 1
            hasMorePublic = true
            loadPublicLibraries()

            if (currentUserId == id) {
                privateLibraries = emptyList()
                privatePageNum = 1
                hasMorePrivate = true
                loadPrivateLibraries()
                loadQuota()
            }
        }
    }

    fun refresh() {
        if (viewUserId != -1) {
            loadProfile(viewUserId)
        }
    }

    fun loadPublicLibraries(loadMore: Boolean = false) {
        if (isLoadingPublic || (!hasMorePublic && loadMore)) return
        isLoadingPublic = true
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getFunctions(
                    pageNum = publicPageNum,
                    pageSize = 20,
                    keyword = null,
                    type = 0,
                    authorId = viewUserId
                )
                val responseData = res.data
                if (res.status == 0 && responseData != null) {
                    val newLibs = responseData.functions?.filterNotNull() ?: emptyList()
                    publicLibraries = if (publicPageNum == 1) {
                        newLibs
                    } else {
                        publicLibraries + newLibs
                    }
                    if (newLibs.size < 20) {
                        hasMorePublic = false
                    } else {
                        publicPageNum++
                    }
                }
            } catch (e: Exception) {
                // Ignore silent errors for pagination
            } finally {
                isLoadingPublic = false
            }
        }
    }

    fun loadPrivateLibraries(loadMore: Boolean = false) {
        if (isLoadingPrivate || (!hasMorePrivate && loadMore)) return
        isLoadingPrivate = true
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE.getMyLibraries(
                    type = 1,
                    pageNum = privatePageNum,
                    pageSize = 20
                )
                val responseData = res.data
                if (res.status == 0 && responseData != null) {
                    val newLibs = responseData.functions?.filterNotNull() ?: emptyList()
                    privateLibraries = if (privatePageNum == 1) {
                        newLibs
                    } else {
                        privateLibraries + newLibs
                    }
                    if (newLibs.size < 20) {
                        hasMorePrivate = false
                    } else {
                        privatePageNum++
                    }
                } else {
                    updateErrorMessage = res.message ?: "拉取私有云库失败"
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "网络错误"
            } finally {
                isLoadingPrivate = false
            }
        }
    }

    private fun loadQuota() {
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE.getQuota()
                val data = res.data
                if (res.status == 0 && data != null) {
                    quotaUsed = data.used ?: 0
                    quotaLimit = data.limit ?: 0
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun deleteOrUnpublishLibrary(libraryId: Int, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE.deleteLibrary(libraryId)
                if (res.status == 0) {
                    updateSuccessMessage = if (isPublic) "下架成功" else "删除成功"
                    if (isPublic) {
                        publicLibraries = publicLibraries.filter { it.id != libraryId }
                    } else {
                        privateLibraries = privateLibraries.filter { it.id != libraryId }
                    }
                } else {
                    updateErrorMessage = res.message ?: "操作失败"
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "网络错误"
            }
        }
    }

    fun updateProfile(
        nickname: String,
        avatarUrl: String?,
        homepage: String?,
        signature: String?,
        onComplete: () -> Unit
    ) {
        if (isUpdating) return
        isUpdating = true
        updateErrorMessage = null
        updateSuccessMessage = null
        viewModelScope.launch {
            try {
                val req = UpdateProfileRequest(
                    nickname = nickname.ifBlank { null },
                    avatarUrl = avatarUrl?.ifBlank { null },
                    homepage = homepage?.ifBlank { null },
                    signature = signature?.ifBlank { null }
                )
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE.updateProfile(viewUserId, req)
                if (res.status == 0) {
                    updateSuccessMessage = "资料已更新"
                    loadProfile(viewUserId) // reload
                    onComplete()
                } else {
                    updateErrorMessage = res.message ?: "更新失败"
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "网络错误"
            } finally {
                isUpdating = false
            }
        }
    }

    fun clearUpdateMessages() {
        updateErrorMessage = null
        updateSuccessMessage = null
    }

    /**
     * 上传头像。仅自己的主页可以触发，外部 Screen 已经做了 `LoginUtil.currentUser.id == viewUserId`
     * 的判断，这里不再重复鉴权。
     *
     * 成功后会重拉自己的 profile 拿新 avatarUrl，并失效云端库缓存
     * （"我的库"页面顶部卡片在用它）。
     */
    fun uploadAvatar(bytes: ByteArray, mimeType: String) {
        if (isUploadingAvatar) return
        isUploadingAvatar = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaType = mimeType.toMediaTypeOrNull()
                val requestBody = bytes.toRequestBody(mediaType)
                val ext = when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
                val part = MultipartBody.Part.createFormData("file", "avatar.$ext", requestBody)
                val result = ServiceManager.COMMAND_LAB_USER_SERVICE.uploadAvatar(part)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        Toaster.show("头像上传成功")
                        // 拉一次 profile 把 avatarUrl 同步过来——对话框预览就跟着换了
                        loadProfile(viewUserId)
                        CloudLibraryCache.invalidateAll()
                    } else {
                        Toaster.show("上传头像失败: ${result.message ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("上传网络错误: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isUploadingAvatar = false
                }
            }
        }
    }
}
