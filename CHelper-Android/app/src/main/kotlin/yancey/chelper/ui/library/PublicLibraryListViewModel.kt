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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yancey.chelper.data.NetworkPublicLibraryRepository
import yancey.chelper.data.PublicLibraryRepository
import yancey.chelper.network.library.data.LibraryFunction

internal data class PublicLibraryListUiState(
    val libraries: List<LibraryFunction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val hasMore: Boolean = true,
    val isRecommendMode: Boolean = false,
    val initialized: Boolean = false,
    val retryFromStart: Boolean = true,
)

internal class PublicLibraryListViewModel(
    private val repository: PublicLibraryRepository = NetworkPublicLibraryRepository(),
    private val requestScope: CoroutineScope? = null,
    initialState: PublicLibraryListUiState = PublicLibraryListUiState(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()
    private val scope
        get() = requestScope ?: viewModelScope

    private val caches = mutableMapOf<Boolean, PublicLibraryListUiState>()
    private var requestJob: Job? = null
    private var requestVersion = 0L
    private var unreadChecked = false
    private val favoriteRequests = mutableSetOf<Int>()

    fun initialize(recommended: Boolean) {
        if (!_uiState.value.initialized && requestJob == null) switchMode(recommended)
    }

    fun switchMode(isRecommend: Boolean) {
        ++requestVersion
        requestJob?.cancel()
        val previous = _uiState.value
        if (previous.initialized)
            caches[previous.isRecommendMode] =
                previous.copy(isLoading = false, actionMessage = null)
        _uiState.value =
            caches[isRecommend] ?: PublicLibraryListUiState(isRecommendMode = isRecommend)
        if (!_uiState.value.initialized) load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun retry() = load(reset = _uiState.value.retryFromStart)

    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoading && state.hasMore && state.initialized) load(reset = false)
    }

    private fun load(reset: Boolean) {
        val before = _uiState.value
        if (before.isLoading && !reset) return
        val version = ++requestVersion
        requestJob?.cancel()
        val mode = before.isRecommendMode
        val page = if (reset) 1 else before.currentPage + 1
        _uiState.value = before.copy(isLoading = true, errorMessage = null)
        requestJob = scope.launch {
            try {
                val response = repository.load(page, mode)
                if (version != requestVersion) return@launch
                val existing = if (reset) emptyList() else _uiState.value.libraries
                val seen = existing.mapNotNullTo(HashSet()) { it.id }
                val added = response.libraries.filter { it.id?.let(seen::add) ?: true }
                _uiState.value =
                    _uiState.value.copy(
                        libraries = existing + added,
                        currentPage = page,
                        totalPages = response.totalPages,
                        hasMore = if (mode) added.isNotEmpty() else page < response.totalPages,
                        initialized = true,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value =
                        _uiState.value.copy(
                            errorMessage = e.message ?: "网络错误",
                            initialized = true,
                            retryFromStart = reset,
                        )
                }
            } finally {
                // An old request must not clear the new mode's loading state or publish its error.
                if (version == requestVersion) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun consumeActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    fun checkUnreadMessages() {
        if (unreadChecked) return
        unreadChecked = true
        scope.launch {
            try {
                val count = repository.unreadCount()
                if (count > 0)
                    _uiState.value =
                        _uiState.value.copy(actionMessage = "您有 $count 条未读站内信，可前往用户中心查看")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A notification failure must not block browsing the library.
            }
        }
    }

    fun toggleFavorite(library: LibraryFunction) {
        if (!repository.canFavorite()) {
            _uiState.value = _uiState.value.copy(actionMessage = "请先登录正式账号使用收藏夹")
            return
        }
        val id = library.id ?: return
        if (!favoriteRequests.add(id)) return
        scope.launch {
            try {
                val result = repository.toggleFavorite(id)
                fun PublicLibraryListUiState.updated() =
                    copy(
                        libraries =
                            libraries.map {
                                if (it.id == id) it.copy(isFavorited = result.favorited) else it
                            }
                    )
                caches.replaceAll { _, cached -> cached.updated() }
                _uiState.value =
                    _uiState.value
                        .updated()
                        .copy(
                            actionMessage =
                                result.message ?: if (result.favorited) "已加入收藏夹" else "已取消收藏"
                        )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionMessage = e.message ?: "收藏操作失败")
            } finally {
                favoriteRequests.remove(id)
            }
        }
    }
}
