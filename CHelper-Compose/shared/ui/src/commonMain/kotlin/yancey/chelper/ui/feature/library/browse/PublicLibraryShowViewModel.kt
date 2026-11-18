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

package yancey.chelper.ui.feature.library.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import yancey.chelper.data.logging.ClientLog
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.model.*
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.feature.library.mcd.McdPreview
import yancey.chelper.ui.feature.library.mcd.prepareMcdPreview
import yancey.chelper.ui.platform.ClientMessages

class PublicLibraryShowViewModel(
    private val accounts: AccountRepository,
    private val libraries: LibraryContentRepository,
    private val settings: AppSettingsRepository,
    private val preparationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var library by mutableStateOf(LibraryFunction())
    var preview by mutableStateOf<McdPreview?>(null)
        private set

    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var actionMessage by mutableStateOf<String?>(null)
    var isPrivate by mutableStateOf(false)

    // 点赞状态独立于 library 对象，防止点赞触发命令可视化的完整重绘
    var likeCount by mutableIntStateOf(0)
    var isLiked by mutableStateOf(false)
    var isFavorited by mutableStateOf(false)

    /** 删除成功后置 true，由 Screen 层观察此状态来安全执行 popBackStack */
    var deleteSuccess by mutableStateOf(false)

    /** 当前是否展示原始源码视图（false = 可视化 UI） */
    var showRawSource by mutableStateOf(false)
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var latestSettings: Settings? = null
    private var loadVersion = 0L
    private val actions = mutableMapOf<String, Job>()

    private fun launchAction(key: String, block: suspend () -> Unit) {
        if (actions[key]?.isActive == true) return
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    actions.remove(key)
                }
            }
        actions[key] = job
        job.start()
    }

    private var initializedTarget: Pair<Int, Boolean>? = null

    init {
        viewModelScope.launch {
            settings.state
                .catch { ClientLog.e("LibraryDetail", "Failed to observe preview settings", it) }
                .collect {
                    previewJob?.cancel()
                    latestSettings = it
                    updatePreview()
                }
        }
        viewModelScope.launch {
            var revision = accounts.state.value.revision
            accounts.state.collect { account ->
                if (revision != account.revision) {
                    revision = account.revision
                    actions.values.toList().forEach(Job::cancel)
                    if (isPrivate) {
                        loadVersion++
                        loadJob?.cancel()
                        previewJob?.cancel()
                        library = LibraryFunction()
                        preview = null
                        isLoading = false
                        errorMessage = "账号已切换，请重新加载"
                    }
                }
            }
        }
    }

    /** Preference changes replace the prepared document without returning to a loading screen. */
    private fun updatePreview() {
        val preferences = latestSettings ?: return
        val current = preview ?: return
        if (isLoading || current.matches(preferences)) return
        previewJob?.cancel()
        val version = loadVersion
        val source = library.content
        previewJob = viewModelScope.launch {
            try {
                val document = prepareMcdPreview(source, preferences, preparationDispatcher)
                ensureActive()
                if (version == loadVersion && !isLoading) preview = document
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ClientLog.e("LibraryDetail", "Failed to update preview", error)
            }
        }
    }

    fun ensureLoaded(id: Int, isPrivate: Boolean) {
        val target = id to isPrivate
        if (
            initializedTarget == target && (isLoading || (errorMessage == null && library.id == id))
        )
            return
        loadFunction(id, isPrivate)
    }

    fun loadFunction(id: Int, isPrivate: Boolean) {
        val target = id to isPrivate
        if (initializedTarget != target) {
            actions.values.toList().forEach(Job::cancel)
            library = LibraryFunction()
            preview = null
            deleteSuccess = false
            pendingShareUrl = null
            releasedPublicId = null
        }
        initializedTarget = target
        val version = ++loadVersion
        loadJob?.cancel()
        previewJob?.cancel()
        this.isPrivate = isPrivate
        loadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                coroutineScope {
                    val preferences = async { settings.state.first() }
                    val response = libraries.detail(id, private = isPrivate)
                    if (response.isSuccess() && response.data != null) {
                        val detail = response.data!!
                        val document =
                            prepareMcdPreview(
                                detail.content,
                                preferences.await(),
                                preparationDispatcher,
                            )
                        ensureActive()
                        if (version != loadVersion) return@coroutineScope
                        library = detail
                        preview = document
                        likeCount = detail.likeCount ?: 0
                        isLiked = detail.isLiked == true
                        isFavorited = detail.isFavorited == true
                    } else {
                        preferences.cancel()
                        errorMessage = response.message ?: "加载失败"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errorMessage = "加载失败: ${e.message}"
            } finally {
                if (version == loadVersion) {
                    isLoading = false
                    updatePreview()
                }
            }
        }
    }

    /** 切换点赞状态 调 like API 后刷新本地计数 like 接口已赞则不管、未赞则点赞 */
    fun toggleLike(id: Int) {
        launchAction("like") {
            try {
                val result = run { libraries.like(id) }
                if (result.isSuccess() && result.data != null) {
                    val likeData = result.data!!
                    likeCount = likeData.likeCount ?: likeCount
                    isLiked = likeData.isLiked == true
                    val bonus = likeData.dailyFirstLikePoints ?: likeData.pointsAwarded
                    actionMessage =
                        if (bonus != null && bonus > 0) {
                            "点赞成功 · 每日首次点赞 +${formatPoints(bonus)} PTS"
                        } else {
                            "已点赞"
                        }
                } else {
                    actionMessage = result.message ?: "操作失败"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    /** Release 成功后后端返回的公有版 ID，Screen 层观察此值来决定是否导航 */
    var releasedPublicId by mutableStateOf<Int?>(null)

    fun releaseToPublic(id: Int, specialCode: String = "") {
        launchAction("publish") {
            try {
                val result = run {
                    libraries.publish(id, specialCode)
                }
                if (result.isSuccess()) {
                    // 后端自动审核通过时返回 {"public_id": <int>}
                    val publicId = result.data?.publicId
                    releasedPublicId = publicId
                    actionMessage = result.message ?: "已提交发布申请"
                } else {
                    actionMessage = result.message ?: "操作失败"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    fun syncToPublic(id: Int) {
        launchAction("sync") {
            try {
                val result = run { libraries.syncPublic(id) }
                actionMessage =
                    if (result.isSuccess()) {
                        "已提交同步申请"
                    } else {
                        result.message ?: "同步失败"
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    fun deleteLibrary(id: Int) {
        launchAction("delete") {
            try {
                val result = run { libraries.delete(id) }
                if (result.isSuccess()) {
                    actionMessage = "删除成功"
                    deleteSuccess = true
                } else {
                    actionMessage = result.message ?: "删除失败"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    fun toggleFavorite(id: Int) {
        if (!accounts.isLoggedIn || accounts.currentUser?.isGuest == true) {
            actionMessage = "请先登录正式账号使用收藏夹"
            return
        }
        launchAction("favorite") {
            if (isPrivate) {
                actionMessage = "私有库不能加入收藏夹"
                return@launchAction
            }
            try {
                val result = run { libraries.favorite(id) }
                if (result.isSuccess() && result.data != null) {
                    isFavorited = result.data!!.isFavorited == true
                    library = library.copy(isFavorited = isFavorited)
                    actionMessage = result.message ?: if (isFavorited) "已加入收藏夹" else "已取消收藏"
                } else {
                    actionMessage = result.message ?: "收藏操作失败"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            }
        }
    }

    /** 最近一次成功生成的分享链接，由 Screen 复制/拉起系统分享后清空。 */
    var pendingShareUrl by mutableStateOf<String?>(null)
    var isSharing by mutableStateOf(false)

    /** 生成云端公开库分享链接。 默认 import_to_local=false：对方从外链进入只看到云端详情，不自动导入本地。 */
    fun createShareLink(id: Int, importToLocal: Boolean = false) {
        if (!accounts.isLoggedIn || accounts.currentUser?.isGuest == true) {
            actionMessage = "请先登录正式账号再分享"
            return
        }
        if (isPrivate || library.isPublish == false) {
            actionMessage = "仅公开库可生成分享链接"
            return
        }
        if (isSharing) return
        launchAction("share") {
            isSharing = true
            try {
                val result = run {
                    libraries.share(id = id, importToLocal = importToLocal)
                }
                if (result.isSuccess()) {
                    val url = result.data?.shareUrl?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        pendingShareUrl = url
                        actionMessage = result.message ?: "分享链接已生成"
                    } else {
                        actionMessage = "分享链接为空"
                    }
                } else {
                    actionMessage = result.message ?: "生成分享链接失败"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            } finally {
                isSharing = false
            }
        }
    }

    companion object {
        private fun formatPoints(value: Double): String {
            val whole = value.toLong()
            return if (value == whole.toDouble()) whole.toString() else value.toString()
        }
    }

    fun submitReport(report: AbuseReport) {
        launchAction("report") {
            try {
                val result = libraries.report(report)
                ClientMessages.show(result.message ?: if (result.isSuccess()) "举报已提交" else "举报失败")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show(e.message ?: "举报失败")
            }
        }
    }
}
