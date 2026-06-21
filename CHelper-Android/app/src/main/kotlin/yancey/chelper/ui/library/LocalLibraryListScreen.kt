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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.hjq.toast.Toaster
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import yancey.chelper.R
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.CPLUploadScreenKey
import yancey.chelper.ui.CPLUserScreenKey
import yancey.chelper.ui.LibraryEditScreenKey
import yancey.chelper.ui.LocalLibraryShowScreenKey
import yancey.chelper.ui.MessageScreenKey
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.UserProfileScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun LocalLibraryListScreen(
    viewModel: LocalLibraryListViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    isTab: Boolean = false,
) {
    val context = LocalContext.current
    val localCommandLabDataStore = remember(context) { LocalCommandLabDataStore(context) }
    val localLibraryFunctions by localCommandLabDataStore.localLibraryFunctions()
        .collectAsState(initial = null)
    val clipboard = LocalClipboard.current
    val filteredLibraries =
        remember(localLibraryFunctions, viewModel.keyword.text) {
            if (viewModel.keyword.text.isEmpty()) {
                localLibraryFunctions ?: listOf()
            } else {
                localLibraryFunctions?.filter { it.name != null && it.name!!.contains(viewModel.keyword.text) }
                    ?: listOf()
            }
        }

    var showLocalMenuIndex by remember { mutableStateOf(-1) }
    var showDeleteConfirmIndex by remember { mutableStateOf(-1) }
    // 右上角"账户"图标点开后的菜单（账户中心 / 退出登录）
    // 未登录态点图标直接跳 CPLUserScreen，不弹这个菜单
    var showAccountMenu by remember { mutableStateOf(false) }
    // "退出登录"二次确认，避免顶栏菜单误触登出
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 进入页面时刷新登录状态、用户资料、容量配额
    // 用户可能从 CPLUserScreen 登录回来或者上传完文件，状态需要重新拉取
    LaunchedEffect(Unit) {
        viewModel.refreshUserState()
    }

    RootViewWithHeaderAndCopyright(
        title = "我的库",
        showBack = !isTab,
        headerRight = {
            Icon(
                id = R.drawable.file_arrow_left,
                modifier = Modifier
                    .clickable {
                        viewModel.isShowImportDialog = true
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = stringResource(R.string.layout_library_list_icon_import_content_description)
            )
            Icon(
                id = R.drawable.plus,
                modifier = Modifier
                    .clickable {
                        // 顶栏 "+" 默认就是新建"本地"命令库（草稿存本地、可手动上传到云端）。
                        // 之前直接跳 CPLUploadScreen 把"新建"硬挂在云端入口上，会让没登录的
                        // 用户进去看到要登录的报错，已登录用户也被迫先发布到云端才能记录。
                        // 想直接发到云端的入口保留在下方"上传指令"快捷操作里。
                        navController.navigate(LibraryEditScreenKey(id = null))
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = stringResource(R.string.layout_library_list_icon_add_content_description)
            )
            // 账户入口：把原来"我的" tab 砍掉后挪到这里。已登录弹菜单（账户中心/登出），
            // 未登录直接跳 CPLUserScreen 让用户去登录注册
            Icon(
                id = R.drawable.ic_user,
                modifier = Modifier
                    .clickable {
                        if (viewModel.isLoggedIn) {
                            showAccountMenu = true
                        } else {
                            navController.navigate(CPLUserScreenKey)
                        }
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = "账户"
            )
        }) {
        Column {
            Spacer(Modifier.height(10.dp))
            TextField(
                state = viewModel.keyword,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart,
                hint = stringResource(R.string.layout_library_list_search_hint),
                horizontalPadding = 20.dp,
                verticalPadding = 0.dp,
                clipCornerSize = 20.dp,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(15.dp, 0.dp)
            ) {
                // 用户信息 + 我的云端库 区域
                if (viewModel.isLoggedIn) {
                    item(key = "user_header") {
                        UserCloudLibrariesSection(
                            viewModel = viewModel,
                            navController = navController
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    item(key = "login_hint") {
                        LoginHintCard(navController = navController)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // 本地库标题
                item(key = "local_header") {
                    Text(
                        text = "本地命令库",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.textMain
                        )
                    )
                }

                // 本地库列表
                item(key = "local_list") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(color = CHelperTheme.colors.backgroundComponent)
                    ) {
                        filteredLibraries.forEachIndexed { index, library ->
                            Row(
                                modifier = Modifier
                                    .clickable(onClick = {
                                        navController.navigate(LocalLibraryShowScreenKey(id = index))
                                    })
                                    .padding(20.dp, 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = library.name ?: "未命名",
                                            modifier = Modifier.weight(1f, fill = false),
                                            style = TextStyle(fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // 本地未同步标签：仅当关联过云端（有 uuid）且本地又改过才亮
                                        // 纯本地草稿 / 已同步的本地副本都不显示，保持列表干净
                                        if (library.localUnsynced && !library.uuid.isNullOrEmpty()) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        CHelperTheme.colors.mainColor.copy(
                                                            alpha = 0.15f
                                                        )
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "本地未同步",
                                                    style = TextStyle(
                                                        fontSize = 10.sp,
                                                        color = CHelperTheme.colors.mainColor,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = library.note ?: "",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 14.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    id = R.drawable.more,
                                    contentDescription = "更多操作",
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .clickable {
                                            showLocalMenuIndex = index
                                        }
                                        .padding(start = 5.dp)
                                        .size(24.dp)
                                )
                            }
                            if (index < filteredLibraries.lastIndex) {
                                Divider(padding = 0.dp)
                            }
                        }
                        if (filteredLibraries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无本地命令库",
                                    style = TextStyle(
                                        color = CHelperTheme.colors.textSecondary,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 本地库操作菜单
    if (showLocalMenuIndex >= 0 && showLocalMenuIndex < filteredLibraries.size) {
        val library = filteredLibraries[showLocalMenuIndex]
        val menuItems = buildList {
            if (viewModel.isLoggedIn) add("上传到云端" to "upload")
            add("编辑" to "edit")
            add("导出" to "export")
            add("删除" to "delete")
            add("关闭" to "close")
        }.toTypedArray()
        ChoosingDialog(
            onDismissRequest = { showLocalMenuIndex = -1 },
            data = menuItems,
            onChoose = { action ->
                when (action) {
                    "upload" -> {
                        viewModel.uploadToCloud(
                            library = library,
                            localIndex = showLocalMenuIndex,
                            localDataStore = localCommandLabDataStore
                        )
                    }

                    "edit" -> {
                        navController.navigate(LibraryEditScreenKey(id = showLocalMenuIndex))
                    }

                    "export" -> {
                        // 导出单个库到剪切板
                        val output = Json.encodeToString(LibraryFunction.serializer(), library)
                        viewModel.viewModelScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        null,
                                        output
                                    )
                                )
                            )
                            Toaster.show("已复制到剪切板")
                        }
                    }

                    "delete" -> {
                        showDeleteConfirmIndex = showLocalMenuIndex
                    }
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirmIndex >= 0 && showDeleteConfirmIndex < filteredLibraries.size) {
        IsConfirmDialog(
            onDismissRequest = { showDeleteConfirmIndex = -1 },
            title = "删除本地命令库",
            content = "确定要删除「${filteredLibraries[showDeleteConfirmIndex].name ?: "未命名"}」吗？此操作不可恢复。",
            confirmText = "删除",
            onConfirm = {
                val indexToDelete = showDeleteConfirmIndex
                showDeleteConfirmIndex = -1
                viewModel.viewModelScope.launch {
                    localCommandLabDataStore.removeLocalLibraryFunction(indexToDelete)
                    Toaster.show("已删除")
                }
            }
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
                    clipboard.getClipEntry()?.clipData?.apply {
                        if (itemCount > 0) {
                            val text = getItemAt(0).text.toString()
                            try {
                                val libraries = Json.decodeFromString<List<LibraryFunction>>(text)
                                localCommandLabDataStore.addLocalLibraryFunctions(libraries)
                                Toaster.show("导入成功")
                            } catch (_: Throwable) {
                                Toaster.show("导入失败")
                            }
                        }
                    }
                }
            }
        )
    }
    if (viewModel.isShowExportDialog) {
        val output = remember(localLibraryFunctions) {
            Json.encodeToString(localLibraryFunctions?.toList())
        }
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowExportDialog = false },
            title = "导出",
            content = output,
            confirmText = "复制",
            onConfirm = {
                viewModel.viewModelScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                null,
                                output
                            )
                        )
                    )
                    Toaster.show("已复制")
                }
            }
        )
    }

    // 已登录态下右上角账户图标点出的菜单：账户中心、退出登录、关闭
    // 未登录走直接跳 CPLUserScreen 的分支，不进这里
    if (showAccountMenu) {
        ChoosingDialog(
            onDismissRequest = { showAccountMenu = false },
            data = arrayOf(
                "账户中心" to "account",
                "退出登录" to "logout",
                "关闭" to "close"
            ),
            onChoose = { action ->
                when (action) {
                    "account" -> navController.navigate(CPLUserScreenKey)
                    "logout" -> showLogoutConfirm = true
                }
            }
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
                Toaster.show("已退出登录")
            }
        )
    }
}

@Composable
private fun UserCloudLibrariesSection(
    viewModel: LocalLibraryListViewModel,
    navController: NavHostController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color = CHelperTheme.colors.backgroundComponent)
    ) {
        // 用户信息行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    navController.navigate(CPLUserScreenKey)
                }
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像：用 AsyncImage 拉真实头像，拉不到再 fallback 到默认 user 图标
            // gravatarUrl 优先（用户上传的），没有再用站点头像兜底
            val user = viewModel.currentUser
            val profile = viewModel.userProfile
            val avatarUrl = user?.gravatarUrl
                ?: profile?.avatarUrl
                ?: user?.id?.let { "https://abyssous.site/avatar/$it" }
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CHelperTheme.colors.mainColor.copy(alpha = 0.15f)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_user),
                error = painterResource(id = R.drawable.ic_user)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 名字 + tier 徽章 + 称号
                // 全部塞在一行内，过长靠 weight 防止挤掉徽章
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user?.nickname ?: profile?.nickname ?: "已登录",
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = CHelperTheme.colors.textMain
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val tier = (profile?.tier ?: user?.tier) ?: 0
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (tier > 0) CHelperTheme.colors.mainColor.copy(alpha = 0.15f)
                                else CHelperTheme.colors.background
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Tier $tier",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = if (tier > 0) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    if (tier >= 2) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_verified_advanced),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    } else if (tier == 1) {
                        Spacer(Modifier.width(4.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_verified_normal),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // 称号：放在 tier 徽章后面，没有就不渲染
                    profile?.userTitle?.takeIf { it.isNotBlank() }?.let { title ->
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CHelperTheme.colors.mainColor.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = title,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = "查看个人中心",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary
                    )
                )
            }
            Icon(
                id = R.drawable.chevron_right,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
        }

        Divider(padding = 0.dp)

        // 快捷操作按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 站内信按钮
            QuickActionButton(
                icon = R.drawable.ic_mail,
                label = "站内信",
                onClick = { navController.navigate(MessageScreenKey) }
            )
            // 上传新指令按钮
            QuickActionButton(
                icon = R.drawable.upload,
                label = "上传指令",
                onClick = { navController.navigate(CPLUploadScreenKey()) }
            )
            // 创作者主页按钮
            QuickActionButton(
                icon = R.drawable.ic_user,
                label = "创作者",
                onClick = {
                    navController.navigate(
                        UserProfileScreenKey(
                            id = viewModel.currentUser?.id ?: 0
                        )
                    )
                }
            )
        }

        Divider(padding = 0.dp)

        // 我的云端库标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    id = R.drawable.box,
                    modifier = Modifier.size(18.dp),
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "我的云端库",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.textMain
                    )
                )
            }
            if (viewModel.isCloudLibrariesLoading) {
                Text(
                    text = "加载中...",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary
                    )
                )
            } else {
                // 显式刷新入口：缓存命中时用户看不到自动刷新，需要这个出口手动绕过 TTL。
                // 比如上传 / 删除完云端库立刻想确认列表对齐。
                Icon(
                    id = R.drawable.refresh,
                    modifier = Modifier
                        .clickable { viewModel.refreshUserState(forceRefresh = true) }
                        .padding(4.dp)
                        .size(18.dp),
                    contentDescription = "刷新云端库"
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 8.dp)
            )
        }

        // 云端库列表
        if (viewModel.myCloudLibraries.isEmpty() && !viewModel.isCloudLibrariesLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无云端命令库",
                    style = TextStyle(
                        color = CHelperTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                )
            }
        } else {
            viewModel.myCloudLibraries.forEachIndexed { index, library ->
                if (index > 0) Divider(padding = 0.dp)
                Row(
                    modifier = Modifier
                        .clickable {
                            library.id?.let {
                                navController.navigate(
                                    PublicLibraryShowScreenKey(id = it, isPrivate = true)
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
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = library.note ?: "",
                            style = TextStyle(
                                color = CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (library.hasPublicVersion == true) {
                        // 公开版与私有版不一致 → 显示"待同步"提醒（橙色）；
                        // 已同步 → 显示"已发布"（主题色）。后端权威。
                        if (library.hasUnsyncedChanges == true) {
                            Text(
                                text = "待同步",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = Color(0xFFE65100)
                                ),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        } else {
                            Text(
                                text = "已发布",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = CHelperTheme.colors.mainColor
                                ),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 容量进度条：单纯展示 used/limit 的视觉条
 * limit == -1 表示无限制，进度条按固定 10% 显示，文案换成"无限制"
 * 配额接近用满时颜色加深一档，给用户提个醒
 */
@Composable
private fun QuotaBar(
    used: Int,
    limit: Int,
    modifier: Modifier = Modifier
) {
    val unlimited = limit == -1
    val ratio = if (unlimited) {
        0.1f
    } else {
        (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }
    val barColor = when {
        unlimited -> CHelperTheme.colors.mainColor
        ratio >= 0.9f -> Color(0xFFD32F2F)
        ratio >= 0.7f -> Color(0xFFE65100)
        else -> CHelperTheme.colors.mainColor
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "容量",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
            Text(
                text = if (unlimited) "$used / 无限制" else "$used / $limit",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
        }
        Spacer(Modifier.height(4.dp))
        // 用 Box 做轨道+前景两层，比拉 LinearProgressIndicator 自定义颜色省事
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CHelperTheme.colors.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun LoginHintCard(navController: NavHostController) {
    // 未登录态的引导卡片。
    // 顶部主区块引导去登录；下方留一个常驻的"站内信"入口——之前砍掉
    // "我的" tab 的时候，访客访问站内信的入口跟着丢了，这里补回来。
    // 站内信里有面向所有人的公告，登录态不是看消息的前置条件
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color = CHelperTheme.colors.backgroundComponent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    navController.navigate(CPLUserScreenKey)
                }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "登录后可管理云端命令库",
                style = TextStyle(
                    color = CHelperTheme.colors.mainColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Divider(padding = 0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(MessageScreenKey) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                id = R.drawable.ic_mail,
                modifier = Modifier.size(18.dp),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "站内信",
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = CHelperTheme.colors.textMain,
                    fontSize = 14.sp
                )
            )
            Icon(
                id = R.drawable.chevron_right,
                modifier = Modifier.size(16.dp),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            id = icon,
            modifier = Modifier.size(24.dp),
            contentDescription = label
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = CHelperTheme.colors.textSecondary
            )
        )
    }
}

@Preview
@Composable
fun LocalLibraryListScreenLightThemePreview() {
    val viewModel = remember {
        LocalLibraryListViewModel()
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryListScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
fun LocalLibraryListScreenDarkThemePreview() {
    val viewModel = remember {
        LocalLibraryListViewModel()
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryListScreen(viewModel = viewModel)
    }
}
