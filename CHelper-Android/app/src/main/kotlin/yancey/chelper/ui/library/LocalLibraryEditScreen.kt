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

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.util.CloudLibraryCache
import yancey.chelper.network.library.util.LoginUtil
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.SettingsItem
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField
import java.util.UUID

@SuppressLint("UseKtx")
@Composable
fun LocalLibraryEditScreen(viewModel: LocalLibraryEditViewModel = viewModel(), id: Int? = null) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { LocalCommandLabDataStore(context) }
    val localLibraryFunction by localCommandLabDataStore.localLibraryFunction(id)
        .collectAsState(initial = null)
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    viewModel.ensureEditingTarget(id, localLibraryFunction)

    RootViewWithHeaderAndCopyright(
        title = when (viewModel.mode) {
            EditMode.ADD -> stringResource(R.string.layout_library_edit_title_add)
            EditMode.UPDATE -> stringResource(R.string.layout_library_edit_title_edit)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 可滚动主体
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---------- 基础信息卡 ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "基础信息",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.textMain
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        state = viewModel.name,
                        hint = stringResource(R.string.upload_field_name),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        state = viewModel.description,
                        hint = stringResource(R.string.upload_field_description),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextField(
                            state = viewModel.version,
                            hint = stringResource(R.string.upload_field_version),
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            state = viewModel.tags,
                            hint = stringResource(R.string.upload_field_tags),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------- 脚本卡 ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "执行脚本",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CHelperTheme.colors.textMain
                            )
                        )
                        // V2 才有"低代码补全"。V1 没有命令链/方块状态概念，按钮纯多余
                        if (viewModel.useV2) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE65100).copy(alpha = 0.1f))
                                    .clickable { viewModel.isShowLowCodeHelper = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    id = R.drawable.pencil,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "低代码补全 V2",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE65100)
                                    )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    TextField(
                        state = viewModel.commands,
                        hint = stringResource(
                            if (viewModel.useV2) R.string.upload_field_commands_v2
                            else R.string.upload_field_commands_v1
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        contentAlignment = Alignment.TopStart
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://abyssous.site/wiki".toUri()
                                    )
                                    context.startActivity(intent)
                                }
                                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                id = R.drawable.book,
                                contentDescription = stringResource(R.string.upload_wiki_link),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.upload_wiki_link),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "启用 V2 语法",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = CHelperTheme.colors.textSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = viewModel.useV2,
                                onCheckedChange = { newValue ->
                                    // 关 V2 等于把已有的命令链可视化和方块状态全降级回纯命令列表，
                                    // 跟云端编辑界面一样要弹一次确认，避免误触
                                    if (!newValue && viewModel.useV2) {
                                        viewModel.isShowV2DowngradeConfirm = true
                                    } else {
                                        viewModel.useV2 = newValue
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------- 同步开关 ----------
                // SettingsItem 跟设置页同款，提示更显眼，明确告诉用户开关代价
                SettingsItem(
                    name = "自动生成 UUID 并同步",
                    description = "保存时自动调用云端接口：本地库未绑定云端就建一条草稿（由云端分配 UUID），已绑定的直接更新",
                    checked = viewModel.autoSync,
                    onCheckedChange = { viewModel.autoSync = it }
                )

                Spacer(Modifier.height(16.dp))
            }

            // ---------- 底部操作区 ----------
            val saveLabel = when {
                viewModel.isSyncing -> "同步中…"
                viewModel.autoSync -> "保存并同步到云端"
                else -> stringResource(R.string.layout_library_edit_save)
            }
            // Compose Lint 不让在 onClick 闭包里走 context.getString —— Configuration 变更
            // 不会触发 LocalContext 重读，会拿到旧值。提前在 Composable 上下文里抓一份字符串
            val emptyErrorText = stringResource(R.string.upload_empty_error)
            Button(
                text = saveLabel,
                onClick = {
                    if (viewModel.isSyncing) return@Button
                    if (viewModel.name.text.isBlank() || viewModel.commands.text.isBlank()) {
                        Toaster.show(emptyErrorText)
                        return@Button
                    }
                    saveLocalLibrary(
                        viewModel = viewModel,
                        editingId = id,
                        existingLibrary = localLibraryFunction,
                        localDataStore = localCommandLabDataStore,
                        onDone = { onBackPressedDispatcher?.onBackPressed() }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (viewModel.mode == EditMode.UPDATE) {
                Spacer(Modifier.height(10.dp))
                Button(stringResource(R.string.layout_library_edit_delete)) {
                    viewModel.isShowDeleteDialog = true
                }
            }
        }
    }

    if (viewModel.isShowDeleteDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowDeleteDialog = false },
            content = stringResource(R.string.layout_library_edit_is_confirm_delete),
            onConfirm = {
                viewModel.viewModelScope.launch {
                    localCommandLabDataStore.removeLocalLibraryFunction(id!!)
                    onBackPressedDispatcher?.onBackPressed()
                }
            }
        )
    }

    if (viewModel.isShowLowCodeHelper) {
        LowCodeV2HelperDialog(
            rawContent = viewModel.commands.text.toString(),
            onDismiss = { viewModel.isShowLowCodeHelper = false },
            onApply = { newContent ->
                viewModel.commands.setTextAndPlaceCursorAtEnd(newContent)
                viewModel.isShowLowCodeHelper = false
                Toaster.show("已应用标记！")
            }
        )
    }

    if (viewModel.isShowV2DowngradeConfirm) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowV2DowngradeConfirm = false },
            title = "切换到 V1 语法",
            content = "V1 语法的渲染效果远低于 V2，不支持命令链可视化和状态标记。确定要降级吗？",
            confirmText = "确定降级",
            onConfirm = {
                viewModel.useV2 = false
                viewModel.isShowV2DowngradeConfirm = false
            }
        )
    }
}

/**
 * 保存本地库，可选附加云端同步。
 *
 * 流程：
 * 1. 拼好新的 LibraryFunction（保留 id / uuid / 旧元数据），写本地。
 * 2. autoSync 开 → 进入云端分支：
 *    - 已有云端 id：调 updateLibrary
 *    - 没云端 id：先客户端生成一个 uuid 兜底（防后端"新建走默认 uuid"），
 *      调 uploadLibrary，把返回的 uuid + 没绑过 id 的本地副本重新写一遍。
 * 3. 同步成功清掉 localUnsynced 标记，失败保留 true 让列表看得见。
 *
 * 失败不阻断本地保存——用户写的脚本不会因为网络问题丢。
 */
private fun saveLocalLibrary(
    viewModel: LocalLibraryEditViewModel,
    editingId: Int?,
    existingLibrary: LibraryFunction?,
    localDataStore: LocalCommandLabDataStore,
    onDone: () -> Unit
) {
    viewModel.viewModelScope.launch {
        val tagList = viewModel.tags.text.toString()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val newLibrary = LibraryFunction().apply {
            // 还原属性，避免擦掉云端字段
            this.id = existingLibrary?.id
            uuid = existingLibrary?.uuid
            createdAt = existingLibrary?.createdAt
            likeCount = existingLibrary?.likeCount
            isLiked = existingLibrary?.isLiked
            hasPublicVersion = existingLibrary?.hasPublicVersion
            isPublish = existingLibrary?.isPublish
            isOwner = existingLibrary?.isOwner
            chainData = existingLibrary?.chainData

            name = viewModel.name.text.toString()
            version = viewModel.version.text.toString()
            author = existingLibrary?.author
            note = viewModel.description.text.toString()
            tags = tagList
            content = viewModel.commands.text.toString()
            autoSync = viewModel.autoSync
            // 已经绑过云端、本地又有新改动 → 标"本地未同步"
            // 纯本地草稿（没 uuid）保持默认 false
            localUnsynced = !existingLibrary?.uuid.isNullOrEmpty()
        }

        // 先写本地，保证用户的脚本一定落盘
        val savedIndex: Int = when (viewModel.mode) {
            EditMode.ADD -> {
                localDataStore.addLocalLibraryFunction(newLibrary)
                // addLocalLibraryFunction 没回 index。按追加约定取末位——拿一次最新快照即可，
                // 后续 syncToCloud 的回写需要这个 index
                localDataStore.localLibraryFunctions().first().lastIndex
            }

            EditMode.UPDATE -> {
                localDataStore.updateLocalLibraryFunction(editingId!!, newLibrary)
                editingId
            }
        }

        if (!viewModel.autoSync) {
            onDone()
            return@launch
        }

        // 进入云端同步分支
        if (!LoginUtil.isLoggedIn) {
            Toaster.show("尚未登录，已仅保存到本地")
            onDone()
            return@launch
        }

        viewModel.isSyncing = true
        try {
            // 给"还没绑定云端"的本地库提前生成一个 uuid，
            // 这样云端接口里 @uuid 头是稳定的，下次再保存还能命中同一条
            val fallbackUuid = newLibrary.uuid?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString()
            val mcd = viewModel.buildFullMCD(existingLibrary, fallbackUuid)

            val syncSucceeded: Boolean = withContext(Dispatchers.IO) {
                if (newLibrary.id != null) {
                    // 已绑云端 id：走 update
                    val req = CommandLabUserService.UpdateLibraryRequest().apply {
                        this.name = newLibrary.name
                        this.version = newLibrary.version?.ifEmpty { "1.0.0" } ?: "1.0.0"
                        this.note = newLibrary.note
                        this.tags = newLibrary.tags ?: emptyList()
                        this.content = mcd
                    }
                    val result =
                        ServiceManager.COMMAND_LAB_USER_SERVICE.updateLibrary(newLibrary.id!!, req)
                    result.isSuccess()
                } else {
                    // 没云端 id：调 upload，让后端建一条草稿；后端会回 uuid（理论上和 fallback 一致）
                    val req = CommandLabUserService.UploadLibraryRequest().apply {
                        content = mcd
                        isPublish = false
                    }
                    val result = ServiceManager.COMMAND_LAB_USER_SERVICE.uploadLibrary(req)
                    if (result.isSuccess()) {
                        val assignedUuid = result.data?.uuid?.takeIf { it.isNotEmpty() }
                            ?: fallbackUuid
                        // 把云端分配的 uuid 写回本地。这次不知道云端 id（upload 没返回），
                        // 下次进"我的库" → loadCloudLibraries 回来时会按 uuid 比对补齐
                        if (savedIndex >= 0) {
                            val rewrite = newLibrary.apply {
                                uuid = assignedUuid
                                localUnsynced = false
                            }
                            localDataStore.updateLocalLibraryFunction(savedIndex, rewrite)
                        }
                        true
                    } else {
                        false
                    }
                }
            }

            if (syncSucceeded) {
                // update 路径：清掉本地未同步标记
                if (newLibrary.id != null && savedIndex >= 0) {
                    val cleared = newLibrary.apply { localUnsynced = false }
                    localDataStore.updateLocalLibraryFunction(savedIndex, cleared)
                }
                CloudLibraryCache.invalidateLibraries()
                Toaster.show("已同步到云端")
            } else {
                Toaster.show("云端同步失败，已保留本地副本")
            }
        } catch (e: Exception) {
            Toaster.show("云端同步异常：${e.message}")
        } finally {
            viewModel.isSyncing = false
            onDone()
        }
    }
}

@Preview
@Composable
fun LocalLibraryEditScreenScreenLightThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryEditScreen()
    }
}

@Preview
@Composable
fun LocalLibraryEditScreenDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryEditScreen()
    }
}
