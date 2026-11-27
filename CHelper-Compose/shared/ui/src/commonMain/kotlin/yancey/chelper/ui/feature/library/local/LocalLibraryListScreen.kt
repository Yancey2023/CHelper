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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.data.library.decodeLocalLibraryImport
import yancey.chelper.data.library.filterAndSortLocalLibraries
import yancey.chelper.data.library.toLocalDuplicate
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.LocalLibrarySort
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.dialog.MenuDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField
import yancey.chelper.ui.feature.library.components.LibraryGroupItem
import yancey.chelper.ui.feature.library.components.LibraryLoginHint
import yancey.chelper.ui.navigation.AccountScreenKey
import yancey.chelper.ui.navigation.ActivityCenterScreenKey
import yancey.chelper.ui.navigation.LibraryEditScreenKey
import yancey.chelper.ui.navigation.LibraryUploadScreenKey
import yancey.chelper.ui.navigation.LocalLibraryShowScreenKey
import yancey.chelper.ui.navigation.MessageScreenKey
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey
import yancey.chelper.ui.navigation.UserProfileScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.LocalClientServices as LocalContext
import yancey.chelper.ui.platform.LocalClientServices
import yancey.chelper.ui.platform.rememberTextClipboard

@Composable
fun LocalLibraryListScreen(
    accounts: AccountRepository = LocalClientServices.current.accounts,
    libraries: LibraryContentRepository = LocalClientServices.current.libraryContent,
    viewModel: LocalLibraryListViewModel = viewModel {
        LocalLibraryListViewModel(accounts, libraries)
    },
    navController: NavHostController = rememberNavController(),
    isTab: Boolean = false,
) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { context.libraries }
    val localLibraryFunctions by
        localCommandLabDataStore
            .localLibraryFunctions()
            .collectAsStateWithLifecycle(initialValue = null)
    val clipboard = rememberTextClipboard()
    val searchQuery = viewModel.keyword.text.toString()
    val filteredLibraries by
        produceState(
            initialValue = emptyList(),
            localLibraryFunctions,
            searchQuery,
            viewModel.localSort,
        ) {
            if (searchQuery.isNotBlank()) delay(150)
            value =
                withContext(Dispatchers.Default) {
                    filterAndSortLocalLibraries(
                        libraries = localLibraryFunctions.orEmpty(),
                        query = searchQuery,
                        sort = viewModel.localSort,
                    )
                }
        }

    var showLocalMenuId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmId by remember { mutableStateOf<String?>(null) }
    var showLocalToolsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedLocalIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var localSelectionMode by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val isSelectionMode = localSelectionMode
    // 右上角"账户"图标点开后的菜单（账户中心 / 退出登录）
    // 未登录态点图标直接跳 AccountScreen，不弹这个菜单
    var showAccountMenu by remember { mutableStateOf(false) }
    // "退出登录"二次确认，避免顶栏菜单误触登出
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 进入页面时刷新登录状态、用户资料、容量配额
    // 用户可能从 AccountScreen 登录回来或者上传完文件，状态需要重新拉取
    LaunchedEffect(Unit) { viewModel.refreshUserState() }
    LaunchedEffect(localLibraryFunctions) {
        val existingIds = localLibraryFunctions.orEmpty().mapNotNull { it.localEntryId }.toSet()
        selectedLocalIds = selectedLocalIds.intersect(existingIds)
    }

    RootViewWithHeaderAndCopyright(
        title = "我的库",
        // 与云端首页一致：tab 内也显示返回箭头，pop 回进入命令库前的页面
        showBack = true,
        onBack = if (isTab) ({ navController.popBackStack() }) else null,
        headerRight = {
            Icon(
                id = Res.drawable.file_input,
                modifier =
                    Modifier.clickable { viewModel.isShowImportDialog = true }
                        .padding(5.dp)
                        .size(24.dp),
                contentDescription =
                    stringResource(Res.string.layout_library_list_icon_import_content_description),
            )
            Icon(
                id = Res.drawable.plus,
                modifier =
                    Modifier.clickable {
                            // 顶栏 "+" 默认就是新建"本地"命令库（草稿存本地、可手动上传到云端）。
                            // 之前直接跳 LibraryUploadScreen 把"新建"硬挂在云端入口上，会让没登录的
                            // 用户进去看到要登录的报错，已登录用户也被迫先发布到云端才能记录。
                            // 想直接发到云端的入口保留在下方"上传指令"快捷操作里。
                            navController.navigate(LibraryEditScreenKey(id = null))
                        }
                        .padding(5.dp)
                        .size(24.dp),
                contentDescription =
                    stringResource(Res.string.layout_library_list_icon_add_content_description),
            )
            Icon(
                id = Res.drawable.ellipsis,
                modifier =
                    Modifier.clickable { showLocalToolsMenu = true }.padding(5.dp).size(24.dp),
                contentDescription = "本地库管理",
            )
            // 账户入口：把原来"我的" tab 砍掉后挪到这里。已登录弹菜单（账户中心/登出），
            // 未登录直接跳 AccountScreen 让用户去登录注册
            Icon(
                id = Res.drawable.user_round,
                modifier =
                    Modifier.clickable {
                            if (viewModel.isLoggedIn) {
                                showAccountMenu = true
                            } else {
                                navController.navigate(AccountScreenKey)
                            }
                        }
                        .padding(5.dp)
                        .size(24.dp),
                contentDescription = "账户",
            )
        },
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            TextField(
                state = viewModel.keyword,
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart,
                hint = stringResource(Res.string.layout_library_list_search_hint),
                horizontalPadding = 20.dp,
                verticalPadding = 0.dp,
                clipCornerSize = 20.dp,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.fillMaxSize().padding(15.dp, 0.dp)) {
                // 用户信息 + 我的云端库 区域
                if (viewModel.isLoggedIn) {
                    item(key = "user_header", contentType = "header") {
                        UserCloudLibrariesHeader(
                            viewModel = viewModel,
                            navController = navController,
                        )
                    }
                    if (viewModel.myCloudLibraries.isEmpty()) {
                        item(key = "cloud_empty", contentType = "empty") {
                            LibraryGroupItem(1, 2) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (viewModel.isCloudLibrariesLoading) "加载中..."
                                        else "暂无云端命令库",
                                        style =
                                            TextStyle(
                                                color = CHelperTheme.colors.textSecondary,
                                                fontSize = 13.sp,
                                            ),
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            viewModel.myCloudLibraries,
                            key = { _, library -> "cloud:${requireNotNull(library.id)}" },
                            contentType = { _, _ -> "cloud-library" },
                        ) { index, library ->
                            LibraryGroupItem(index + 1, viewModel.myCloudLibraries.size + 1) {
                                Row(
                                    modifier =
                                        Modifier.clickable {
                                                library.id?.let {
                                                    navController.navigate(
                                                        PublicLibraryShowScreenKey(
                                                            id = it,
                                                            isPrivate = true,
                                                        )
                                                    )
                                                }
                                            }
                                            .padding(15.dp, 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = library.name ?: "未命名",
                                            style = TextStyle(fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = library.note ?: "",
                                            style =
                                                TextStyle(
                                                    color = CHelperTheme.colors.textSecondary,
                                                    fontSize = 12.sp,
                                                ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (library.hasPublicVersion == true) {
                                        // 公开版与私有版不一致 → 显示"待同步"提醒（橙色）；
                                        // 已同步 → 显示"已发布"（主题色）。后端权威。
                                        if (library.hasUnsyncedChanges == true) {
                                            Text(
                                                text = "待同步",
                                                style =
                                                    TextStyle(
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFE65100),
                                                    ),
                                                modifier =
                                                    Modifier.align(Alignment.CenterVertically),
                                            )
                                        } else {
                                            Text(
                                                text = "已发布",
                                                style =
                                                    TextStyle(
                                                        fontSize = 11.sp,
                                                        color = CHelperTheme.colors.mainColor,
                                                    ),
                                                modifier =
                                                    Modifier.align(Alignment.CenterVertically),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "cloud_spacing", contentType = "spacing") {
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    item(key = "login_hint", contentType = "header") {
                        LoginHintCard(navController = navController)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // 本地库标题
                item(key = "local_header", contentType = "header") {
                    Text(
                        text =
                            if (isSelectionMode) {
                                "已选择 ${selectedLocalIds.size} 项"
                            } else {
                                "本地命令库 · ${viewModel.localSort.label}"
                            },
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                        style =
                            TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CHelperTheme.colors.textMain,
                            ),
                    )
                }

                if (filteredLibraries.isEmpty()) {
                    item(key = "local_empty", contentType = "empty") {
                        LibraryGroupItem(0, 1) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无本地命令库",
                                    style =
                                        TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 14.sp,
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        filteredLibraries,
                        key = { _, entry -> "local:${entry.localEntryId}" },
                        contentType = { _, _ -> "local-library" },
                    ) { index, entry ->
                        val library = entry.library
                        val localEntryId = entry.localEntryId
                        val isSelected = localEntryId in selectedLocalIds
                        LibraryGroupItem(index, filteredLibraries.size) {
                            Row(
                                modifier =
                                    Modifier.clickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedLocalIds =
                                                        if (isSelected) {
                                                            selectedLocalIds - localEntryId
                                                        } else {
                                                            selectedLocalIds + localEntryId
                                                        }
                                                } else {
                                                    navController.navigate(
                                                        LocalLibraryShowScreenKey(
                                                            localEntryId = localEntryId
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                        .padding(20.dp, 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = library.name ?: "未命名",
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = TextStyle(fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        // 本地未同步标签：仅当关联过云端（有 uuid）且本地又改过才亮
                                        // 纯本地草稿 / 已同步的本地副本都不显示，保持列表干净
                                        if (
                                            library.localUnsynced && !library.uuid.isNullOrEmpty()
                                        ) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier =
                                                    Modifier.clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            CHelperTheme.colors.mainColor.copy(
                                                                alpha = 0.15f
                                                            )
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text =
                                                        when {
                                                            library.localSyncError != null ->
                                                                "同步失败，将重试"
                                                            library.localSyncRevision != null ->
                                                                "等待同步"
                                                            else -> "本地未同步"
                                                        },
                                                    style =
                                                        TextStyle(
                                                            fontSize = 10.sp,
                                                            color = CHelperTheme.colors.mainColor,
                                                            fontWeight = FontWeight.Medium,
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = library.note ?: "",
                                        modifier = Modifier.fillMaxWidth(),
                                        style =
                                            TextStyle(
                                                color = CHelperTheme.colors.textSecondary,
                                                fontSize = 14.sp,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelectionMode) {
                                    Icon(
                                        id =
                                            if (isSelected) Res.drawable.circle_check
                                            else Res.drawable.circle,
                                        contentDescription = if (isSelected) "取消选择" else "选择",
                                        modifier =
                                            Modifier.align(Alignment.CenterVertically)
                                                .padding(start = 5.dp)
                                                .size(24.dp),
                                    )
                                } else {
                                    Icon(
                                        id = Res.drawable.ellipsis,
                                        contentDescription = "更多操作",
                                        modifier =
                                            Modifier.align(Alignment.CenterVertically)
                                                .clickable { showLocalMenuId = localEntryId }
                                                .padding(start = 5.dp)
                                                .size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 本地库操作菜单
    val menuLibrary =
        localLibraryFunctions.orEmpty().firstOrNull { it.localEntryId == showLocalMenuId }
    if (showLocalMenuId != null && menuLibrary != null) {
        val library = menuLibrary
        val localEntryId = requireNotNull(library.localEntryId)
        val menuItems = buildList {
            if (viewModel.isLoggedIn) add("上传到云端" to "upload")
            add("编辑" to "edit")
            add("复制副本" to "duplicate")
            add("导出" to "export")
            add("删除" to "delete")
            add("关闭" to "close")
        }
            .toTypedArray()
        MenuDialog(
            onDismissRequest = { showLocalMenuId = null },
            data = menuItems,
            onChoose = { action ->
                when (action) {
                    "upload" -> {
                        viewModel.uploadToCloud(
                            library = library,
                            localEntryId = localEntryId,
                            localDataStore = localCommandLabDataStore,
                        )
                    }

                    "edit" -> {
                        navController.navigate(LibraryEditScreenKey(localEntryId = localEntryId))
                    }

                    "duplicate" -> {
                        viewModel.viewModelScope.launch {
                            localCommandLabDataStore.addLocalLibraryFunction(
                                library.toLocalDuplicate()
                            )
                            ClientMessages.show("已创建本地副本")
                        }
                    }

                    "export" -> {
                        // 导出单个库到剪切板
                        val output = Json.encodeToString(LibraryFunction.serializer(), library)
                        viewModel.viewModelScope.launch {
                            clipboard.withFeedback {
                                copyText(output)
                                ClientMessages.show("已复制到剪切板")
                            }
                        }
                    }

                    "delete" -> {
                        showDeleteConfirmId = localEntryId
                    }
                }
            },
        )
    }

    // 删除确认对话框
    val deleteLibrary =
        localLibraryFunctions.orEmpty().firstOrNull { it.localEntryId == showDeleteConfirmId }
    if (showDeleteConfirmId != null && deleteLibrary != null) {
        IsConfirmDialog(
            onDismissRequest = { showDeleteConfirmId = null },
            title = "删除本地命令库",
            content = "确定要删除「${deleteLibrary.name ?: "未命名"}」吗？此操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                val idToDelete = showDeleteConfirmId ?: return@IsConfirmDialog
                showDeleteConfirmId = null
                viewModel.viewModelScope.launch {
                    localCommandLabDataStore.removeLocalLibraryFunction(idToDelete)
                    ClientMessages.show("已删除")
                }
            },
        )
    }

    if (viewModel.isShowImportDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowImportDialog = false },
            title = "从剪切板导入",
            content = "请把要导入的数据放到剪切板",
            confirmText = "导入",
            onConfirm = {
                viewModel.viewModelScope.launch {
                    clipboard.withFeedback {
                        clipboardText()?.let { text ->
                            run {
                                try {
                                    val libraries = decodeLocalLibraryImport(text)
                                    localCommandLabDataStore.addLocalLibraryFunctions(libraries)
                                    ClientMessages.show("已导入 ${libraries.size} 个命令库")
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    ClientMessages.show("导入失败")
                                }
                            }
                        }
                    }
                }
            },
        )
    }
    if (viewModel.isShowExportDialog) {
        val output =
            remember(localLibraryFunctions) { Json.encodeToString(localLibraryFunctions?.toList()) }
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowExportDialog = false },
            title = "导出",
            content = output,
            confirmText = "复制",
            onConfirm = {
                viewModel.viewModelScope.launch {
                    clipboard.withFeedback {
                        copyText(output)
                        ClientMessages.show("已复制")
                    }
                }
            },
        )
    }

    if (showLocalToolsMenu) {
        val allVisibleSelected =
            filteredLibraries.isNotEmpty() &&
                filteredLibraries.all { it.localEntryId in selectedLocalIds }
        MenuDialog(
            onDismissRequest = { showLocalToolsMenu = false },
            data = buildList {
                    add("排序：${viewModel.localSort.label}" to "sort")
                    if (!isSelectionMode) add("进入批量选择" to "selection_mode")
                    if (isSelectionMode)
                        add((if (allVisibleSelected) "取消选择当前结果" else "选择当前结果") to "toggle_visible")
                    if (isSelectionMode && selectedLocalIds.isNotEmpty()) {
                        add("导出已选 (${selectedLocalIds.size})" to "export_selected")
                        add("删除已选 (${selectedLocalIds.size})" to "delete_selected")
                    }
                    if (isSelectionMode) add("退出批量选择" to "clear_selection")
                    add("导出全部本地库" to "export_all")
                    add("关闭" to "close")
                }
                    .toTypedArray(),
            onChoose = { action ->
                when (action) {
                    "sort" -> showSortMenu = true
                    "selection_mode" -> localSelectionMode = true
                    "toggle_visible" -> {
                        val visibleIds = filteredLibraries.map { it.localEntryId }.toSet()
                        selectedLocalIds =
                            if (allVisibleSelected) {
                                selectedLocalIds - visibleIds
                            } else {
                                selectedLocalIds + visibleIds
                            }
                    }

                    "export_selected" -> {
                        val selected =
                            localLibraryFunctions.orEmpty().filter {
                                it.localEntryId in selectedLocalIds
                            }
                        val output = Json.encodeToString(selected)
                        viewModel.viewModelScope.launch {
                            clipboard.withFeedback {
                                copyText(output)
                                ClientMessages.show("已导出 ${selected.size} 个命令库")
                            }
                        }
                    }

                    "delete_selected" -> showBatchDeleteConfirm = true
                    "clear_selection" -> {
                        selectedLocalIds = emptySet()
                        localSelectionMode = false
                    }

                    "export_all" -> viewModel.isShowExportDialog = true
                }
            },
        )
    }

    if (showSortMenu) {
        MenuDialog(
            onDismissRequest = { showSortMenu = false },
            data = LocalLibrarySort.entries.map { it.label to it.name }.toTypedArray(),
            onChoose = { value -> viewModel.localSort = LocalLibrarySort.valueOf(value) },
        )
    }

    if (showBatchDeleteConfirm && selectedLocalIds.isNotEmpty()) {
        IsConfirmDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = "批量删除本地命令库",
            content = "确定删除已选择的 ${selectedLocalIds.size} 个命令库吗？此操作不可恢复。",
            confirmText = "全部删除",
            onConfirm = {
                val ids = selectedLocalIds
                selectedLocalIds = emptySet()
                localSelectionMode = false
                showBatchDeleteConfirm = false
                viewModel.viewModelScope.launch {
                    val count = localCommandLabDataStore.removeLocalLibraryFunctions(ids)
                    ClientMessages.show("已删除 $count 个命令库")
                }
            },
        )
    }

    // 已登录态下右上角账户图标点出的菜单：账户中心、退出登录、关闭
    // 未登录走直接跳 AccountScreen 的分支，不进这里
    if (showAccountMenu) {
        MenuDialog(
            onDismissRequest = { showAccountMenu = false },
            data = arrayOf("账户中心" to "account", "退出登录" to "logout", "关闭" to "close"),
            onChoose = { action ->
                when (action) {
                    "account" -> navController.navigate(AccountScreenKey)
                    "logout" -> showLogoutConfirm = true
                }
            },
        )
    }

    if (showLogoutConfirm) {
        IsConfirmDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = "退出登录",
            content = "确定退出当前账户？退出后云端命令库需要重新登录才能访问。",
            confirmText = "退出",
            onConfirm = {
                viewModel.logout()
                ClientMessages.show("已退出登录")
            },
        )
    }
}

@Composable
private fun UserCloudLibrariesHeader(
    viewModel: LocalLibraryListViewModel,
    navController: NavHostController,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(color = CHelperTheme.colors.backgroundComponent)
    ) {
        // 用户信息行
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { navController.navigate(AccountScreenKey) }
                    .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像：用 AsyncImage 拉真实头像，拉不到再 fallback 到默认 user 图标
            // gravatarUrl 优先（用户上传的），没有再用站点头像兜底
            val user = viewModel.currentUser
            val profile = viewModel.userProfile
            val avatarUrl =
                user?.gravatarUrl
                    ?: profile?.avatarUrl
                    ?: user?.id?.let { "https://abyssous.site/avatar/$it" }
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(CHelperTheme.colors.mainColor.copy(alpha = 0.15f)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(resource = Res.drawable.user_round),
                error = painterResource(resource = Res.drawable.user_round),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 名字 + tier 徽章 + 称号
                // 全部塞在一行内，过长靠 weight 防止挤掉徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user?.nickname ?: profile?.nickname ?: "已登录",
                        modifier = Modifier.weight(1f, fill = false),
                        style =
                            TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = CHelperTheme.colors.textMain,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val tier = (profile?.tier ?: user?.tier) ?: 0
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier =
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (tier > 0) CHelperTheme.colors.mainColor.copy(alpha = 0.15f)
                                    else CHelperTheme.colors.background
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Tier $tier",
                            style =
                                TextStyle(
                                    fontSize = 10.sp,
                                    color =
                                        if (tier > 0) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                    if (tier >= 2) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(resource = Res.drawable.shield_check),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    } else if (tier == 1) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(resource = Res.drawable.badge_check),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }

                    // 称号：放在 tier 徽章后面，没有就不渲染
                    profile
                        ?.userTitle
                        ?.takeIf { it.isNotBlank() }
                        ?.let { title ->
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier =
                                    Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(
                                            CHelperTheme.colors.mainColor.copy(alpha = 0.10f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = title,
                                    style =
                                        TextStyle(
                                            fontSize = 10.sp,
                                            color = CHelperTheme.colors.mainColor,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    maxLines = 1,
                                )
                            }
                        }
                }
                Text(
                    text = "查看个人中心",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                )
            }
            Icon(
                id = Res.drawable.chevron_right,
                modifier = Modifier.size(20.dp),
                contentDescription = null,
            )
        }

        Divider(padding = 0.dp)

        // 快捷操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 站内信按钮
            QuickActionButton(
                icon = Res.drawable.mail,
                label = "站内信",
                onClick = { navController.navigate(MessageScreenKey) },
            )
            // 上传新指令按钮
            QuickActionButton(
                icon = Res.drawable.upload,
                label = "上传指令",
                onClick = { navController.navigate(LibraryUploadScreenKey()) },
            )
            QuickActionButton(
                icon = Res.drawable.heart,
                label = "创作季",
                onClick = { navController.navigate(ActivityCenterScreenKey()) },
            )
            // 创作者主页按钮
            QuickActionButton(
                icon = Res.drawable.user_round,
                label = "创作者",
                onClick = {
                    navController.navigate(
                        UserProfileScreenKey(id = viewModel.currentUser?.id ?: 0)
                    )
                },
            )
        }

        Divider(padding = 0.dp)

        // 我的云端库标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    id = Res.drawable.box,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "我的云端库",
                    style =
                        TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = CHelperTheme.colors.textMain,
                        ),
                )
            }
            if (viewModel.isCloudLibrariesLoading) {
                Text(
                    text = "加载中...",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                )
            } else {
                // 显式刷新入口：缓存命中时用户看不到自动刷新，需要这个出口手动绕过 TTL。
                // 比如上传 / 删除完云端库立刻想确认列表对齐。
                Icon(
                    id = Res.drawable.refresh_cw,
                    modifier =
                        Modifier.clickable { viewModel.refreshUserState(forceRefresh = true) }
                            .padding(4.dp)
                            .size(18.dp),
                    contentDescription = "刷新云端库",
                )
            }
        }

        // 容量条：在 title 下方
        // 后端约定：limit == -1 表示无限制；正常 limit > 0 表示具体配额；其他情况才不渲染
        val limit = viewModel.quotaLimit
        val used = viewModel.quotaUsed
        if (used != null && limit != null && (limit > 0 || limit == -1)) {
            QuotaBar(
                used = used,
                limit = limit,
                modifier =
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp).padding(bottom = 8.dp),
            )
        }
    }
}

/** One lazy row retains the rounded card outline without composing its off-screen siblings. */
@Composable
private fun QuotaBar(used: Int, limit: Int, modifier: Modifier = Modifier) {
    val unlimited = limit == -1
    val ratio =
        if (unlimited) {
            0.1f
        } else {
            (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        }
    val barColor =
        when {
            unlimited -> CHelperTheme.colors.mainColor
            ratio >= 0.9f -> Color(0xFFD32F2F)
            ratio >= 0.7f -> Color(0xFFE65100)
            else -> CHelperTheme.colors.mainColor
        }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "容量",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
            Text(
                text = if (unlimited) "$used / 无限制" else "$used / $limit",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Spacer(Modifier.height(4.dp))
        // 用 Box 做轨道+前景两层，比拉 LinearProgressIndicator 自定义颜色省事
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CHelperTheme.colors.background)
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth(ratio)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
            )
        }
    }
}

@Composable
private fun LoginHintCard(navController: NavHostController) {
    LibraryLoginHint(
        onLogin = { navController.navigate(AccountScreenKey) },
        onMessages = { navController.navigate(MessageScreenKey) },
    )
}

@Composable
private fun QuickActionButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(id = icon, modifier = Modifier.size(24.dp), contentDescription = label)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
        )
    }
}
