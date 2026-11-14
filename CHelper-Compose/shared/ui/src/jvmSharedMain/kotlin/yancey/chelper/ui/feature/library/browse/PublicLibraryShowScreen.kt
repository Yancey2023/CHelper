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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.library.hasSameLocalContent
import yancey.chelper.data.library.toLocalImportedCopy
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.domain.library.parseMCDStructure
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.CaptchaDialog
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.dialog.MenuDialog
import yancey.chelper.ui.designsystem.dialog.ReportDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.library.components.LineCopyContent
import yancey.chelper.ui.feature.library.mcd.MCDContentView
import yancey.chelper.ui.navigation.LibrarySearchScreenKey
import yancey.chelper.ui.navigation.LibraryUploadScreenKey
import yancey.chelper.ui.navigation.LocalLibraryShowScreenKey
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey
import yancey.chelper.ui.navigation.UserProfileScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.LocalClientServices as LocalContext
import yancey.chelper.ui.platform.rememberCopyText

@Composable
fun PublicLibraryShowScreen(
    id: Int,
    isPrivate: Boolean = false,
    navController: NavHostController? = null,
    importToLocal: Boolean = false,
    viewModel: PublicLibraryShowViewModel = viewModel { PublicLibraryShowViewModel() },
) {
    val context = LocalContext.current
    val copyAll = rememberCopyText("已复制全部 MCD 源码")
    val coroutineScope = rememberCoroutineScope()

    // 读取设置
    val settingsDataStore = remember(context) { context.settings }
    val localCommandLabDataStore = remember(context) { context.libraries }
    val ambiguousLineDefault =
        settingsDataStore
            .ambiguousLineDefault()
            .collectAsStateWithLifecycle(initialValue = "comment")
    val isHideMetadataPreview =
        settingsDataStore.isHideMetadataPreview().collectAsStateWithLifecycle(initialValue = false)

    // 对话框状态
    var showMainMenu by remember { mutableStateOf(false) }
    var showManageMenu by remember { mutableStateOf(false) }
    var showLineCopyDialog by remember { mutableStateOf(false) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLocalImportConfirmDialog by remember { mutableStateOf(false) }
    var showLoongFlowPermissionDialog by remember { mutableStateOf(false) }
    var localImportPromptHandled by remember(id, isPrivate, importToLocal) { mutableStateOf(false) }

    fun openLoongFlowImport() {
        if (viewModel.library.id == null) return
        if (context.platform.loongFlowPermissionGranted()) {
            if (!context.platform.showLoongFlow(viewModel.library)) {
                ClientMessages.show("游龙窗口打开失败")
            }
        } else {
            showLoongFlowPermissionDialog = true
        }
    }

    fun shareCurrentLibrary() {
        val libraryId = viewModel.library.id ?: return
        if (!LoginUtil.isLoggedIn || LoginUtil.currentUser?.isGuest == true) {
            ClientMessages.show("请先登录正式账号再分享")
            return
        }
        if (isPrivate || viewModel.library.isPublish == false) {
            ClientMessages.show("仅公开库可生成分享链接")
            return
        }
        if (viewModel.isSharing) {
            ClientMessages.show("正在生成分享链接…")
            return
        }
        // 外链默认只打开云端库，不自动导入本地
        viewModel.createShareLink(libraryId, importToLocal = false)
    }

    viewModel.ensureLoaded(id, isPrivate)

    // 新分享默认不带 local 参数，只展示云端详情；显式 local=1 仍兼容旧导入链接。
    LaunchedEffect(
        importToLocal,
        viewModel.isLoading,
        viewModel.errorMessage,
        viewModel.library.id,
    ) {
        if (
            importToLocal &&
                !localImportPromptHandled &&
                !viewModel.isLoading &&
                viewModel.errorMessage == null &&
                viewModel.library.id == id
        ) {
            localImportPromptHandled = true
            showLocalImportConfirmDialog = true
        }
    }

    // 操作结果反馈
    DisposableEffect(viewModel.actionMessage) {
        viewModel.actionMessage?.let {
            ClientMessages.show(it)
            viewModel.actionMessage = null
        }
        onDispose {}
    }

    // 分享链接生成后：复制到剪贴板并拉起系统分享面板
    DisposableEffect(viewModel.pendingShareUrl) {
        viewModel.pendingShareUrl?.let { url ->
            viewModel.pendingShareUrl = null
            try {
                context.platform.shareText(url)
                ClientMessages.show("分享链接已复制")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ClientMessages.show("分享失败，请稍后重试")
            }
        }
        onDispose {}
    }

    // 删除成功后安全返回上一页——在 Composition 上下文中执行 popBackStack 避免时序崩溃
    DisposableEffect(viewModel.deleteSuccess) {
        if (viewModel.deleteSuccess) {
            viewModel.deleteSuccess = false
            navController?.popBackStack()
        }
        onDispose {}
    }

    // Release 成功后跳转到新公有版详情页
    DisposableEffect(viewModel.releasedPublicId) {
        viewModel.releasedPublicId?.let { publicId ->
            viewModel.releasedPublicId = null
            navController?.navigate(PublicLibraryShowScreenKey(id = publicId)) {
                // 把当前私有版详情页从栈里弹掉，防止 back 回去看到旧数据
                popUpTo(navController.currentDestination?.id ?: return@navigate) {
                    inclusive = true
                }
            }
        }
        onDispose {}
    }

    RootViewWithHeaderAndCopyright(
        title = viewModel.library.name ?: "加载中",
        headerRight = {
            Row {
                // 仅公开云端库可分享；私有库/未加载完成不展示入口
                if (
                    viewModel.library.id != null &&
                        !isPrivate &&
                        viewModel.library.isPublish != false
                ) {
                    Icon(
                        id = Res.drawable.share,
                        modifier =
                            Modifier.clickable { shareCurrentLibrary() }.padding(5.dp).size(24.dp),
                        contentDescription = "分享",
                    )
                }
                if (viewModel.library.id != null && context.platform.supportsLoongFlow) {
                    Icon(
                        id = Res.drawable.loong_flow_import,
                        modifier =
                            Modifier.clickable { openLoongFlowImport() }.padding(5.dp).size(24.dp),
                        contentDescription = "游龙导入",
                    )
                }
                Icon(
                    id = Res.drawable.more,
                    modifier = Modifier.clickable { showMainMenu = true }.padding(5.dp).size(24.dp),
                    contentDescription = "菜单",
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "加载中...",
                            style = TextStyle(color = CHelperTheme.colors.textSecondary),
                        )
                    }
                }

                viewModel.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.errorMessage ?: "加载失败",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点击重试",
                                modifier =
                                    Modifier.clickable { viewModel.loadFunction(id, isPrivate) },
                                style = TextStyle(color = CHelperTheme.colors.mainColor),
                            )
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                        // 元信息卡片
                        @OptIn(ExperimentalLayoutApi::class)
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 15.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color = CHelperTheme.colors.backgroundComponent)
                                    .padding(15.dp)
                        ) {
                            // 作者
                            viewModel.library.authorName?.let { author ->
                                val authorId = viewModel.library.author?.id
                                Row(
                                    modifier =
                                        Modifier.padding(bottom = 4.dp)
                                            .then(
                                                if (authorId != null)
                                                    Modifier.clip(RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            navController?.navigate(
                                                                UserProfileScreenKey(id = authorId)
                                                            )
                                                        }
                                                else Modifier
                                            ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "作者  ",
                                        style =
                                            TextStyle(
                                                color = CHelperTheme.colors.textSecondary,
                                                fontSize = 13.sp,
                                            ),
                                    )
                                    Text(
                                        text = author,
                                        style =
                                            TextStyle(
                                                color =
                                                    if (authorId != null)
                                                        CHelperTheme.colors.mainColor
                                                    else CHelperTheme.colors.textMain,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                    )
                                    // 作者专属头衔徽章
                                    viewModel.library.author
                                        ?.userTitle
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { title ->
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier =
                                                    Modifier.clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            CHelperTheme.colors.mainColor.copy(
                                                                alpha = 0.12f
                                                            )
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
                                                )
                                            }
                                        }
                                    // 可点击进入主页的箭头指示
                                    if (authorId != null) {
                                        Spacer(Modifier.width(4.dp))
                                        Image(
                                            painter =
                                                painterResource(
                                                    resource = Res.drawable.external_link
                                                ),
                                            contentDescription = "进入主页",
                                            modifier = Modifier.size(12.dp),
                                            colorFilter =
                                                ColorFilter.tint(CHelperTheme.colors.mainColor),
                                        )
                                    }
                                }
                            }
                            // 版本
                            viewModel.library.version
                                ?.takeIf { it.isNotBlank() }
                                ?.let { ver ->
                                    Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                        Text(
                                            text = "版本  ",
                                            style =
                                                TextStyle(
                                                    color = CHelperTheme.colors.textSecondary,
                                                    fontSize = 13.sp,
                                                ),
                                        )
                                        Text(
                                            text = ver,
                                            style =
                                                TextStyle(
                                                    color = CHelperTheme.colors.textMain,
                                                    fontSize = 13.sp,
                                                ),
                                        )
                                    }
                                }
                            // 标签
                            viewModel.library.tags
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { tags ->
                                    Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                        Text(
                                            text = "标签  ",
                                            style =
                                                TextStyle(
                                                    color = CHelperTheme.colors.textSecondary,
                                                    fontSize = 13.sp,
                                                ),
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            tags.forEach { tag ->
                                                Box(
                                                    modifier =
                                                        Modifier.clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                CHelperTheme.colors.background
                                                            )
                                                            .clickable {
                                                                navController?.navigate(
                                                                    LibrarySearchScreenKey(tag)
                                                                )
                                                            }
                                                            .padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp,
                                                            )
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style =
                                                            TextStyle(
                                                                color =
                                                                    CHelperTheme.colors.mainColor,
                                                                fontSize = 12.sp,
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 点赞按钮 — 可交互
                                Row(
                                    modifier =
                                        Modifier.padding(top = 4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                viewModel.library.id?.let {
                                                    viewModel.toggleLike(it)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val isLiked = viewModel.isLiked
                                    Image(
                                        painter =
                                            painterResource(
                                                if (isLiked) Res.drawable.heart_filled
                                                else Res.drawable.heart
                                            ),
                                        contentDescription = "点赞",
                                        modifier = Modifier.size(16.dp),
                                        colorFilter =
                                            ColorFilter.tint(
                                                if (isLiked) CHelperTheme.colors.mainColor
                                                else CHelperTheme.colors.textSecondary
                                            ),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${viewModel.likeCount}",
                                        style =
                                            TextStyle(
                                                color =
                                                    if (isLiked) CHelperTheme.colors.mainColor
                                                    else CHelperTheme.colors.textSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLiked) "已赞" else "点赞",
                                        style =
                                            TextStyle(
                                                color =
                                                    if (isLiked) CHelperTheme.colors.mainColor
                                                    else CHelperTheme.colors.textSecondary,
                                                fontSize = 12.sp,
                                            ),
                                    )
                                }
                                if (!isPrivate) {
                                    Spacer(Modifier.width(14.dp))
                                    val isFavorited = viewModel.isFavorited
                                    Row(
                                        modifier =
                                            Modifier.padding(top = 4.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    viewModel.library.id?.let {
                                                        viewModel.toggleFavorite(it)
                                                    }
                                                }
                                                .padding(vertical = 4.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Image(
                                            painter =
                                                painterResource(
                                                    if (isFavorited) Res.drawable.bookmark_filled
                                                    else Res.drawable.bookmark
                                                ),
                                            contentDescription = "收藏",
                                            modifier = Modifier.size(16.dp),
                                            colorFilter =
                                                ColorFilter.tint(
                                                    if (isFavorited) CHelperTheme.colors.mainColor
                                                    else CHelperTheme.colors.textSecondary
                                                ),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isFavorited) "已收藏" else "收藏",
                                            style =
                                                TextStyle(
                                                    color =
                                                        if (isFavorited)
                                                            CHelperTheme.colors.mainColor
                                                        else CHelperTheme.colors.textSecondary,
                                                    fontSize = 12.sp,
                                                ),
                                        )
                                    }
                                }
                            }
                            // 备注
                            viewModel.library.note
                                ?.takeIf { it.isNotBlank() }
                                ?.let { note ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = note,
                                        style =
                                            TextStyle(
                                                color = CHelperTheme.colors.textSecondary,
                                                fontSize = 12.sp,
                                            ),
                                    )
                                }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 视图切换指示条
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (viewModel.showRawSource) "MCD 源码" else "可视化预览",
                                style =
                                    TextStyle(
                                        fontSize = 12.sp,
                                        color = CHelperTheme.colors.textSecondary,
                                    ),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (viewModel.showRawSource) "切换到可视化" else "切换到源码",
                                modifier =
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            viewModel.showRawSource = !viewModel.showRawSource
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                style =
                                    TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = CHelperTheme.colors.mainColor,
                                    ),
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 内容区：可视化 / 原始源码
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            if (viewModel.showRawSource) {
                                // 原始源码视图
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .padding(horizontal = 15.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CHelperTheme.colors.backgroundComponent)
                                            .padding(12.dp)
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = viewModel.library.content ?: "",
                                        style =
                                            TextStyle(
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = CHelperTheme.colors.textMain,
                                            ),
                                    )
                                }
                            } else {
                                // MCD 可视化视图
                                // MCDContentView 已不再自带 LazyColumn，这里包一层 verticalScroll
                                // 让长命令库内容能继续滚动
                                Column(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 15.dp)
                                ) {
                                    MCDContentView(
                                        content = viewModel.library.content,
                                        ambiguousDefault = ambiguousLineDefault.value,
                                        showMetadata = !isHideMetadataPreview.value,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 所有dialog必须在RootView后声明，因为AppDialog是纯Box覆盖层而非真popup
    // Compose中后声明的节点z-order更高，放前面会被RootView压住导致点击穿透不到dialog

    // 主菜单对话框
    if (showMainMenu) {
        val menuItems =
            buildList {
                    if (isPrivate) add("管理 ▸" to "manage")
                    // 收藏仅对公有库有意义（跳公有详情）；私有库走本地草稿即可
                    if (!isPrivate) add((if (viewModel.isFavorited) "取消收藏" else "收藏") to "favorite")
                    add("逐行复制" to "line_copy")
                    add("复制全部 MCD 源码" to "copy_all")
                    add((if (viewModel.showRawSource) "查看可视化" else "查看源码") to "toggle_view")
                    // 公有库才显示举报：私有库是用户自己的，没必要给自己的库做举报入口
                    if (!isPrivate) add("举报" to "report")
                    add("关闭" to "close")
                }
                .toTypedArray()
        MenuDialog(
            onDismissRequest = { showMainMenu = false },
            data = menuItems,
            onChoose = { action ->
                when (action) {
                    "manage" -> showManageMenu = true
                    "favorite" -> viewModel.library.id?.let { viewModel.toggleFavorite(it) }
                    "line_copy" -> showLineCopyDialog = true
                    "copy_all" -> {
                        viewModel.library.content?.let { mcd -> copyAll(mcd) }
                    }

                    "toggle_view" -> viewModel.showRawSource = !viewModel.showRawSource

                    "report" -> showReportDialog = true
                }
            },
        )
    }

    // 管理子菜单对话框
    if (showManageMenu) {
        val hasPublic = viewModel.library.hasPublicVersion == true
        val manageItems =
            buildList {
                    if (hasPublic) {
                        add("查看公开版本" to "view_public")
                        add("同步到公开库" to "sync")
                    } else {
                        add("发布到公开市场" to "release")
                    }
                    add("编辑" to "edit")
                    add("删除私有库" to "delete")
                    add("◂ 返回" to "back")
                }
                .toTypedArray()
        MenuDialog(
            onDismissRequest = { showManageMenu = false },
            data = manageItems,
            onChoose = { action ->
                when (action) {
                    "release" -> {
                        val user = LoginUtil.currentUser
                        if (user != null && (user.tier ?: 0) >= 2) {
                            viewModel.library.id?.let { viewModel.releaseToPublic(it, "") }
                        } else {
                            showCaptchaDialog = true
                        }
                    }

                    "view_public" ->
                        navController?.navigate(
                            PublicLibraryShowScreenKey(id = id, isPrivate = false)
                        )

                    "sync" -> viewModel.library.id?.let { viewModel.syncToPublic(it) }
                    "edit" -> {
                        navController?.navigate(LibraryUploadScreenKey(editLibraryId = id))
                    }

                    "delete" -> showDeleteConfirmDialog = true
                    "back" -> showMainMenu = true
                }
            },
        )
    }

    if (showDeleteConfirmDialog) {
        MenuDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            data = arrayOf("确认删除 (不可恢复)" to "confirm", "取消" to "cancel"),
            onChoose = { action ->
                if (action == "confirm") {
                    viewModel.library.id?.let { viewModel.deleteLibrary(it) }
                }
                showDeleteConfirmDialog = false
            },
        )
    }

    if (showLocalImportConfirmDialog) {
        MenuDialog(
            onDismissRequest = { showLocalImportConfirmDialog = false },
            data = arrayOf("导入到本地库" to "confirm", "仅查看云端详情" to "cancel"),
            onChoose = { action ->
                showLocalImportConfirmDialog = false
                if (action != "confirm") return@MenuDialog
                coroutineScope.launch {
                    val imported = viewModel.library.toLocalImportedCopy()
                    val (localEntryId, alreadyExists) =
                        withContext(Dispatchers.IO) {
                            val existing =
                                localCommandLabDataStore
                                    .localLibraryFunctions()
                                    .first()
                                    .firstOrNull { it.hasSameLocalContent(imported) }
                            if (existing != null) {
                                requireNotNull(existing.localEntryId) to true
                            } else {
                                localCommandLabDataStore.addLocalLibraryFunction(imported) to false
                            }
                        }
                    ClientMessages.show(if (alreadyExists) "本地库已有相同内容，已打开" else "已导入本地库")
                    navController?.navigate(
                        LocalLibraryShowScreenKey(localEntryId = localEntryId)
                    ) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
        )
    }

    if (showLoongFlowPermissionDialog) {
        IsConfirmDialog(
            onDismissRequest = { showLoongFlowPermissionDialog = false },
            content = "游龙导入需要悬浮窗权限，请进入设置授权。",
            confirmText = "打开设置",
            onConfirm = {
                showLoongFlowPermissionDialog = false
                context.platform.requestLoongFlowPermission { granted ->
                    ClientMessages.show(
                        if (granted) {
                            "悬浮窗权限获取成功，请再次点击游龙导入"
                        } else {
                            "悬浮窗权限获取失败"
                        }
                    )
                }
            },
            onCancel = { showLoongFlowPermissionDialog = false },
        )
    }

    // 发布验证码流程
    if (showCaptchaDialog) {
        CaptchaDialog(
            action = "publish",
            onDismissRequest = { showCaptchaDialog = false },
            onSuccess = { specialCode ->
                viewModel.library.id?.let { viewModel.releaseToPublic(it, specialCode) }
            },
        )
    }

    // 逐行复制对话框
    if (showLineCopyDialog) {
        val commands =
            remember(viewModel.library, ambiguousLineDefault.value) {
                // 逐行复制只要结构，别走高亮路径拖慢弹窗
                val parsed =
                    parseMCDStructure(viewModel.library.content, ambiguousLineDefault.value)
                parsed.chains.flatMap { chain ->
                    chain.items.mapNotNull { item ->
                        when (item) {
                            is ChainItem.Block -> item.block.command.takeIf { it.isNotEmpty() }
                            is ChainItem.RawCommand -> item.command.takeIf { it.isNotEmpty() }
                            is ChainItem.Comment -> null
                        }
                    }
                }
            }
        if (commands.isEmpty()) {
            DisposableEffect(Unit) {
                ClientMessages.show("没有可复制的命令")
                showLineCopyDialog = false
                onDispose {}
            }
        } else {
            LineCopyDialog(commands = commands, onDismiss = { showLineCopyDialog = false })
        }
    }

    if (showReportDialog) {
        // 用 uuid 作为 target_id，保证不同版本（同 uuid 的私有/公开）能在后端聚合
        // 这里只举报公有库，所以 target_type=library 没问题
        val targetUuid = viewModel.library.uuid
        val targetName = viewModel.library.name
        ReportDialog(
            onDismissRequest = { showReportDialog = false },
            title = "举报命令库",
            targetDescription = targetName,
            onConfirm = { reason ->
                if (targetUuid.isNullOrBlank()) {
                    ClientMessages.show("无法定位举报目标")
                    return@ReportDialog
                }
                coroutineScope.launch {
                    try {
                        val request =
                            CommandLabUserService.ReportRequest().apply {
                                targetType = "library"
                                targetId = targetUuid
                                this.reason = reason
                            }
                        val resp =
                            withContext(Dispatchers.IO) {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.submitReport(request)
                            }
                        ClientMessages.show(
                            resp.message ?: if (resp.isSuccess()) "举报已提交" else "举报失败"
                        )
                    } catch (e: Exception) {
                        ClientMessages.show("网络错误: ${e.message}")
                    }
                }
            },
        )
    }
}

/** 逐行复制对话框：逐条展示命令并自动复制到剪贴板 用户点击下一条前进并复制下一条，点击完成关闭 */
@Composable
fun LineCopyDialog(commands: List<String>, onDismiss: () -> Unit) {
    val copy = rememberCopyText()
    LineCopyContent(commands, onDismiss, onCopy = copy)
}
