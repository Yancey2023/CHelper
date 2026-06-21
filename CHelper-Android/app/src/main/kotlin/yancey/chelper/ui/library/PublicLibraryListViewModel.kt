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

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction

class PublicLibraryListViewModel : ViewModel() {
    var libraries: SnapshotStateList<LibraryFunction> = mutableStateListOf()

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var currentPage by mutableIntStateOf(1)
    var totalPages by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    private var forceRefresh = false
    var isRecommendMode by mutableStateOf(false)
    var currentLoadedMode: Boolean? = null

    // 缓存两种模式下的数据状态
    private val recommendCache = mutableListOf<LibraryFunction>()
    private val latestCache = mutableListOf<LibraryFunction>()
    private var recommendCurrentPage = 1
    private var latestCurrentPage = 1
    private var recommendTotalPages = 1
    private var latestTotalPages = 1
    private var recommendHasMore = true
    private var latestHasMore = true

    private var searchJob: Job? = null

    fun switchMode(isRecommend: Boolean) {
        Log.d(
            "CPL_Tab",
            "switchMode called: isRecommend=$isRecommend, currentLoadedMode=$currentLoadedMode, isRecommendMode=$isRecommendMode"
        )
        // 保存当前模式的状态
        saveCurrentState()

        isRecommendMode = isRecommend
        currentLoadedMode = isRecommend
        val cache = if (isRecommend) recommendCache else latestCache
        val page = if (isRecommend) recommendCurrentPage else latestCurrentPage
        val total = if (isRecommend) recommendTotalPages else latestTotalPages
        val more = if (isRecommend) recommendHasMore else latestHasMore

        Log.d(
            "CPL_Tab",
            "cache size: ${cache.size}, recommendCache: ${recommendCache.size}, latestCache: ${latestCache.size}"
        )
        if (cache.isNotEmpty()) {
            // 使用缓存数据
            Log.d("CPL_Tab", "Using cached data, libraries count: ${cache.size}")
            libraries.clear()
            libraries.addAll(cache)
            currentPage = page
            totalPages = total
            hasMore = more
        } else {
            // 缓存为空，先取消正在进行的加载，再刷新
            // 否则 isLoading=true 会拦住 refresh -> loadFunctions
            Log.d("CPL_Tab", "Cache empty, cancelling current job and calling refresh")
            searchJob?.cancel()
            isLoading = false
            refresh(isRecommend = isRecommend)
        }
    }

    private fun saveCurrentState() {
        if (currentLoadedMode == null) return
        val isCurrentRecommend = currentLoadedMode == true
        val cache = if (isCurrentRecommend) recommendCache else latestCache
        cache.clear()
        cache.addAll(libraries)

        if (isCurrentRecommend) {
            recommendCurrentPage = currentPage
            recommendTotalPages = totalPages
            recommendHasMore = hasMore
        } else {
            latestCurrentPage = currentPage
            latestTotalPages = totalPages
            latestHasMore = hasMore
        }
    }

    fun loadFunctions(
        search: String? = null,
        resetPage: Boolean = true,
        isRecommend: Boolean = false
    ) {
        Log.d(
            "CPL_Tab",
            "loadFunctions: search=$search, resetPage=$resetPage, isRecommend=$isRecommend, isLoading=$isLoading, libraries.size=${libraries.size}, forceRefresh=$forceRefresh"
        )
        if (isLoading) {
            Log.d("CPL_Tab", "loadFunctions blocked: isLoading=true")
            return
        }
        // 如果已经有数据且不是用户手动刷新，跳过重复拉取
        if (resetPage && libraries.isNotEmpty() && !forceRefresh) {
            Log.d(
                "CPL_Tab",
                "loadFunctions blocked: already has data and not force refresh"
            )
            return
        }
        forceRefresh = false

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null

            if (resetPage) {
                currentPage = 1
                libraries.clear()
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    if (isRecommend && search.isNullOrBlank()) {
                        // 推荐接口本身就是随机抽样，不传 pageNum/pageSize；
                        // loadMore 等同于"再洗一批"追加，避免分页带来的重复
                        ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getRecommendedLibrary(limit = 15)
                    } else {
                        ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getFunctions(
                            pageNum = currentPage,
                            pageSize = 20,
                            keyword = search?.takeIf { it.isNotBlank() }
                        )
                    }
                }

                if (response.isSuccess() && response.data != null) {
                    val data = response.data!!
                    val functions = data.functions?.filterNotNull() ?: emptyList()
                    // 推荐接口随机抽样，loadMore 拉来的新批次很可能与已有项 id 重复；
                    // 不去重 LazyColumn 会撞 key 抛 IllegalArgumentException 闪退。
                    // 同时兜底单次响应内自带重复的情况。
                    val seenIds = libraries.mapNotNullTo(HashSet()) { it.id }
                    val deduped = functions.filter { fn ->
                        val id = fn.id ?: return@filter true
                        seenIds.add(id)
                    }
                    libraries.addAll(deduped)

                    val total = data.totalCount ?: 0
                    val size = data.perPage ?: 20
                    // Calculate total pages: ceil(total / size)
                    totalPages = if (size > 0) (total + size - 1) / size else 1

                    // 推荐模式以"去重后还能添加"作为继续加载的依据：
                    // 若某次洗出来全是已见过的项，说明库存基本耗尽，停止 loadMore
                    hasMore = if (isRecommend) {
                        deduped.isNotEmpty()
                    } else {
                        currentPage < totalPages
                    }
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

    fun loadMore(isRecommend: Boolean = false) {
        if (!hasMore || isLoading) return
        // 推荐接口不分页，直接再洗一批追加；普通列表才推进页码
        if (!isRecommend) {
            currentPage++
        }
        loadFunctions(null, resetPage = false, isRecommend = isRecommend)
    }

    fun refresh(isRecommend: Boolean = false) {
        Log.d("CPL_Tab", "refresh called: isRecommend=$isRecommend")
        forceRefresh = true
        loadFunctions(null, resetPage = true, isRecommend = isRecommend)
    }
}
