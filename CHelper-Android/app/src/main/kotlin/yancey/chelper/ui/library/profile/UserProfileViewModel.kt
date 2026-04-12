package yancey.chelper.ui.library.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yancey.chelper.network.library.util.LoginUtil
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.UpdateProfileRequest
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.network.library.data.LibraryFunction

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

    var publicPageNum by mutableStateOf(1)
        private set

    var hasMorePublic by mutableStateOf(true)
        private set

    var privateLibraries by mutableStateOf<List<LibraryFunction>>(emptyList())
        private set

    var isLoadingPrivate by mutableStateOf(false)
        private set

    var privatePageNum by mutableStateOf(1)
        private set

    var hasMorePrivate by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var viewUserId by mutableStateOf<Int>(-1)
        private set

    var isUpdating by mutableStateOf(false)
        private set

    var updateErrorMessage by mutableStateOf<String?>(null)
        private set
        
    var updateSuccessMessage by mutableStateOf<String?>(null)
        private set

    init {
        currentUserId = LoginUtil.currentUser?.id
    }

    fun loadProfile(id: Int) {
        viewUserId = id
        if (isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE?.getUserProfile(id)
                if (res?.status == 0 && res.data != null) {
                    userProfile = res.data
                } else {
                    errorMessage = res?.message ?: "拉取主页失败"
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
                val res = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE?.getFunctions(
                    pageNum = publicPageNum,
                    pageSize = 20,
                    keyword = null,
                    type = 0,
                    authorId = viewUserId
                )
                val responseData = res?.data
                if (res?.status == 0 && responseData != null) {
                    val newLibs = responseData.functions?.filterNotNull() ?: emptyList()
                    if (publicPageNum == 1) {
                        publicLibraries = newLibs
                    } else {
                        publicLibraries = publicLibraries + newLibs
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
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE?.getMyLibraries(type = 1, pageNum = privatePageNum, pageSize = 20)
                val responseData = res?.data
                if (res?.status == 0 && responseData != null) {
                    val newLibs = responseData.functions?.filterNotNull() ?: emptyList()
                    if (privatePageNum == 1) {
                        privateLibraries = newLibs
                    } else {
                        privateLibraries = privateLibraries + newLibs
                    }
                    if (newLibs.size < 20) {
                        hasMorePrivate = false
                    } else {
                        privatePageNum++
                    }
                } else {
                    updateErrorMessage = res?.message ?: "拉取私有云库失败"
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "网络错误"
            } finally {
                isLoadingPrivate = false
            }
        }
    }

    fun deleteOrUnpublishLibrary(libraryId: Int, isPublic: Boolean) {
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE?.deleteLibrary(libraryId)
                if (res?.status == 0) {
                    updateSuccessMessage = if(isPublic) "下架成功" else "删除成功"
                    if (isPublic) {
                        publicLibraries = publicLibraries.filter { it.id != libraryId }
                    } else {
                        privateLibraries = privateLibraries.filter { it.id != libraryId }
                    }
                } else {
                    updateErrorMessage = res?.message ?: "操作失败"
                }
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "网络错误"
            }
        }
    }

    fun updateProfile(nickname: String, avatarUrl: String?, homepage: String?, signature: String?, onComplete: () -> Unit) {
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
                val res = ServiceManager.COMMAND_LAB_USER_SERVICE?.updateProfile(viewUserId, req)
                if (res?.status == 0) {
                    updateSuccessMessage = "资料已更新"
                    loadProfile(viewUserId) // reload
                    onComplete()
                } else {
                    updateErrorMessage = res?.message ?: "更新失败"
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
}
