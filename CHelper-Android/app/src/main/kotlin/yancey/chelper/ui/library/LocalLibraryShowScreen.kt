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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.toast.Toaster
import yancey.chelper.R
import yancey.chelper.android.window.LoongFlowWindowManager
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.library.data.AuthorInfo
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.LibraryEditScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.MenuDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.library.mcd.ChainItem
import yancey.chelper.ui.library.mcd.MCDContentView
import yancey.chelper.ui.library.mcd.parseMCDStructure

@Composable
fun LocalLibraryShowScreen(
    library: LibraryFunction?,
    onEdit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val ambiguousDefault by
        settingsDataStore
            .ambiguousLineDefault()
            .collectAsStateWithLifecycle(initialValue = "comment")
    val hideMetadata by
        settingsDataStore.isHideMetadataPreview().collectAsStateWithLifecycle(initialValue = false)
    val resolvedSource = remember(library) { library?.toFullLocalMcd().orEmpty() }
    var showMenu by remember { mutableStateOf(false) }
    var showRawSource by remember { mutableStateOf(false) }
    var showLineCopy by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun openLoongFlowImport() {
        val currentLibrary = library ?: return
        if (
            XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getSystemAlertWindowPermission(),
            )
        ) {
            val shown =
                LoongFlowWindowManager.INSTANCE.showImport(
                    context,
                    currentLibrary.copy(content = resolvedSource),
                )
            if (!shown) Toaster.show("游龙窗口打开失败")
        } else {
            showPermissionDialog = true
        }
    }

    RootViewWithHeaderAndCopyright(
        title = library?.name ?: "加载中",
        headerRight = {
            Row {
                if (library != null) {
                    Icon(
                        id = R.drawable.loong_flow_import,
                        contentDescription = "游龙导入",
                        modifier =
                            Modifier.clickable { openLoongFlowImport() }.padding(5.dp).size(24.dp),
                    )
                }
                Icon(
                    id = R.drawable.more,
                    contentDescription = "更多操作",
                    modifier = Modifier.clickable { showMenu = true }.padding(5.dp).size(24.dp),
                )
            }
        },
    ) {
        // MCDContentView 现在不再自带 LazyColumn，需要外层提供滚动；
        // 这里用 verticalScroll 给整个内容区一个垂直滚动通道
        if (showRawSource) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 15.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
            ) {
                Text(text = resolvedSource)
            }
        } else {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 15.dp, vertical = 10.dp)
            ) {
                MCDContentView(
                    content = resolvedSource,
                    ambiguousDefault = ambiguousDefault,
                    showMetadata = !hideMetadata,
                )
            }
        }
    }

    if (showMenu && library != null) {
        MenuDialog(
            onDismissRequest = { showMenu = false },
            data = buildList {
                    if (onEdit != null) add("编辑" to "edit")
                    add("逐行复制" to "line_copy")
                    add("复制全部 MCD 源码" to "copy_all")
                    add((if (showRawSource) "查看可视化" else "查看源码") to "toggle_view")
                    add("关闭" to "close")
                }
                    .toTypedArray(),
            onChoose = { action ->
                when (action) {
                    "edit" -> onEdit?.invoke()
                    "line_copy" -> showLineCopy = true
                    "copy_all" -> {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MCD", resolvedSource))
                        Toast.makeText(context, "已复制全部 MCD 源码", Toast.LENGTH_SHORT).show()
                    }

                    "toggle_view" -> showRawSource = !showRawSource
                }
            },
        )
    }

    if (showLineCopy) {
        val commands =
            remember(resolvedSource, ambiguousDefault) {
                parseMCDStructure(resolvedSource, ambiguousDefault).chains.flatMap { chain ->
                    chain.items.mapNotNull { item ->
                        when (item) {
                            is ChainItem.Block -> item.block.command.takeIf(String::isNotEmpty)
                            is ChainItem.RawCommand -> item.command.takeIf(String::isNotEmpty)
                            is ChainItem.Comment -> null
                        }
                    }
                }
            }
        if (commands.isEmpty()) {
            DisposableEffect(Unit) {
                Toaster.show("没有可复制的命令")
                showLineCopy = false
                onDispose {}
            }
        } else {
            LineCopyDialog(commands = commands, onDismiss = { showLineCopy = false })
        }
    }

    if (showPermissionDialog) {
        IsConfirmDialog(
            onDismissRequest = { showPermissionDialog = false },
            content = "游龙导入需要悬浮窗权限，请进入设置授权。",
            confirmText = "打开设置",
            onConfirm = {
                XXPermissions.with(context)
                    .permission(PermissionLists.getSystemAlertWindowPermission())
                    .request { _, deniedList ->
                        Toaster.show(if (deniedList.isEmpty()) "悬浮窗权限获取成功" else "悬浮窗权限获取失败")
                    }
            },
        )
    }
}

@Composable
fun LocalLibraryShowScreen(
    localEntryId: String? = null,
    id: Int? = null,
    navController: NavHostController? = null,
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
    LocalLibraryShowScreen(
        library = localLibraryFunction,
        onEdit =
            localLibraryFunction?.localEntryId?.let { targetId ->
                { navController?.navigate(LibraryEditScreenKey(localEntryId = targetId)) }
            },
    )
}

@Preview
@Composable
fun LibraryShowScreenLightThemePreview() {
    val library =
        LibraryFunction().apply {
            name = "Library"
            author = AuthorInfo(name = "Author")
            content = buildString {
                appendLine("@name=TestLibrary")
                appendLine("@version=1.0.0")
                appendLine("# This is a comment")
                appendLine("/say Hello")
                appendLine("/tp @s 0 0 0")
            }
        }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryShowScreen(library = library)
    }
}

@Preview
@Composable
fun LibraryShowScreenDarkThemePreview() {
    val library =
        LibraryFunction().apply {
            name = "Library"
            author = AuthorInfo(name = "Author")
            content = buildString {
                appendLine("@name=TestLibrary")
                appendLine("@version=1.0.0")
                appendLine("# This is a comment")
                appendLine("/say Hello")
                appendLine("/tp @s 0 0 0")
            }
        }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryShowScreen(library = library)
    }
}
