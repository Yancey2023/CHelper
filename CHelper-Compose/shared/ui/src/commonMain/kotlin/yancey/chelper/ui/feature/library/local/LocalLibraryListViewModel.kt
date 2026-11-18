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

package yancey.chelper.ui.feature.library.local

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import yancey.chelper.data.library.document.toFullLocalMcd
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*
import yancey.chelper.ui.platform.ClientMessages

class LocalLibraryListViewModel(
    private val accounts: AccountRepository,
    private val libraries: LibraryContentRepository,
) : ViewModel() {
    var keyword by mutableStateOf(TextFieldState())
    var isShowImportDialog by mutableStateOf(false)
    var isShowExportDialog by mutableStateOf(false)
    var localSort by mutableStateOf(LocalLibrarySort.DEFAULT)
    var isLoggedIn by mutableStateOf(false)
        private set

    var currentUser by mutableStateOf<AccountUser?>(null)
        private set

    var userProfile by mutableStateOf<UserProfile?>(null)
        private set

    var quotaUsed by mutableStateOf<Int?>(null)
        private set

    var quotaLimit by mutableStateOf<Int?>(null)
        private set

    var myCloudLibraries by mutableStateOf<List<LibraryFunction>>(emptyList())
        private set

    var isCloudLibrariesLoading by mutableStateOf(false)
        private set

    var cloudLibrariesPage by mutableIntStateOf(1)
        private set

    var cloudLibrariesHasMore by mutableStateOf(true)
        private set

    var uploadingLocalEntryId by mutableStateOf<String?>(null)
        private set

    private var listJob: Job? = null
    private var profileJob: Job? = null
    private var quotaJob: Job? = null
    private var listVersion = 0L
    private var accountRevision = Long.MIN_VALUE

    init {
        viewModelScope.launch {
            accounts.state.collect { account ->
                val changed = accountRevision != account.revision
                accountRevision = account.revision
                currentUser = account.user
                isLoggedIn = account.user != null
                if (changed) {
                    listVersion++
                    listJob?.cancel()
                    profileJob?.cancel()
                    quotaJob?.cancel()
                    myCloudLibraries = emptyList()
                    userProfile = null
                    quotaUsed = null
                    quotaLimit = null
                    isCloudLibrariesLoading = false
                    if (isLoggedIn) refreshUserState()
                }
            }
        }
    }

    fun refreshUserState(forceRefresh: Boolean = false) {
        currentUser = accounts.currentUser
        isLoggedIn = accounts.isLoggedIn
        if (!isLoggedIn) {
            myCloudLibraries = emptyList()
            userProfile = null
            quotaUsed = null
            quotaLimit = null
            return
        }
        val id = currentUser?.id ?: return
        loadCloudLibraries(true, forceRefresh)
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                val result = accounts.profile(id, forceRefresh)
                ensureActive()
                if (result.isSuccess() && currentUser?.id == id) userProfile = result.data
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        quotaJob?.cancel()
        quotaJob = viewModelScope.launch {
            try {
                val result = libraries.quota(forceRefresh)
                ensureActive()
                if (result.isSuccess() && currentUser?.id == id) {
                    quotaUsed = result.data?.used
                    quotaLimit = result.data?.limit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun logout() {
        accounts.logout()
    }

    fun loadCloudLibraries(refresh: Boolean = false, forceRefresh: Boolean = false) {
        if (!accounts.isLoggedIn) return
        if (refresh) {
            listJob?.cancel()
        } else if (isCloudLibrariesLoading || !cloudLibrariesHasMore) return
        val version = ++listVersion
        val page = if (refresh) 1 else cloudLibrariesPage
        isCloudLibrariesLoading = true
        listJob = viewModelScope.launch {
            try {
                val result = libraries.mine(pageNum = page, forceRefresh = forceRefresh)
                ensureActive()
                if (version != listVersion) return@launch
                if (result.isSuccess()) {
                    val data = result.data ?: return@launch
                    val rows = data.functions.orEmpty().filterNotNull()
                    myCloudLibraries =
                        ((if (refresh) emptyList() else myCloudLibraries) + rows).distinctBy {
                            it.id ?: it.uuid
                        }
                    val size = (data.perPage ?: 100).coerceAtLeast(1)
                    cloudLibrariesHasMore =
                        data.totalCount?.let { page.toLong() * size < it } ?: (rows.size >= size)
                    cloudLibrariesPage = page + 1
                } else ClientMessages.show(result.message ?: "加载失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "加载失败")
            } finally {
                if (version == listVersion) isCloudLibrariesLoading = false
            }
        }
    }

    fun uploadToCloud(
        library: LibraryFunction,
        localEntryId: String,
        localDataStore: LocalCommandLabDataStore? = null,
    ) {
        if (!accounts.isLoggedIn) {
            ClientMessages.show("请先登录")
            return
        }
        if (uploadingLocalEntryId != null) return
        val owner = accounts.currentUser?.id
        val content = buildFullMCD(library)
        uploadingLocalEntryId = localEntryId
        viewModelScope.launch {
            try {
                val result = libraries.upload(content)
                ensureActive()
                if (result.isSuccess()) {
                    if (accounts.currentUser?.id != owner) return@launch
                    // Await the durable local acknowledgement before announcing completion.
                    withContext(Dispatchers.IO) {
                        localDataStore?.markLocalLibrarySynced(
                            localEntryId,
                            result.data?.uuid,
                            library,
                        )
                    }
                    ClientMessages.show("上传成功")
                    loadCloudLibraries(true, true)
                } else ClientMessages.show(result.message ?: "上传失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "上传失败")
            } finally {
                uploadingLocalEntryId = null
            }
        }
    }

    private val deleting = mutableSetOf<Int>()

    fun deleteCloudLibrary(id: Int) {
        if (!deleting.add(id)) return
        viewModelScope.launch {
            try {
                val result = libraries.delete(id)
                if (result.isSuccess()) {
                    myCloudLibraries = myCloudLibraries.filterNot { it.id == id }
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

    companion object {
        fun buildFullMCD(library: LibraryFunction) = library.toFullLocalMcd()
    }
}
