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

    // Login Fields
    val loginAccount = TextFieldState()
    val loginPassword = TextFieldState()

    // Register Fields
    val registerEmail = TextFieldState()
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
            val result = LoginUtil.login(loginAccount.text.toString(), loginPassword.text.toString())
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
        if (registerEmail.text.isBlank()) {
            Toaster.show("请输入邮箱")
            return
        }
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = CommandLabUserService.SendMailRequest().apply {
                    this.mail = registerEmail.text.toString()
                    this.special_code = specialCode
                    this.type = CommandLabUserService.SendMailRequest.TYPE_REGISTER
                }
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.sendMail(request).execute()
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
        if (registerEmail.text.isBlank() || registerCode.text.isBlank() || registerPassword.text.isBlank()) {
            Toaster.show("请补全注册信息")
            return
        }
        isLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = CommandLabUserService.RegisterRequest().apply {
                    this.mail = registerEmail.text.toString()
                    this.mailCode = registerCode.text.toString()
                    this.password = registerPassword.text.toString()
                    this.nickname = registerNickname.text.toString()
                    this.special_code = specialCode
                }
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.register(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.body()?.isSuccess() == true) {
                        Toaster.show("注册成功，请登录")
                        currentTab = UserTab.LOGIN
                        loginAccount.setTextAndPlaceCursorAtEnd(registerEmail.text.toString())
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
                            data.functions!!.filterNotNull().forEach { myLibraries.add(it) }
                            if (data.currentPage != null && data.totalCount != null && data.perPage != null) {
                                val totalPages = (data.totalCount!! + data.perPage!! - 1) / data.perPage!!
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
}
