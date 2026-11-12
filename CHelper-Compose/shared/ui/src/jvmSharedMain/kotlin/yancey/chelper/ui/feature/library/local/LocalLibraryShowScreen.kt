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

package yancey.chelper.ui.feature.library.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import yancey.chelper.data.library.document.toFullLocalMcd
import yancey.chelper.domain.library.AuthorInfo
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.feature.library.components.LibraryDetailContent
import yancey.chelper.ui.feature.library.mcd.MCDContentView
import yancey.chelper.ui.navigation.LibraryEditScreenKey
import yancey.chelper.ui.platform.ClientToast as Toaster
import yancey.chelper.ui.platform.LocalClientServices as LocalContext

@Composable
fun LocalLibraryShowScreen(library: LibraryFunction?, onEdit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val settingsDataStore = remember(context) { context.settings }
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
        if (context.platform.loongFlowPermissionGranted()) {
            val shown =
                context.platform.showLoongFlow(currentLibrary.copy(content = resolvedSource))
            if (!shown) Toaster.show("游龙窗口打开失败")
        } else {
            showPermissionDialog = true
        }
    }

    LibraryDetailContent(
        title = library?.name ?: "加载中",
        resolvedSource = resolvedSource,
        ambiguousDefault = ambiguousDefault,
        hideMetadata = hideMetadata,
        onEdit = onEdit,
        onLoongFlow =
            if (context.platform.supportsLoongFlow && library != null) ::openLoongFlowImport
            else null,
        onCopy = { text ->
            context.platform.copyText(text)
            Toaster.show("已复制")
        },
        onMessage = { Toaster.show(it) },
    ) { content, ambiguous, metadata ->
        MCDContentView(content, ambiguousDefault = ambiguous, showMetadata = metadata)
    }

    if (showPermissionDialog) {
        IsConfirmDialog(
            onDismissRequest = { showPermissionDialog = false },
            content = "游龙导入需要悬浮窗权限，请进入设置授权。",
            confirmText = "打开设置",
            onConfirm = {
                context.platform.requestLoongFlowPermission { granted ->
                    Toaster.show(if (granted) "悬浮窗权限获取成功" else "悬浮窗权限获取失败")
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
    val localCommandLabDataStore = remember(context) { context.libraries }
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
