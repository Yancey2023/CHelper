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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hjq.toast.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.android.work.LibrarySyncScheduler
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.data.prepareAutoSync
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.util.LoginUtil
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.MenuDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.SettingsItem
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@SuppressLint("UseKtx")
@Composable
fun LocalLibraryEditScreen(
    viewModel: LocalLibraryEditViewModel = viewModel(),
    localEntryId: String? = null,
    id: Int? = null,
) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { LocalCommandLabDataStore(context) }
    val localLibraryFunction by
        if (localEntryId != null) {
                localCommandLabDataStore.localLibraryFunction(localEntryId)
            } else {
                localCommandLabDataStore.localLibraryFunction(id)
            }
            .collectAsStateWithLifecycle(initialValue = null)
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    viewModel.ensureEditingTarget(localEntryId, id, localLibraryFunction)

    LaunchedEffect(viewModel.isInitialized, viewModel.draftKey) {
        if (!viewModel.isInitialized || viewModel.draftRestored) return@LaunchedEffect
        val draft = localCommandLabDataStore.localLibraryEditDraft(viewModel.draftKey).first()
        viewModel.restoreDraft(draft)
        if (draft != null) Toaster.show("已恢复上次编辑草稿")
    }

    LaunchedEffect(viewModel.draftRestored, viewModel.draftKey) {
        if (!viewModel.draftRestored) return@LaunchedEffect
        snapshotFlow { viewModel.snapshot() }
            .collectLatest { snapshot ->
                delay(1000)
                if (!viewModel.draftWritesEnabled) return@collectLatest
                if (viewModel.isDirty) {
                    localCommandLabDataStore.saveLocalLibraryEditDraft(
                        viewModel.draftKey,
                        viewModel.toDraft(snapshot),
                    )
                } else {
                    localCommandLabDataStore.clearLocalLibraryEditDraft(viewModel.draftKey)
                }
            }
    }

    LaunchedEffect(viewModel.exitApproved) {
        if (viewModel.exitApproved) onBackPressedDispatcher?.onBackPressed()
    }

    fun requestExit() {
        when {
            viewModel.isSyncing -> Toaster.show("正在同步，请稍候")
            viewModel.isDirty -> viewModel.isShowExitConfirm = true
            else -> onBackPressedDispatcher?.onBackPressed()
        }
    }

    BackHandler(enabled = !viewModel.exitApproved && (viewModel.isDirty || viewModel.isSyncing)) {
        requestExit()
    }

    RootViewWithHeaderAndCopyright(
        title =
            when (viewModel.mode) {
                EditMode.ADD -> stringResource(R.string.layout_library_edit_title_add)
                EditMode.UPDATE -> stringResource(R.string.layout_library_edit_title_edit)
            },
        onBack = ::requestExit,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 可滚动主体
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CHelperTheme.colors.backgroundComponent)
                            .padding(16.dp)
                ) {
                    Text(
                        text = "版本信息",
                        style =
                            TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CHelperTheme.colors.textMain,
                            ),
                    )
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        state = viewModel.name,
                        hint = stringResource(R.string.upload_field_name),
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        state = viewModel.description,
                        hint = stringResource(R.string.upload_field_description),
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextField(
                            state = viewModel.version,
                            hint = stringResource(R.string.upload_field_version),
                            modifier = Modifier.weight(1f),
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                        TextField(
                            state = viewModel.tags,
                            hint = stringResource(R.string.upload_field_tags),
                            modifier = Modifier.weight(1f),
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CHelperTheme.colors.backgroundComponent)
                            .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "命令区",
                            style =
                                TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CHelperTheme.colors.textMain,
                                ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(
                                            CHelperTheme.colors.mainColor.copy(alpha = 0.1f)
                                        )
                                        .clickable { viewModel.isShowTemplateDialog = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "示例模板",
                                    style =
                                        TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = CHelperTheme.colors.mainColor,
                                        ),
                                )
                            }
                            // V2 才有低代码状态补全。
                            if (viewModel.useV2)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFE65100).copy(alpha = 0.1f))
                                            .clickable { viewModel.isShowLowCodeHelper = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        id = R.drawable.pencil,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "标记辅助",
                                        style =
                                            TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFE65100),
                                            ),
                                    )
                                }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    TextField(
                        state = viewModel.commands,
                        hint =
                            stringResource(
                                if (viewModel.useV2) R.string.upload_field_commands_v2
                                else R.string.upload_field_commands_v1
                            ),
                        modifier = Modifier.fillMaxWidth().height(350.dp),
                        contentAlignment = Alignment.TopStart,
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val intent =
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                "https://abyssous.site/wiki".toUri(),
                                            )
                                        context.startActivity(intent)
                                    }
                                    .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                id = R.drawable.book,
                                contentDescription = stringResource(R.string.upload_wiki_link),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.upload_wiki_link),
                                style =
                                    TextStyle(
                                        fontSize = 13.sp,
                                        color = CHelperTheme.colors.mainColor,
                                        fontWeight = FontWeight.Medium,
                                    ),
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "启用 V2 语法",
                                style =
                                    TextStyle(
                                        fontSize = 13.sp,
                                        color = CHelperTheme.colors.textSecondary,
                                    ),
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
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                SettingsItem(
                    name = "自动生成 UUID 并同步",
                    description = "保存时自动生成 UUID，并将本地库同步到云端",
                    checked = viewModel.autoSync,
                    onCheckedChange = { viewModel.autoSync = it },
                )

                Spacer(Modifier.height(16.dp))
            }

            val saveLabel =
                when {
                    viewModel.isSyncing -> "同步中…"
                    viewModel.autoSync -> "保存并同步到云端"
                    else -> stringResource(R.string.layout_library_edit_save)
                }

            Button(
                text = saveLabel,
                onClick = {
                    if (viewModel.isSyncing) return@Button
                    viewModel.validationError()?.let { error ->
                        Toaster.show(error)
                        return@Button
                    }
                    saveLocalLibrary(
                        viewModel = viewModel,
                        editingLocalEntryId = localEntryId,
                        editingId = id,
                        existingLibrary = localLibraryFunction,
                        localDataStore = localCommandLabDataStore,
                        context = context,
                        onDone = viewModel::approveExit,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
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
                    val targetId = localEntryId ?: localLibraryFunction?.localEntryId
                    if (targetId != null) {
                        localCommandLabDataStore.removeLocalLibraryFunction(targetId)
                    } else if (id != null) {
                        localCommandLabDataStore.removeLocalLibraryFunction(id)
                    }
                    localCommandLabDataStore.clearLocalLibraryEditDraft(viewModel.draftKey)
                    viewModel.markSaved()
                    viewModel.approveExit()
                }
            },
        )
    }

    if (viewModel.isShowLowCodeHelper) {
        LowCodeV2HelperDialog(
            rawContent = viewModel.commands.text.toString(),
            onDismiss = { viewModel.isShowLowCodeHelper = false },
            onApply = { newContent ->
                viewModel.commands.setTextAndPlaceCursorAtEnd(newContent)
                viewModel.isShowLowCodeHelper = false
                Toaster.show("已应用！")
            },
        )
    }

    if (viewModel.isShowTemplateDialog) {
        val templates = viewModel.availableTemplates()
        MenuDialog(
            onDismissRequest = { viewModel.isShowTemplateDialog = false },
            data =
                templates
                    .mapIndexed { index, template -> template.label to index.toString() }
                    .plus("关闭" to "close")
                    .toTypedArray(),
            onChoose = { value ->
                value.toIntOrNull()?.let { index -> viewModel.applyTemplate(templates[index]) }
            },
        )
    }

    if (viewModel.isShowExitConfirm) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowExitConfirm = false },
            title = "退出编辑？",
            content = "当前修改尚未保存到本地库，草稿会自动保留，下次进入可继续编辑。",
            cancelText = "继续编辑",
            confirmText = "退出",
            onConfirm = {
                viewModel.isShowExitConfirm = false
                viewModel.viewModelScope.launch {
                    localCommandLabDataStore.saveLocalLibraryEditDraft(
                        viewModel.draftKey,
                        viewModel.toDraft(),
                    )
                    viewModel.approveExit()
                }
            },
        )
    }

    if (viewModel.isShowV2DowngradeConfirm) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowV2DowngradeConfirm = false },
            title = "切换到 V1 语法",
            content = "V1 语法的渲染效果远低于 V2，不支持命令链可视化和状态标记。确定吗？",
            confirmText = "确定",
            onConfirm = {
                viewModel.useV2 = false
                viewModel.isShowV2DowngradeConfirm = false
            },
        )
    }
}

/** 保存本地库，可选附加云端同步。 */
private fun saveLocalLibrary(
    viewModel: LocalLibraryEditViewModel,
    editingLocalEntryId: String?,
    editingId: Int?,
    existingLibrary: LibraryFunction?,
    localDataStore: LocalCommandLabDataStore,
    context: Context,
    onDone: () -> Unit,
) {
    viewModel.isSyncing = true
    viewModel.viewModelScope.launch {
        try {
            val ownerId = LoginUtil.accountId.value
            val library =
                viewModel
                    .buildLocalLibrary(existingLibrary, editingLocalEntryId)
                    .prepareAutoSync(ownerId)
            val entryId =
                when (viewModel.mode) {
                    EditMode.ADD -> localDataStore.addLocalLibraryFunction(library)
                    EditMode.UPDATE -> {
                        val target = editingLocalEntryId ?: existingLibrary?.localEntryId
                        if (target != null) {
                            check(localDataStore.updateLocalLibraryFunction(target, library)) {
                                "命令库已被删除"
                            }
                            target
                        } else {
                            localDataStore.updateLocalLibraryFunction(
                                requireNotNull(editingId),
                                library,
                            )
                            requireNotNull(library.localEntryId)
                        }
                    }
                }
            viewModel.markSaved()
            localDataStore.clearLocalLibraryEditDraft(viewModel.draftKey)
            val revision = library.localSyncRevision
            if (ownerId != null && revision != null) {
                try {
                    withContext(NonCancellable) {
                        LibrarySyncScheduler.enqueue(
                            context.applicationContext,
                            ownerId,
                            entryId,
                            revision,
                        )
                    }
                    Toaster.show("已保存到本地，将自动同步到云端")
                } catch (e: Exception) {
                    Toaster.show("已保留本地同步任务，稍后重试")
                }
            } else if (viewModel.autoSync) {
                Toaster.show("尚未登录正式账号，已保存到本地")
            }
            onDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toaster.show("保存失败：${e.message}")
        } finally {
            viewModel.isSyncing = false
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
