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

package yancey.chelper.ui.feature.library.messages

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

class MessageViewModel(
    private val inbox: MessageRepository,
    private val links: BrowserLinkRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    var messages by mutableStateOf<List<InboxMessage>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableIntStateOf(1)
        private set

    var totalCount by mutableIntStateOf(0)
        private set

    var hasMore by mutableStateOf(true)
        private set

    var unreadCount by mutableIntStateOf(0)
        private set

    var showUnreadOnly by mutableStateOf(false)
    var pendingBrowserUrl by mutableStateOf<String?>(null)
    var linkMessage by mutableStateOf<String?>(null)
    var isResolvingLink by mutableStateOf(false)
        private set

    private var loaded = false
    private var generation = 0L
    private var loadJob: Job? = null
    private var unreadJob: Job? = null
    private val changing = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            var revision = accounts.state.value.revision
            accounts.state.collect { account ->
                if (revision != account.revision) {
                    revision = account.revision
                    generation++
                    loadJob?.cancel()
                    unreadJob?.cancel()
                    messages = emptyList()
                    unreadCount = 0
                    totalCount = 0
                    currentPage = 1
                    loaded = false
                    hasMore = true
                    isLoading = false
                }
            }
        }
    }

    fun loadMessages(resetPage: Boolean = true) {
        if (resetPage && loaded) return
        if (!resetPage && (isLoading || !hasMore)) return
        if (resetPage) loadJob?.cancel()
        val version = ++generation
        val page = if (resetPage) 1 else currentPage + 1
        val unreadOnly = showUnreadOnly
        isLoading = true
        errorMessage = null
        loadJob = viewModelScope.launch {
            try {
                val result = inbox.load(page, 20, unreadOnly)
                ensureActive()
                if (version != generation) return@launch
                if (!result.isSuccess()) error(result.message ?: "加载失败")
                val data = result.data ?: error("站内信为空")
                messages =
                    ((if (resetPage) emptyList() else messages) + data.messages.orEmpty())
                        .distinctBy { it.id }
                totalCount = data.total ?: messages.size
                val size = (data.pageSize ?: 20).coerceAtLeast(1)
                hasMore =
                    data.total?.let { page.toLong() * size < it }
                        ?: (data.messages.orEmpty().size >= size)
                currentPage = page
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载失败"
            } finally {
                if (version == generation) isLoading = false
            }
        }
    }

    fun loadMore() = loadMessages(false)

    fun refresh() {
        loaded = false
        loadMessages(true)
    }

    fun refreshUnreadCount() {
        unreadJob?.cancel()
        unreadJob = viewModelScope.launch {
            try {
                val result = inbox.unreadCount()
                ensureActive()
                if (result.isSuccess()) unreadCount = result.data?.count ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun openMessageLink(url: String) {
        if (isResolvingLink) return
        isResolvingLink = true
        viewModelScope.launch {
            try {
                links
                    .resolveBrowserUrl(url)
                    .onSuccess { pendingBrowserUrl = it }
                    .onFailure { linkMessage = it.message ?: "无法打开链接" }
            } catch (e: CancellationException) {
                throw e
            } finally {
                isResolvingLink = false
            }
        }
    }

    fun markAsRead(messageId: Int) {
        if (!changing.add(messageId)) return
        viewModelScope.launch {
            try {
                val result = inbox.markRead(messageId)
                if (!result.isSuccess()) error(result.message ?: "标记已读失败")
                if (messages.any { it.id == messageId && it.isRead != true })
                    unreadCount = (unreadCount - 1).coerceAtLeast(0)
                messages = messages.map { if (it.id == messageId) it.copy(isRead = true) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "操作失败"
            } finally {
                changing.remove(messageId)
            }
        }
    }

    fun deleteMessage(messageId: Int) {
        if (!changing.add(messageId)) return
        viewModelScope.launch {
            try {
                val result = inbox.delete(messageId)
                if (!result.isSuccess()) error(result.message ?: "删除失败")
                if (messages.any { it.id == messageId && it.isRead != true })
                    unreadCount = (unreadCount - 1).coerceAtLeast(0)
                messages = messages.filterNot { it.id == messageId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "操作失败"
            } finally {
                changing.remove(messageId)
            }
        }
    }
}
