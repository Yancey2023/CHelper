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
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.*
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*
import yancey.chelper.ui.platform.ClientMessages

data class AccountUiState(
    val user: AccountUser? = null,
    val guest: Boolean = false,
    val profile: UserProfile? = null,
    val libraries: List<LibraryFunction> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val updatingProfile: Boolean = false,
)

class AccountViewModel(
    private val accounts: AccountRepository,
    private val libraries: LibraryContentRepository,
) : ViewModel() {
    enum class UserTab {
        LOGIN,
        REGISTER,
    }

    var currentTab by mutableStateOf(UserTab.LOGIN)
    var isCheckingCaptcha by mutableStateOf(false)
    val loginAccount = TextFieldState()
    val loginPassword = TextFieldState()
    val registerAccount = TextFieldState()
    val registerCode = TextFieldState()
    val registerPassword = TextFieldState()
    val registerNickname = TextFieldState()
    var state by mutableStateOf(AccountUiState())
        private set

    val currentUser
        get() = state.user

    val isGuest
        get() = state.guest

    val userProfile
        get() = state.profile

    val myLibraries
        get() = state.libraries

    val myLibrariesPage
        get() = state.nextPage

    val myLibrariesHasMore
        get() = state.hasMore

    val isLoading
        get() = state.loading

    val isUploadingAvatar
        get() = state.uploadingAvatar

    val isUpdatingProfile
        get() = state.updatingProfile

    private var listJob: Job? = null
    private var profileJob: Job? = null
    private var actionJob: Job? = null
    private var pageVersion = 0L
    private var pageLoading = false
    private var observedRevision = Long.MIN_VALUE

    init {
        viewModelScope.launch {
            accounts.state.collect { account ->
                val changed =
                    observedRevision != account.revision ||
                        state.user?.id != (account.user ?: account.guest)?.id
                observedRevision = account.revision
                if (changed) {
                    pageVersion++
                    listJob?.cancel()
                    profileJob?.cancel()
                    pageLoading = false
                    state =
                        AccountUiState(
                            user = account.user ?: account.guest,
                            guest = account.user == null && account.guest != null,
                        )
                    if (account.user != null) {
                        loadMyLibraries(true)
                        loadUserProfile()
                    }
                } else
                    state =
                        state.copy(
                            user = account.user ?: account.guest,
                            guest = account.user == null && account.guest != null,
                        )
            }
        }
    }

    fun refreshUserState() {
        val account = accounts.state.value
        state =
            state.copy(
                user = account.user ?: account.guest,
                guest = account.user == null && account.guest != null,
            )
        if (account.user != null) {
            loadMyLibraries(true)
            loadUserProfile()
        }
    }

    fun loadUserProfile() {
        val id = accounts.currentUser?.id ?: return
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                val response = accounts.profile(id, forceRefresh = true)
                currentCoroutineContext().ensureActive()
                if (accounts.currentUser?.id == id && response.isSuccess())
                    state = state.copy(profile = response.data)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun updateProfile(
        nickname: String,
        avatarUrl: String?,
        homepage: String?,
        signature: String?,
        onComplete: () -> Unit,
    ) {
        val id = accounts.currentUser?.id ?: return
        if (state.updatingProfile || state.uploadingAvatar) return
        val changes =
            ProfileChanges(
                nickname.ifBlank { null },
                avatarUrl?.ifBlank { null },
                homepage?.ifBlank { null },
                signature?.ifBlank { null },
            )
        state = state.copy(updatingProfile = true)
        viewModelScope.launch {
            try {
                val result = accounts.updateProfile(id, changes)
                currentCoroutineContext().ensureActive()
                if (result.isSuccess()) {
                    loadUserProfile()
                    ClientMessages.show("资料已更新")
                    onComplete()
                } else ClientMessages.show(result.message ?: "更新失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "更新失败")
            } finally {
                state = state.copy(updatingProfile = false)
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        state = state.copy(loading = true)
        actionJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "操作失败")
            } finally {
                state = state.copy(loading = false)
                isCheckingCaptcha = false
            }
        }
    }

    fun login() {
        val account = loginAccount.text.toString()
        val password = loginPassword.text.toString()
        if (account.isBlank() || password.isBlank()) {
            ClientMessages.show("请输入账号和密码")
            return
        }
        action {
            val result = accounts.login(account, password)
            currentCoroutineContext().ensureActive()
            ClientMessages.show(
                if (result.isSuccess) "登录成功" else "登录失败: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun sendVerifyCode(specialCode: String) {
        val account = registerAccount.text.toString()
        if (account.isBlank()) {
            ClientMessages.show("请输入邮箱或手机号")
            return
        }
        action {
            val result = accounts.sendVerification(account, specialCode)
            ClientMessages.show(if (result.isSuccess()) "验证码已发送" else result.message ?: "发送失败")
        }
    }

    fun register(specialCode: String) {
        val details =
            Registration(
                registerAccount.text.toString(),
                registerPassword.text.toString(),
                registerNickname.text.toString().ifBlank {
                    "用户${Clock.System.now().toEpochMilliseconds() % 1000}"
                },
                registerCode.text.toString(),
                specialCode,
            )
        if (details.account.isBlank() || details.password.isBlank() || details.code.isBlank()) {
            ClientMessages.show("请补全注册信息")
            return
        }
        action {
            val result = accounts.register(details)
            currentCoroutineContext().ensureActive()
            if (result.isSuccess()) {
                ClientMessages.show("注册成功，请登录")
                currentTab = UserTab.LOGIN
                loginAccount.setTextAndPlaceCursorAtEnd(details.account)
            } else ClientMessages.show(result.message ?: "注册失败")
        }
    }

    fun loadMyLibraries(refresh: Boolean = false) {
        if (accounts.currentUser == null) return
        if (refresh) {
            listJob?.cancel()
            pageLoading = false
        } else if (pageLoading || !state.hasMore) return
        val version = ++pageVersion
        val page = if (refresh) 1 else state.nextPage
        pageLoading = true
        listJob = viewModelScope.launch {
            try {
                val response = libraries.mine(pageNum = page, forceRefresh = refresh)
                currentCoroutineContext().ensureActive()
                if (version != pageVersion) return@launch
                if (response.isSuccess()) {
                    val data = response.data ?: return@launch
                    val rows = data.functions.orEmpty().filterNotNull()
                    val size = (data.perPage ?: 100).coerceAtLeast(1)
                    val more =
                        data.totalCount?.let { page.toLong() * size < it } ?: (rows.size >= size)
                    state =
                        state.copy(
                            libraries =
                                ((if (refresh) emptyList() else state.libraries) + rows)
                                    .distinctBy { it.id ?: it.uuid },
                            nextPage = page + 1,
                            hasMore = more,
                        )
                } else ClientMessages.show(response.message ?: "加载失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "加载失败")
            } finally {
                if (version == pageVersion) pageLoading = false
            }
        }
    }

    fun logout() {
        actionJob?.cancel()
        accounts.logout()
    }

    private val deleting = mutableSetOf<Int>()

    fun deleteLibrary(id: Int) {
        if (!deleting.add(id)) return
        viewModelScope.launch {
            try {
                val result = libraries.delete(id)
                if (result.isSuccess()) {
                    state = state.copy(libraries = state.libraries.filterNot { it.id == id })
                    ClientMessages.show("删除成功")
                } else ClientMessages.show(result.message ?: "删除失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "删除失败")
            } finally {
                deleting.remove(id)
            }
        }
    }

    fun uploadAvatar(bytes: ByteArray, filename: String, mimeType: String) {
        if (state.uploadingAvatar || state.updatingProfile) return
        state = state.copy(uploadingAvatar = true)
        viewModelScope.launch {
            try {
                val result = accounts.uploadAvatar(bytes, mimeType)
                if (result.isSuccess()) {
                    ClientMessages.show("头像上传成功")
                    loadUserProfile()
                } else ClientMessages.show(result.message ?: "头像上传失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "头像上传失败")
            } finally {
                state = state.copy(uploadingAvatar = false)
            }
        }
    }
}
