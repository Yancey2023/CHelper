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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.SiteMessage

class MessageViewModel : ViewModel() {
    var messages: SnapshotStateList<SiteMessage> = mutableStateListOf()
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var currentPage by mutableIntStateOf(1)
    var totalCount by mutableIntStateOf(0)
    var hasMore by mutableStateOf(true)

    /** 未读小红点计数，可在外部（如用户中心）直接观察 */
    var unreadCount by mutableIntStateOf(0)

    var showUnreadOnly by mutableStateOf(false)

    private var hasLoaded = false

    fun loadMessages(resetPage: Boolean = true) {
        if (isLoading) return
        if (resetPage && hasLoaded && messages.isNotEmpty()) return
        hasLoaded = true

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            if (resetPage) {
                currentPage = 1
                messages.clear()
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE.getMessages(
                        page = currentPage,
                        pageSize = 20,
                        unreadOnly = showUnreadOnly
                    )
                }

                if (response.isSuccess() && response.data != null) {
                    val data = response.data!!
                    val items = data.messages ?: emptyList()
                    messages.addAll(items)

                    totalCount = data.total ?: 0
                    val pageSize = data.pageSize ?: 20
                    val totalPages = if (pageSize > 0) (totalCount + pageSize - 1) / pageSize else 1
                    hasMore = currentPage < totalPages
                } else {
                    errorMessage = response.message ?: "加载失败"
                }
            } catch (e: Exception) {
                errorMessage = "网络错误: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (!hasMore || isLoading) return
        currentPage++
        loadMessages(resetPage = false)
    }

    fun refresh() {
        hasLoaded = false
        loadMessages(resetPage = true)
    }

    fun refreshUnreadCount() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE.getUnreadCount()
                }
                if (result.isSuccess() && result.data != null) {
                    unreadCount = result.data!!.count ?: 0
                }
            } catch (_: Exception) {
                // 静默失败，不影响主流程
            }
        }
    }

    fun markAsRead(messageId: Int) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE.markMessageRead(messageId)
                }
                if (result.isSuccess()) {
                    // 本地同步状态，避免重新拉取整个列表
                    val idx = messages.indexOfFirst { it.id == messageId }
                    if (idx >= 0) {
                        val old = messages[idx]
                        if (old.isRead != true) {
                            messages[idx] = SiteMessage(
                                id = old.id, title = old.title, content = old.content,
                                msgType = old.msgType, senderId = old.senderId,
                                createdAt = old.createdAt, isRead = true,
                                readAt = old.readAt, isGlobal = old.isGlobal
                            )
                            if (unreadCount > 0) unreadCount--
                        }
                    }
                }
            } catch (_: Exception) {
                // 静默
            }
        }
    }

    fun deleteMessage(messageId: Int) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ServiceManager.COMMAND_LAB_USER_SERVICE.deleteMessage(messageId)
                }
                if (result.isSuccess()) {
                    val idx = messages.indexOfFirst { it.id == messageId }
                    if (idx >= 0) {
                        val wasUnread = messages[idx].isRead != true
                        messages.removeAt(idx)
                        if (wasUnread && unreadCount > 0) unreadCount--
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
