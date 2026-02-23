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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import yancey.chelper.android.common.util.LocalLibraryManager
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction

class LibrarySearchViewModel : ViewModel() {
    val keyword = TextFieldState()
    
    // 第一栏：来自本地和私有云端库的匹配项
    var localAndPrivateLibraries: SnapshotStateList<LibraryFunction> = mutableStateListOf()
    
    // 第二栏：来自公开市场库的匹配项
    var publicLibraries: SnapshotStateList<LibraryFunction> = mutableStateListOf()
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    // 公开市场库的分页参数
    var currentPage by mutableIntStateOf(1)
    var totalPages by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)
    
    private var searchJob: Job? = null
    
    fun setInitialKeyword(initialWord: String?) {
        if (!initialWord.isNullOrBlank()) {
            keyword.setTextAndPlaceCursorAtEnd(initialWord)
        }
    }

    fun search() {
        val q = keyword.text.toString().trim()
        if (q.isEmpty()) {
            // 清空搜索结果
            localAndPrivateLibraries.clear()
            publicLibraries.clear()
            currentPage = 1
            hasMore = false
            return
        }
        
        loadFunctions(q, resetPage = true)
    }

    fun loadMore() {
        val q = keyword.text.toString().trim()
        if (q.isEmpty() || !hasMore || isLoading) return
        currentPage++
        loadFunctions(q, resetPage = false)
    }

    private fun loadFunctions(searchStr: String, resetPage: Boolean) {
        if (isLoading) return
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            if (resetPage) {
                currentPage = 1
                localAndPrivateLibraries.clear()
                publicLibraries.clear()
            }
            
            try {
                // 并行执行两部分逻辑
                withContext(Dispatchers.IO) {
                    val publicJob = launch { fetchPublicLibraries(searchStr) }
                    
                    // 私有与本地过滤只在第一页加载时查一次即可
                    if (resetPage) {
                        launch { fetchLocalAndPrivateLibraries(searchStr) }
                    }
                    
                    publicJob.join()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage = "网络错误: ${e.message}"
                if (!resetPage && currentPage > 1) {
                    currentPage--
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    /**
     * 加载第二栏数据，从远端获取市场公开库
     */
    private suspend fun fetchPublicLibraries(searchStr: String) {
        val response = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE?.getFunctions(
            pageNum = currentPage,
            pageSize = 20,
            keyword = searchStr
        )
        
        withContext(Dispatchers.Main) {
            if (response?.isSuccess() == true && response.data != null) {
                val data = response.data!!
                val functions = data.functions?.filterNotNull() ?: emptyList()
                publicLibraries.addAll(functions)

                val total = data.totalCount ?: 0
                val size = data.perPage ?: 20
                totalPages = if (size > 0) (total + size - 1) / size else 1
                hasMore = currentPage < totalPages
            } else {
                if (publicLibraries.isEmpty()) {
                    errorMessage = response?.message ?: "加载公开库失败"
                }
            }
        }
    }
    
    /**
     * 加载第一栏数据，从本地和云端私有库过滤
     */
    private suspend fun fetchLocalAndPrivateLibraries(searchStr: String) {
        val searchLower = searchStr.lowercase()
        val tempMatches = mutableListOf<LibraryFunction>()
        
        // 扫描本地库 (转换为 LibraryFunction 做格式统一，我们借用 name, note 等字段)
        LocalLibraryManager.INSTANCE?.ensureInit()
        val localList = LocalLibraryManager.INSTANCE?.getFunctions()
        localList?.forEach { localFunc ->
            val nameMatch = localFunc.name?.lowercase()?.contains(searchLower) == true
            val tagsMatch = localFunc.tags?.any { it.lowercase().contains(searchLower) } == true
            val noteMatch = localFunc.note?.lowercase()?.contains(searchLower) == true
            if (nameMatch || tagsMatch || noteMatch) {
                tempMatches.add(
                    LibraryFunction().apply {
                        name = localFunc.name
                        note = localFunc.note
                        tags = localFunc.tags
                        // 将本地包 ID 转为负数，防止和远端冲突，并在 UI 中做区分
                        id = -(localFunc.id ?: 0)
                        author = "[本地包]"
                        version = localFunc.version
                    }
                )
            }
        }
        
        // 2. 扫描云端私有库
        try {
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE?.getMyLibraries()?.execute()?.body()
            if (response?.isSuccess() == true && response.data != null) {
                val privateList = response.data!!.functions?.filterNotNull() ?: emptyList()
                privateList.forEach { privateFunc ->
                    val nameMatch = privateFunc.name?.lowercase()?.contains(searchLower) == true
                    val tagsMatch = privateFunc.tags?.any { it.lowercase().contains(searchLower) } == true
                    val noteMatch = privateFunc.note?.lowercase()?.contains(searchLower) == true
                    
                    if (nameMatch || tagsMatch || noteMatch) {
                        privateFunc.author = "[我的私有库]" // 标记
                        tempMatches.add(privateFunc)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        }
        
        withContext(Dispatchers.Main) {
            localAndPrivateLibraries.addAll(tempMatches)
        }
    }
}
