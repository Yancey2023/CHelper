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

package yancey.chelper.ui.feature.library.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.data.remote.UploadFile
import yancey.chelper.data.remote.library.model.UpdateProfileRequest
import yancey.chelper.data.remote.library.model.UserProfileData
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.platform.ClientMessages

class AccountViewModel(private val network: NetworkServices) : ViewModel() {

    enum class UserTab {
        LOGIN,
        REGISTER,
    }

    var currentTab by mutableStateOf(UserTab.LOGIN)
    var isLoading by mutableStateOf(false)
    var isCheckingCaptcha by mutableStateOf(false)

    // Register temporary data
    // private var tempSpecialCode: String? = null

    // Login Fields
    val loginAccount = TextFieldState()
    val loginPassword = TextFieldState()

    // Register Fields
    val registerAccount = TextFieldState()
    val registerCode = TextFieldState()
    val registerPassword = TextFieldState()
    val registerNickname = TextFieldState()

    // User State
    var currentUser by mutableStateOf<CommandLabUserService.User?>(null)
    var isGuest by mutableStateOf(false)
    var isUploadingAvatar by mutableStateOf(false)

    // 个人公开资料登录回包没有 signature / homepage / userTitle 这些字段
    // 编辑面板需要拿这条记录做 form 初值
    var userProfile by mutableStateOf<UserProfileData?>(null)
    var isUpdatingProfile by mutableStateOf(false)

    // My Cloud Libraries
    val myLibraries = mutableStateListOf<LibraryFunction>()
    var myLibrariesPage by mutableIntStateOf(1)
    var myLibrariesHasMore by mutableStateOf(true)

    init {
        refreshUserState()
    }

    fun refreshUserState() {
        if (network.login.isLoggedIn) {
            currentUser = network.login.currentUser
            isGuest = false
            loadMyLibraries(true)
            loadUserProfile()
        } else if (network.guest.isLoggedIn()) {
            currentUser = network.guest.guestUser
            isGuest = true
            userProfile = null
        } else {
            currentUser = null
            isGuest = false
            userProfile = null
        }
    }

    /** 把自己完整的公开资料拉一份回来——给编辑资料对话框做初值 失败静默，不挡用户继续浏览页面 */
    fun loadUserProfile() {
        val uid = currentUser?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = network.public.getUserProfile(uid)
                if (resp.isSuccess()) {
                    withContext(Dispatchers.Main) { userProfile = resp.data }
                }
            } catch (_: Exception) {
                // 拉不到就算了，对话框会显示"加载中"，用户可以重试
            }
        }
    }

    /**
     * 走 PUT users/{id} uid 固定取 currentUser.id。
     *
     * 成功后： 重拉 userProfile，刷新本地副本 把昵称同步回 currentUser 失效云端库缓存——LocalLibraryListScreen 顶上的用户卡片是这份缓存
     */
    fun updateProfile(
        nickname: String,
        avatarUrl: String?,
        homepage: String?,
        signature: String?,
        onComplete: () -> Unit,
    ) {
        if (isUpdatingProfile) return
        val uid =
            currentUser?.id
                ?: run {
                    ClientMessages.show("尚未登录")
                    return
                }
        isUpdatingProfile = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req =
                    UpdateProfileRequest(
                        nickname = nickname.ifBlank { null },
                        avatarUrl = avatarUrl?.ifBlank { null },
                        homepage = homepage?.ifBlank { null },
                        signature = signature?.ifBlank { null },
                    )
                val res = network.user.updateProfile(uid, req)
                withContext(Dispatchers.Main) {
                    if (res.status == 0) {
                        ClientMessages.show("资料已更新")
                        currentUser?.let { u ->
                            if (!req.nickname.isNullOrBlank()) u.nickname = req.nickname
                            if (!req.avatarUrl.isNullOrBlank()) u.gravatarUrl = req.avatarUrl
                            // 重组，var by mutableStateOf 的 set 不会因为可变字段内容变化重新发布
                            val tmp = currentUser
                            currentUser = null
                            currentUser = tmp
                        }
                        loadUserProfile()
                        network.cache.invalidateAll()
                        onComplete()
                    } else {
                        ClientMessages.show("更新失败: ${res.message ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ClientMessages.show("网络错误: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { isUpdatingProfile = false }
            }
        }
    }

    fun login() {
        if (loginAccount.text.isBlank() || loginPassword.text.isBlank()) {
            ClientMessages.show("请输入账号和密码")
            return
        }
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                network.login.login(loginAccount.text.toString(), loginPassword.text.toString())
            withContext(Dispatchers.Main) {
                isLoading = false
                if (result.isSuccess) {
                    ClientMessages.show("登录成功")
                    // Try migrate guest data if any
                    if (network.guest.getFingerprint() != null) {
                        migrateGuestData()
                    }
                    refreshUserState()
                } else {
                    ClientMessages.show("登录失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun migrateGuestData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fingerprint = network.guest.getFingerprint() ?: return@launch
                val authCode = network.guest.generateAuthCode(fingerprint) ?: return@launch

                val request =
                    CommandLabUserService.GuestAuthRequest().apply {
                        this.fingerprint = fingerprint
                        this.authCode = authCode
                    }

                val response = network.user.guestMigrate(request)
                if (response.isSuccess()) {
                    network.guest.clearGuestSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendVerifyCode(specialCode: String) {
        if (registerAccount.text.isBlank()) {
            ClientMessages.show("请输入邮箱或手机号")
            return
        }
        isLoading = true
        // Cache special_code for registration (Removed as we now double-verify)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request =
                    CommandLabUserService.SendCodeRequest().apply {
                        if (registerAccount.text.toString().contains("@")) {
                            this.email = registerAccount.text.toString()
                        } else {
                            this.phone = registerAccount.text.toString()
                        }
                        this.specialCode = specialCode
                        this.type = CommandLabUserService.SendCodeRequest.TYPE_REGISTER
                    }
                val response = network.auth.sendCode(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccess()) {
                        ClientMessages.show("验证码已发送")
                    } else {
                        ClientMessages.show("发送失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ClientMessages.show("网络错误: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isCheckingCaptcha = false
                }
            }
        }
    }

    fun register(specialCode: String) {
        if (
            registerAccount.text.isBlank() ||
                registerCode.text.isBlank() ||
                registerPassword.text.isBlank()
        ) {
            ClientMessages.show("请补全注册信息")
            return
        }

        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request =
                    CommandLabUserService.RegisterRequest().apply {
                        if (registerAccount.text.toString().contains("@")) {
                            this.email = registerAccount.text.toString()
                        } else {
                            this.phone = registerAccount.text.toString()
                        }
                        this.code = registerCode.text.toString()
                        this.specialCode = specialCode
                        this.nickname =
                            registerNickname.text.toString().takeIf { it.isNotBlank() }
                                ?: "用户${Clock.System.now().toEpochMilliseconds() % 1000}"
                        this.password = registerPassword.text.toString()
                        this.androidId = network.guest.getFingerprint()
                    }

                val response = network.auth.register(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccess()) {
                        ClientMessages.show("注册成功，请登录")
                        currentTab = UserTab.LOGIN
                        loginAccount.setTextAndPlaceCursorAtEnd(registerAccount.text.toString())
                    } else {
                        ClientMessages.show("注册失败: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ClientMessages.show("网络错误: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isCheckingCaptcha = false
                }
            }
        }
    }

    fun loadMyLibraries(refresh: Boolean = false) {
        if (refresh) {
            myLibrariesPage = 1
            myLibraries.clear()
            myLibrariesHasMore = true
        }
        if (!myLibrariesHasMore) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = network.user.getMyLibraries(pageNum = myLibrariesPage)
                withContext(Dispatchers.Main) {
                    if (response.isSuccess()) {
                        val data = response.data
                        if (data != null && !data.functions.isNullOrEmpty()) {
                            val existingIds = myLibraries.mapNotNull { it.id }.toSet()
                            data.functions!!
                                .filterNotNull()
                                .filter { it.id != null && it.id !in existingIds }
                                .forEach { myLibraries.add(it) }
                            if (
                                data.currentPage != null &&
                                    data.totalCount != null &&
                                    data.perPage != null
                            ) {
                                val totalPages =
                                    (data.totalCount!! + data.perPage!! - 1) / data.perPage!!
                                myLibrariesHasMore = data.currentPage!! < totalPages
                                if (myLibrariesHasMore) myLibrariesPage++
                            }
                        } else {
                            myLibrariesHasMore = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        network.login.logout()
        network.guest.clearGuestSession()
        refreshUserState()
    }

    fun deleteLibrary(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = network.user.deleteLibrary(id)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        myLibraries.removeAll { it.id == id }
                        ClientMessages.show("删除成功")
                    } else {
                        ClientMessages.show("删除失败: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ClientMessages.show("网络错误: ${e.message}") }
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray, filename: String, mimeType: String) {
        if (isUploadingAvatar) return
        isUploadingAvatar = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext =
                    when (mimeType) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        else -> "jpg"
                    }
                val part = UploadFile(bytes, "avatar.$ext", mimeType)
                val result = network.user.uploadAvatar(part)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess() == true) {
                        ClientMessages.show("头像上传成功")
                        val newUrl = result.data?.avatarUrl
                        if (newUrl != null) {
                            currentUser?.gravatarUrl = newUrl
                            val temp = currentUser
                            currentUser = null
                            currentUser = temp
                        }
                        // 资料和卡片都在用 userProfile / cloud cache，需要一起刷新
                        loadUserProfile()
                        network.cache.invalidateAll()
                    } else {
                        ClientMessages.show("上传头像失败: ${result.message ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { ClientMessages.show("上传网络错误: ${e.message}") }
            } finally {
                withContext(Dispatchers.Main) { isUploadingAvatar = false }
            }
        }
    }
}
