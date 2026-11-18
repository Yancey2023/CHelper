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

package yancey.chelper.ui.feature.library.profile

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*
import yancey.chelper.ui.platform.ClientMessages

class UserProfileViewModel(
    private val accounts: AccountRepository,
    private val libraries: LibraryContentRepository,
) : ViewModel() {
    var userProfile by mutableStateOf<UserProfile?>(null)
        private set

    var currentUserId by mutableStateOf<Int?>(accounts.currentUser?.id)
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

    var viewUserId by mutableIntStateOf(-1)
        private set

    var isUpdating by mutableStateOf(false)
        private set

    var isUploadingAvatar by mutableStateOf(false)
        private set

    var updateErrorMessage by mutableStateOf<String?>(null)
        private set

    var updateSuccessMessage by mutableStateOf<String?>(null)
        private set

    var quotaUsed by mutableIntStateOf(0)
        private set

    var quotaLimit by mutableIntStateOf(0)
        private set

    private var profileJob: Job? = null
    private var publicJob: Job? = null
    private var privateJob: Job? = null
    private var quotaJob: Job? = null
    private var generation = 0L
    private var initializedProfileId: Int? = null

    init {
        viewModelScope.launch {
            accounts.state.collect {
                currentUserId = it.user?.id
                if (currentUserId != viewUserId) {
                    privateJob?.cancel()
                    quotaJob?.cancel()
                    privateLibraries = emptyList()
                    quotaUsed = 0
                    quotaLimit = 0
                }
            }
        }
    }

    fun ensureProfileLoaded(id: Int) {
        if (initializedProfileId == id && (userProfile != null || isLoading)) return
        loadProfile(id)
    }

    fun loadProfile(id: Int) {
        initializedProfileId = id
        val changed = viewUserId != id
        viewUserId = id
        val version = ++generation
        profileJob?.cancel()
        publicJob?.cancel()
        privateJob?.cancel()
        quotaJob?.cancel()
        if (changed) {
            userProfile = null
            publicLibraries = emptyList()
            privateLibraries = emptyList()
        }
        publicPageNum = 1
        privatePageNum = 1
        hasMorePublic = true
        hasMorePrivate = true
        isLoadingPublic = false
        isLoadingPrivate = false
        isLoading = true
        errorMessage = null
        profileJob = viewModelScope.launch {
            try {
                val result = accounts.profile(id, forceRefresh = true)
                ensureActive()
                if (version != generation) return@launch
                if (result.isSuccess()) userProfile = result.data
                else errorMessage = result.message ?: "拉取主页失败"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载失败"
            } finally {
                if (version == generation) isLoading = false
            }
            loadPublicLibraries()
            if (currentUserId == id) {
                loadPrivateLibraries()
                loadQuota()
            }
        }
    }

    fun refresh() {
        if (viewUserId > 0) loadProfile(viewUserId)
    }

    fun loadPublicLibraries(loadMore: Boolean = false) {
        if (isLoadingPublic || (loadMore && !hasMorePublic)) return
        val id = viewUserId
        val version = generation
        val page = publicPageNum
        isLoadingPublic = true
        publicJob = viewModelScope.launch {
            try {
                val result = libraries.byAuthor(id, page)
                ensureActive()
                if (version != generation) return@launch
                if (!result.isSuccess()) error(result.message ?: "加载失败")
                val rows = result.data?.functions.orEmpty().filterNotNull()
                publicLibraries =
                    ((if (page == 1) emptyList() else publicLibraries) + rows).distinctBy {
                        it.id ?: it.uuid
                    }
                hasMorePublic =
                    result.data?.totalCount?.let { page.toLong() * 20 < it } ?: (rows.size >= 20)
                publicPageNum = page + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "加载失败"
            } finally {
                if (version == generation) isLoadingPublic = false
            }
        }
    }

    fun loadPrivateLibraries(loadMore: Boolean = false) {
        if (currentUserId != viewUserId || isLoadingPrivate || (loadMore && !hasMorePrivate)) return
        val version = generation
        val page = privatePageNum
        isLoadingPrivate = true
        privateJob = viewModelScope.launch {
            try {
                val result = libraries.mine(pageNum = page, pageSize = 20, forceRefresh = page == 1)
                ensureActive()
                if (version != generation) return@launch
                if (!result.isSuccess()) error(result.message ?: "拉取私有云库失败")
                val rows = result.data?.functions.orEmpty().filterNotNull()
                privateLibraries =
                    ((if (page == 1) emptyList() else privateLibraries) + rows).distinctBy {
                        it.id ?: it.uuid
                    }
                hasMorePrivate =
                    result.data?.totalCount?.let { page.toLong() * 20 < it } ?: (rows.size >= 20)
                privatePageNum = page + 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "加载失败"
            } finally {
                if (version == generation) isLoadingPrivate = false
            }
        }
    }

    private fun loadQuota() {
        val version = generation
        quotaJob = viewModelScope.launch {
            try {
                val result = libraries.quota(true)
                ensureActive()
                if (version == generation && result.isSuccess()) {
                    quotaUsed = result.data?.used ?: 0
                    quotaLimit = result.data?.limit ?: 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    private val deleting = mutableSetOf<Int>()

    fun deleteOrUnpublishLibrary(libraryId: Int, isPublic: Boolean) {
        if (!deleting.add(libraryId)) return
        viewModelScope.launch {
            try {
                val version = generation
                val result = libraries.delete(libraryId)
                if (version != generation) return@launch
                if (!result.isSuccess()) error(result.message ?: "操作失败")
                updateSuccessMessage = if (isPublic) "下架成功" else "删除成功"
                publicLibraries = publicLibraries.filterNot { it.id == libraryId }
                privateLibraries = privateLibraries.filterNot { it.id == libraryId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "操作失败"
            } finally {
                deleting.remove(libraryId)
            }
        }
    }

    fun updateProfile(
        nickname: String,
        avatarUrl: String?,
        homepage: String?,
        signature: String?,
        onComplete: () -> Unit,
    ) {
        if (isUpdating || isUploadingAvatar) return
        val id = viewUserId
        val changes =
            ProfileChanges(
                nickname.ifBlank { null },
                avatarUrl?.ifBlank { null },
                homepage?.ifBlank { null },
                signature?.ifBlank { null },
            )
        isUpdating = true
        clearUpdateMessages()
        viewModelScope.launch {
            try {
                val result = accounts.updateProfile(id, changes)
                if (!result.isSuccess()) error(result.message ?: "更新失败")
                if (viewUserId == id) {
                    updateSuccessMessage = "资料已更新"
                    loadProfile(id)
                    onComplete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateErrorMessage = e.message ?: "更新失败"
            } finally {
                isUpdating = false
            }
        }
    }

    fun clearUpdateMessages() {
        updateErrorMessage = null
        updateSuccessMessage = null
    }

    fun uploadAvatar(bytes: ByteArray, mimeType: String) {
        if (isUploadingAvatar || isUpdating) return
        val id = viewUserId
        isUploadingAvatar = true
        viewModelScope.launch {
            try {
                val result = accounts.uploadAvatar(bytes, mimeType)
                if (!result.isSuccess()) error(result.message ?: "头像上传失败")
                if (viewUserId == id) {
                    ClientMessages.show("头像上传成功")
                    loadProfile(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "头像上传失败")
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    private var reporting = false

    fun submitReport(report: AbuseReport) {
        if (reporting) return
        reporting = true
        viewModelScope.launch {
            try {
                val result = libraries.report(report)
                ClientMessages.show(result.message ?: if (result.isSuccess()) "举报已提交" else "举报失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "举报失败")
            } finally {
                reporting = false
            }
        }
    }
}
