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

package yancey.chelper.ui.library.score

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LeaderboardUser

class LeaderboardViewModel : ViewModel() {
    var leaderboard by mutableStateOf<List<LeaderboardUser>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val res = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getLeaderboard()
                if (res.status == 0 && res.data?.leaderboard != null) {
                    leaderboard = res.data!!.leaderboard!!
                } else {
                    errorMessage = res.message ?: "拉取榜单失败"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "网络错误"
            } finally {
                isLoading = false
            }
        }
    }

    fun refresh() {
        fetchLeaderboard()
    }
}
