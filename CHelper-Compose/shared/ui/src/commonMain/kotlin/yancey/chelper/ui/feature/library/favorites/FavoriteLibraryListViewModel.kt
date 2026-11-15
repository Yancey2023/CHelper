/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package yancey.chelper.ui.feature.library.favorites

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import yancey.chelper.domain.library.*

class FavoriteLibraryListViewModel(
    private val accounts: AccountRepository,
    private val collection: FavoriteCollectionRepository,
) : ViewModel() {
    var favorites by mutableStateOf<List<LibraryFunction>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var actionMessage by mutableStateOf<String?>(null)
    private var loadJob: Job? = null
    private var version = 0L
    private var loaded = false
    private val changing = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            var revision = accounts.state.value.revision
            accounts.state.collect { account ->
                if (revision != account.revision) {
                    revision = account.revision
                    version++
                    loadJob?.cancel()
                    favorites = emptyList()
                    loaded = false
                    isLoading = false
                    load()
                }
            }
        }
    }

    fun load(force: Boolean = false) {
        if (!accounts.isLoggedIn) {
            favorites = emptyList()
            errorMessage = "请先登录账号查看云端收藏夹"
            return
        }
        if (!force && (isLoading || loaded)) return
        loadJob?.cancel()
        val current = ++version
        isLoading = true
        errorMessage = null
        loadJob = viewModelScope.launch {
            try {
                val result = collection.load()
                ensureActive()
                if (current == version) {
                    favorites = result.toList()
                    loaded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "收藏夹加载失败"
            } finally {
                if (current == version) isLoading = false
            }
        }
    }

    fun removeFavorite(library: LibraryFunction) {
        val id = library.id ?: return
        if (!changing.add(id)) return
        viewModelScope.launch {
            try {
                val result = collection.remove(id)
                if (result.isSuccess() && result.data?.isFavorited == false) {
                    favorites = favorites.filterNot {
                        it.id == id || (library.uuid != null && it.uuid == library.uuid)
                    }
                    actionMessage = result.message ?: "已取消收藏"
                } else actionMessage = result.message ?: "取消收藏失败"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionMessage = e.message ?: "取消收藏失败"
            } finally {
                changing.remove(id)
            }
        }
    }
}
