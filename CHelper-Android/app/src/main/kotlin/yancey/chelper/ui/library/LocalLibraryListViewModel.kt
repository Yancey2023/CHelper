/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.util.CloudLibraryCache
import yancey.chelper.network.library.util.LoginUtil

class LocalLibraryListViewModel : ViewModel() {
    var keyword by mutableStateOf(TextFieldState())
    var isShowImportDialog by mutableStateOf(false)
    var isShowExportDialog by mutableStateOf(false)

    var isLoggedIn by mutableStateOf(false)
    var currentUser by mutableStateOf<CommandLabUserService.User?>(null)

    // 公开资料：拿 userTitle / 准确 tier 用，登录返回的 User 里没有 user_title
    var userProfile by mutableStateOf<UserProfileData?>(null)

    // 私有库容量：used / limit，用于显示容量条
    var quotaUsed by mutableStateOf<Int?>(null)
    var quotaLimit by mutableStateOf<Int?>(null)

    val myCloudLibraries = mutableStateListOf<LibraryFunction>()
    var isCloudLibrariesLoading by mutableStateOf(false)
    var cloudLibrariesPage by mutableIntStateOf(1)
    var cloudLibrariesHasMore by mutableStateOf(true)

    var uploadingLibraryIndex by mutableIntStateOf(-1)

    init {
        refreshUserState()
    }

    fun refreshUserState(forceRefresh: Boolean = false) {
        isLoggedIn = LoginUtil.isLoggedIn
        currentUser = LoginUtil.currentUser
        if (isLoggedIn) {
            val uid = currentUser?.id
            if (uid != null && !forceRefresh) {
                // 先把缓存灌进 UI，让短时间回访（切 tab、详情返回）零等待
                CloudLibraryCache.getLibraries(uid)?.let { cached ->
                    myCloudLibraries.clear()
                    myCloudLibraries.addAll(cached)
                    // 命中缓存就当作"已经把第一页加载完"，后续滚动再分页
                    cloudLibrariesPage = 1
                    cloudLibrariesHasMore = false
                }
                CloudLibraryCache.getProfile(uid)?.let { userProfile = it }
                CloudLibraryCache.getQuota(uid)?.let {
                    quotaUsed = it.used
                    quotaLimit = it.limit
                }
            }
            // 缓存没命中（或强制刷新）才走网络。命中后下面三个调用各自会自己判一次
            loadCloudLibraries(refresh = true, forceRefresh = forceRefresh)
            loadUserProfile(forceRefresh = forceRefresh)
            loadQuota(forceRefresh = forceRefresh)
        } else {
            myCloudLibraries.clear()
            userProfile = null
            quotaUsed = null
            quotaLimit = null
        }
    }

    /**
     * 右上角"账户菜单 → 退出登录"用的快捷出口。
     * 实际清除登录信息后立刻 refreshUserState，让"我的库"页面在原地切回未登录视图，
     * 不再让用户为了登出多跳一次 CPLUserScreen
     */
    fun logout() {
        LoginUtil.logout()
        // 登出立刻清掉用户身份相关的缓存，避免下个登录用户看到上一个人的私有库
        CloudLibraryCache.invalidateAll()
        refreshUserState()
    }

    /**
     * 拉取自身的公开资料，主要为了拿 userTitle（称号）。
     * 登录返回里没带这个字段，需要单独查一次。
     */
    private fun loadUserProfile(forceRefresh: Boolean = false) {
        val uid = currentUser?.id ?: return
        if (!forceRefresh && CloudLibraryCache.getProfile(uid) != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getUserProfile(uid)
                if (response.isSuccess()) {
                    withContext(Dispatchers.Main) {
                        userProfile = response.data
                        CloudLibraryCache.setProfile(uid, response.data)
                    }
                }
            } catch (_: Exception) {
                // 资料拉不到不影响主流程，静默
            }
        }
    }

    /**
     * 拉取私有库容量配额，给"我的云端库"下方的容量条用。
     */
    private fun loadQuota(forceRefresh: Boolean = false) {
        val uid = currentUser?.id ?: return
        if (!forceRefresh && CloudLibraryCache.getQuota(uid) != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getQuota()
                if (response.isSuccess()) {
                    withContext(Dispatchers.Main) {
                        quotaUsed = response.data?.used
                        quotaLimit = response.data?.limit
                        CloudLibraryCache.setQuota(uid, response.data?.used, response.data?.limit)
                    }
                }
            } catch (_: Exception) {
                // 同上，容量拉不到也不挡用户
            }
        }
    }

    fun loadCloudLibraries(refresh: Boolean = false, forceRefresh: Boolean = false) {
        if (!isLoggedIn) return
        val uid = currentUser?.id
        // refresh=true 是"重置到第一页"，但如果缓存还热，就完全跳过这次拉取
        if (refresh && uid != null && !forceRefresh && CloudLibraryCache.getLibraries(uid) != null) {
            return
        }
        if (refresh) {
            cloudLibrariesPage = 1
            myCloudLibraries.clear()
            cloudLibrariesHasMore = true
        }
        if (!cloudLibrariesHasMore) return

        isCloudLibrariesLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getMyLibraries(
                    pageNum = cloudLibrariesPage
                )
                withContext(Dispatchers.Main) {
                    isCloudLibrariesLoading = false
                    if (response.isSuccess()) {
                        val data = response.data
                        if (data != null && !data.functions.isNullOrEmpty()) {
                            val existingIds = myCloudLibraries.mapNotNull { it.id }.toSet()
                            data.functions!!.filterNotNull()
                                .filter { it.id != null && it.id !in existingIds }
                                .forEach { myCloudLibraries.add(it) }
                            if (data.currentPage != null && data.totalCount != null && data.perPage != null) {
                                val totalPages =
                                    (data.totalCount!! + data.perPage!! - 1) / data.perPage!!
                                cloudLibrariesHasMore = data.currentPage!! < totalPages
                                if (cloudLibrariesHasMore) cloudLibrariesPage++
                            }
                            // 只缓存"第一页结果"作为快照——再往后翻页是用户主动行为，
                            // 没必要全量缓存做无意义的持久膨胀
                            if (uid != null && cloudLibrariesPage <= 2) {
                                CloudLibraryCache.setLibraries(uid, myCloudLibraries.toList())
                            }
                        } else {
                            cloudLibrariesHasMore = false
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCloudLibrariesLoading = false
                }
            }
        }
    }

    fun uploadToCloud(
        library: LibraryFunction,
        localIndex: Int,
        localDataStore: LocalCommandLabDataStore? = null
    ) {
        if (!isLoggedIn) {
            Toaster.show("请先登录")
            return
        }
        uploadingLibraryIndex = localIndex
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mcdContent = buildFullMCD(library)
                val request = CommandLabUserService.UploadLibraryRequest().apply {
                    content = mcdContent
                    isPublish = false
                }
                val result = ServiceManager.COMMAND_LAB_USER_SERVICE.uploadLibrary(request)
                withContext(Dispatchers.Main) {
                    uploadingLibraryIndex = -1
                    if (result.isSuccess()) {
                        Toaster.show("上传成功")
                        // 上传成功：把后端分配的 uuid 写回本地副本，并清掉未同步标记
                        // 之后用户在本地再编辑才会把 flag 变回 true，符合预期的同步语义
                        val assignedUuid = result.data?.uuid
                        if (localDataStore != null && localIndex >= 0) {
                            val updated = library.apply {
                                if (!assignedUuid.isNullOrEmpty()) uuid = assignedUuid
                                localUnsynced = false
                            }
                            launch(Dispatchers.IO) {
                                localDataStore.updateLocalLibraryFunction(localIndex, updated)
                            }
                        }
                        // 云端列表变了，让缓存失效，loadCloudLibraries 才会真正去拉
                        CloudLibraryCache.invalidateLibraries()
                        loadCloudLibraries(refresh = true, forceRefresh = true)
                    } else {
                        Toaster.show(result.message ?: "上传失败")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadingLibraryIndex = -1
                    Toaster.show("网络错误: ${e.message}")
                }
            }
        }
    }

    fun deleteCloudLibrary(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ServiceManager.COMMAND_LAB_USER_SERVICE.deleteLibrary(id)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess()) {
                        myCloudLibraries.removeAll { it.id == id }
                        // 缓存里也同步移除，避免下一次回访又把旧条目灌回来
                        val uid = currentUser?.id
                        if (uid != null) {
                            CloudLibraryCache.setLibraries(uid, myCloudLibraries.toList())
                        }
                        Toaster.show("删除成功")
                    } else {
                        Toaster.show("删除失败: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.show("网络错误: ${e.message}")
                }
            }
        }
    }

    companion object {
        fun buildFullMCD(library: LibraryFunction): String {
            val mcdBuilder = StringBuilder()
            mcdBuilder.append("@name=${library.name ?: ""}\n")
            mcdBuilder.append("@version=${library.version?.ifEmpty { "1.0.0" } ?: "1.0.0"}\n")

            if (!library.tags.isNullOrEmpty()) {
                mcdBuilder.append("@tags=${library.tags!!.joinToString(",")}\n")
            }

            mcdBuilder.append("@note=${library.note ?: ""}\n")
            if (library.content?.contains("@mcd_version=2") == true) {
                mcdBuilder.append("@mcd_version=2\n")
            }
            if (!library.uuid.isNullOrEmpty()) {
                mcdBuilder.append("@uuid=${library.uuid}\n")
            }
            mcdBuilder.append("\n###Function###\n")
            mcdBuilder.append(library.content ?: "")
            mcdBuilder.append("\n###End###")
            return mcdBuilder.toString()
        }
    }
}
