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

package yancey.chelper.ui.library.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.LibrarySearchRepository
import yancey.chelper.data.NetworkLibrarySearchRepository
import yancey.chelper.data.matchingLibraries
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.util.LoginUtil

internal data class LibrarySearchUiState(
    val query: String = "",
    val localLibraries: List<LibraryFunction> = emptyList(),
    val privateLibraries: List<LibraryFunction> = emptyList(),
    val publicLibraries: List<LibraryFunction> = emptyList(),
    val localLoading: Boolean = false,
    val privateLoading: Boolean = false,
    val publicLoading: Boolean = false,
    val privateError: String? = null,
    val publicError: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = false,
) {
    val isLoading
        get() = localLoading || privateLoading || publicLoading

    val localAndPrivateLibraries
        get() = localLibraries + privateLibraries
}

internal class LibrarySearchViewModel(
    private val repository: LibrarySearchRepository = NetworkLibrarySearchRepository(),
    private val requestScope: CoroutineScope? = null,
    private val localDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val accountId: StateFlow<Int?> = LoginUtil.accountId,
) : ViewModel() {
    val keyword = TextFieldState()
    private val _uiState = MutableStateFlow(LibrarySearchUiState())
    val uiState = _uiState.asStateFlow()
    private val scope
        get() = requestScope ?: viewModelScope

    private var generation = 0L
    private var publicJob: Job? = null
    private var privateJob: Job? = null
    private var localJob: Job? = null
    private var initialKeywordSet = false
    private var publicVersion = 0L
    private var privateVersion = 0L

    init {
        var observedAccount = accountId.value
        scope.launch {
            accountId.collect { ownerId ->
                if (ownerId != observedAccount) {
                    observedAccount = ownerId
                    ++privateVersion
                    privateJob?.cancel()
                    _uiState.value =
                        _uiState.value.copy(
                            privateLibraries = emptyList(),
                            privateLoading = false,
                            privateError = null,
                        )
                    if (ownerId != null) fetchPrivate(_uiState.value.query, generation)
                }
            }
        }
    }

    fun setInitialKeyword(initialWord: String?) {
        if (initialKeywordSet) return
        initialKeywordSet = true
        if (!initialWord.isNullOrBlank()) keyword.setTextAndPlaceCursorAtEnd(initialWord)
    }

    fun search(localLibraryFunctions: List<LibraryFunction>?) {
        val query = keyword.text.toString().trim()
        val version = ++generation
        ++publicVersion
        ++privateVersion
        publicJob?.cancel()
        privateJob?.cancel()
        localJob?.cancel()
        _uiState.value = LibrarySearchUiState(query = query)
        if (query.isEmpty()) return
        refreshLocal(localLibraryFunctions)
        fetchPrivate(query, version)
        fetchPublic(query, version, reset = true)
    }

    fun refreshLocal(libraries: List<LibraryFunction>?) {
        val state = _uiState.value
        if (state.query.isEmpty()) return
        localJob?.cancel()
        val version = generation
        _uiState.value = state.copy(localLoading = true)
        localJob = scope.launch {
            val matches =
                withContext(localDispatcher) {
                    libraries.orEmpty().matchingLibraries(state.query, "[本地包]")
                }
            if (version == generation)
                _uiState.value = _uiState.value.copy(localLibraries = matches, localLoading = false)
        }
    }

    fun retryPrivate() = fetchPrivate(_uiState.value.query, generation)

    fun retryPublic() =
        fetchPublic(_uiState.value.query, generation, _uiState.value.currentPage == 0)

    fun loadMore() {
        val state = _uiState.value
        if (!state.publicLoading && state.hasMore && state.publicError == null) {
            fetchPublic(state.query, generation, reset = false)
        }
    }

    private fun fetchPrivate(query: String, version: Long) {
        if (query.isEmpty()) return
        val requestVersion = ++privateVersion
        val ownerId = accountId.value
        privateJob?.cancel()
        _uiState.value = _uiState.value.copy(privateLoading = true, privateError = null)
        privateJob = scope.launch {
            try {
                val matches = repository.privateLibraries(query)
                if (
                    version == generation &&
                        requestVersion == privateVersion &&
                        accountId.value == ownerId
                )
                    _uiState.value = _uiState.value.copy(privateLibraries = matches)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (
                    version == generation &&
                        requestVersion == privateVersion &&
                        accountId.value == ownerId
                )
                    _uiState.value = _uiState.value.copy(privateError = e.message ?: "加载私有库失败")
            } finally {
                if (version == generation && requestVersion == privateVersion)
                    _uiState.value = _uiState.value.copy(privateLoading = false)
            }
        }
    }

    private fun fetchPublic(query: String, version: Long, reset: Boolean) {
        if (query.isEmpty()) return
        val requestVersion = ++publicVersion
        publicJob?.cancel()
        val page = if (reset) 1 else _uiState.value.currentPage + 1
        _uiState.value = _uiState.value.copy(publicLoading = true, publicError = null)
        publicJob = scope.launch {
            try {
                val result = repository.publicLibraries(query, page)
                if (version != generation || requestVersion != publicVersion) return@launch
                val existing = if (reset) emptyList() else _uiState.value.publicLibraries
                val ids = existing.mapNotNullTo(HashSet()) { it.id }
                val added = result.libraries.filter { it.id?.let(ids::add) ?: true }
                _uiState.value =
                    _uiState.value.copy(
                        publicLibraries = existing + added,
                        currentPage = page,
                        hasMore = page < result.totalPages,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == generation && requestVersion == publicVersion)
                    _uiState.value = _uiState.value.copy(publicError = e.message ?: "加载公开库失败")
            } finally {
                if (version == generation && requestVersion == publicVersion)
                    _uiState.value = _uiState.value.copy(publicLoading = false)
            }
        }
    }
}
