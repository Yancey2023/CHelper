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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import yancey.chelper.network.library.util.GuestAuthUtil
import yancey.chelper.network.library.util.LoginUtil

class CPLUserViewModel : ViewModel() {

    enum class UserTab {
        LOGIN,
        REGISTER
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

    // My Cloud Libraries
    val myLibraries = mutableStateListOf<LibraryFunction>()
    var myLibrariesPage by mutableStateOf(1)
    var myLibrariesHasMore by mutableStateOf(true)

    init {
        refreshUserState()
    }

    fun refreshUserState() {
        if (LoginUtil.isLoggedIn) {
            currentUser = LoginUtil.currentUser
            isGuest = false
            loadMyLibraries(true)
        } else if (GuestAuthUtil.isLoggedIn()) {
            currentUser = GuestAuthUtil.guestUser
            isGuest = true
        } else {
            currentUser = null
            isGuest = false
        }
    }

    fun login() {
        if (loginAccount.text.isBlank() || loginPassword.text.isBlank()) {
            Toaster.show("请输入账号和密码")
            return
        }
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                LoginUtil.login(loginAccount.text.toString(), loginPassword.text.toString())
            withContext(Dispatchers.Main) {
                isLoading = false
                if (result.isSuccess) {
                    Toaster.show("登录成功")
                    // Try migrate guest data if any
                    if (GuestAuthUtil.getFingerprint() != null) {
                        migrateGuestData()
                    }
                    refreshUserState()
                } else {
                    Toaster.show("登录失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun migrateGuestData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: Implement migration logic using GuestAuthUtil signature generation
                // For now we just skip or implement if needed
                val fingerprint = GuestAuthUtil.getFingerprint() ?: return@launch
                // val authCode = GuestAuthUtil.generateAuthCode(fingerprint) // Need public access to this or new method
                // ServiceManager.COMMAND_LAB_USER_SERVICE.guestMigrate(...)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendVerifyCode(specialCode: String) {
        if (registerAccount.text.isBlank()) {
            Toaster.show("请输入邮箱或手机号")
            return
        }
        isLoading = true
        // Cache special_code for registration (Removed as we now double-verify)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = CommandLabUserService.SendCodeRequest().apply {
                    if (registerAccount.text.toString().contains("@")) {
                        this.email = registerAccount.text.toString()
                    } else {
                        this.phone = registerAccount.text.toString()
                    }
                    this.special_code = specialCode
                    this.type = CommandLabUserService.SendCodeRequest.TYPE_REGISTER
                }
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.sendCode(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.body()?.isSuccess() == true) {
                        Toaster.show("验证码已发送")
                    } else {
                        Toaster.show("发送失败: ${response.body()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("网络错误: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isCheckingCaptcha = false
                }
            }
        }
    }

    fun register(specialCode: String) {
        if (registerAccount.text.isBlank() || registerCode.text.isBlank() || registerPassword.text.isBlank()) {
            Toaster.show("请补全注册信息")
            return
        }

        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = CommandLabUserService.RegisterRequest().apply {
                    if (registerAccount.text.toString().contains("@")) {
                        this.email = registerAccount.text.toString()
                    } else {
                        this.phone = registerAccount.text.toString()
                    }
                    this.code = registerCode.text.toString()
                    this.special_code = specialCode
                    this.nickname = registerNickname.text.toString().takeIf { it.isNotBlank() }
                        ?: "用户${System.currentTimeMillis() % 1000}"
                    this.password = registerPassword.text.toString()
                    this.android_id = GuestAuthUtil.getFingerprint()
                }

                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.register(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.body()?.isSuccess() == true) {
                        Toaster.show("注册成功，请登录")
                        currentTab = UserTab.LOGIN
                        loginAccount.setTextAndPlaceCursorAtEnd(registerAccount.text.toString())
                    } else {
                        Toaster.show("注册失败: ${response.body()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("网络错误: ${e.message}")
                }
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
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.getMyLibraries(
                    pageNum = myLibrariesPage
                ).execute()

                withContext(Dispatchers.Main) {
                    if (response.body()?.isSuccess() == true) {
                        val data = response.body()!!.data
                        if (data != null && !data.functions.isNullOrEmpty()) {
                            val existingIds = myLibraries.mapNotNull { it.id }.toSet()
                            data.functions!!.filterNotNull()
                                .filter { it.id != null && it.id !in existingIds }
                                .forEach { myLibraries.add(it) }
                            if (data.currentPage != null && data.totalCount != null && data.perPage != null) {
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
        LoginUtil.logout()
        GuestAuthUtil.clearGuestSession() // Optional: clear guest session too? maybe revert to guest
        refreshUserState()
    }

    fun deleteLibrary(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ServiceManager.COMMAND_LAB_USER_SERVICE?.deleteLibrary(id)
                withContext(Dispatchers.Main) {
                    if (result?.isSuccess() == true) {
                        myLibraries.removeAll { it.id == id }
                        Toaster.show("删除成功")
                    } else {
                        Toaster.show("删除失败: ${result?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("网络错误: ${e.message}")
                }
            }
        }
    }
}
