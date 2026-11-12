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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.platform.ClientServices

class FavoriteLibraryListViewModel : ViewModel() {
    val favorites = mutableStateListOf<LibraryFunction>()
    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var actionMessage by mutableStateOf<String?>(null)
    private var migrationChecked = false

    fun load(context: ClientServices, force: Boolean = false) {
        if (isLoading || (!force && favorites.isNotEmpty())) return
        viewModelScope.launch {
            if (!LoginUtil.isLoggedIn || LoginUtil.currentUser?.isGuest == true) {
                errorMessage = "请先登录账号查看云端收藏夹"
                favorites.clear()
                return@launch
            }

            isLoading = true
            errorMessage = null
            try {
                var remote = fetchFavorites()
                if (!migrationChecked) {
                    migrationChecked = true
                    if (migrateLegacyFavorites(context, remote)) {
                        remote = fetchFavorites()
                    }
                }
                favorites.clear()
                favorites.addAll(remote)
            } catch (e: Exception) {
                errorMessage = e.message ?: "收藏夹加载失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun removeFavorite(library: LibraryFunction) {
        val id = library.id ?: return
        viewModelScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        ServiceManager.COMMAND_LAB_USER_SERVICE.toggleFavorite(id)
                    }
                if (result.isSuccess() && result.data?.isFavorited == false) {
                    favorites.removeAll { it.uuid == library.uuid || it.id == id }
                    actionMessage = result.message ?: "已取消收藏"
                } else {
                    actionMessage = result.message ?: "取消收藏失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    private suspend fun fetchFavorites(): List<LibraryFunction> =
        withContext(Dispatchers.IO) {
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getFavoriteLibraries()
            if (!response.isSuccess() || response.data == null) {
                throw IllegalStateException(response.message ?: "收藏夹加载失败")
            }
            response.data!!.functions?.filterNotNull().orEmpty()
        }

    private suspend fun migrateLegacyFavorites(
        context: ClientServices,
        remote: List<LibraryFunction>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val store = context.favorites
            val legacy = store.pendingServerMigration()
            if (legacy.isEmpty()) {
                store.markServerMigrationComplete()
                return@withContext false
            }

            val remoteUuids = remote.mapNotNullTo(HashSet()) { it.uuid }
            val remoteIds = remote.mapNotNullTo(HashSet()) { it.id }
            var changed = false
            for (bookmark in legacy) {
                val alreadyRemote =
                    bookmark.uuid?.let(remoteUuids::contains) == true ||
                        bookmark.id?.let(remoteIds::contains) == true
                if (alreadyRemote) continue

                val id = bookmark.id ?: return@withContext false
                val result = ServiceManager.COMMAND_LAB_USER_SERVICE.toggleFavorite(id)
                if (!result.isSuccess() || result.data?.isFavorited != true) {
                    return@withContext false
                }
                changed = true
            }
            store.markServerMigrationComplete()
            changed
        }
}
