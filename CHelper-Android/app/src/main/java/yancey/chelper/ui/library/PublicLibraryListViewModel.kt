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

import android.provider.Settings
import androidx.compose.foundation.text.input.TextFieldState
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
    
    private var searchJob: Job? = null
    
    fun loadFunctions(search: String? = null, resetPage: Boolean = true) {
        if (isLoading) return
        
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
                    ServiceManager.COMMAND_LAB_PUBLIC_SERVICE?.getFunctions(
                        pageNum = currentPage,
                        pageSize = 20,
                        keyword = search?.takeIf { it.isNotBlank() }
                    )
                }
                
                if (response?.isSuccess() == true && response.data != null) {
                    val data = response.data!!
                    val functions = data.functions?.filterNotNull() ?: emptyList()
                    libraries.addAll(functions)

                    val total = data.totalCount ?: 0
                    val size = data.perPage ?: 20
                    // Calculate total pages: ceil(total / size)
                    totalPages = if (size > 0) (total + size - 1) / size else 1
                    hasMore = currentPage < totalPages
                } else {
                    errorMessage = response?.message ?: "加载失败"
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
        loadFunctions(null, resetPage = false)
    }
    
    fun refresh() {
        loadFunctions(null, resetPage = true)
    }
}
