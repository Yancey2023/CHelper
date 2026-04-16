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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.Json
import yancey.chelper.R
import yancey.chelper.network.library.data.AuthorInfo
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.CPLUploadScreenKey
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.UserProfileScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.DividerVertical
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.library.mcd.ChainItem
import yancey.chelper.ui.library.mcd.MCDContentView
import yancey.chelper.ui.library.mcd.parseMCD

@Composable
fun PublicLibraryShowScreen(
    id: Int,
    isPrivate: Boolean = false,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: PublicLibraryShowViewModel = viewModel()
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    // 读取设置
    val settingsDataStore = remember(context) { yancey.chelper.data.SettingsDataStore(context) }
    val ambiguousLineDefault = settingsDataStore.ambiguousLineDefault()
        .collectAsState(initial = "comment")
    val isHideMetadataPreview = settingsDataStore.isHideMetadataPreview()
        .collectAsState(initial = false)

    // 对话框状态
    var showMainMenu by remember { mutableStateOf(false) }
    var showManageMenu by remember { mutableStateOf(false) }
    var showLineCopyDialog by remember { mutableStateOf(false) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(id, isPrivate) {
        viewModel.loadFunction(id, isPrivate)
    }

    // 操作结果反馈
    LaunchedEffect(viewModel.actionMessage) {
        viewModel.actionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.actionMessage = null
        }
    }

    // 删除成功后安全返回上一页——在 Composition 上下文中执行 popBackStack 避免时序崩溃
    LaunchedEffect(viewModel.deleteSuccess) {
        if (viewModel.deleteSuccess) {
            viewModel.deleteSuccess = false
            navController?.popBackStack()
        }
    }

    // Release 成功后跳转到新公有版详情页
    LaunchedEffect(viewModel.releasedPublicId) {
        viewModel.releasedPublicId?.let { publicId ->
            viewModel.releasedPublicId = null
            navController?.navigate(PublicLibraryShowScreenKey(id = publicId)) {
                // 把当前私有版详情页从栈里弹掉，防止 back 回去看到旧数据
                popUpTo(navController.currentDestination?.id ?: return@navigate) {
                    inclusive = true
                }
            }
        }
    }

    // 主菜单对话框
    if (showMainMenu) {
        val menuItems = buildList {
            if (isPrivate) add("管理 ▸" to "manage")
            add("逐行复制" to "line_copy")
            add("复制全部 MCD 源码" to "copy_all")
            add((if (viewModel.showRawSource) "查看可视化" else "查看源码") to "toggle_view")
            add("关闭" to "close")
        }.toTypedArray()
        ChoosingDialog(
            onDismissRequest = { showMainMenu = false },
            data = menuItems,
            onChoose = { action ->
                when (action) {
                    "manage" -> showManageMenu = true
                    "line_copy" -> showLineCopyDialog = true
                    "copy_all" -> {
                        viewModel.library.content?.let { mcd ->
                            val clip =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("MCD", mcd))
                            Toast.makeText(context, "已复制全部 MCD 源码", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    "toggle_view" -> viewModel.showRawSource = !viewModel.showRawSource
                }
            }
        )
    }

    // 管理子菜单对话框
    if (showManageMenu) {
        val hasPublic = viewModel.library.hasPublicVersion == true
        val manageItems = buildList {
            if (hasPublic) {
                add("查看公开版本" to "view_public")
                add("同步到公开库" to "sync")
            } else {
                add("发布到公开市场" to "release")
            }
            add("编辑" to "edit")
            add("删除私有库" to "delete")
            add("◂ 返回" to "back")
        }.toTypedArray()
        ChoosingDialog(
            onDismissRequest = { showManageMenu = false },
            data = manageItems,
            onChoose = { action ->
                when (action) {
                    "release" -> {
                        val user = yancey.chelper.network.library.util.LoginUtil.currentUser
                        if (user != null && (user.tier ?: 0) >= 2) {
                            viewModel.library.id?.let { viewModel.releaseToPublic(it, "") }
                        } else {
                            showCaptchaDialog = true
                        }
                    }
                    "view_public" -> navController?.navigate(
                        PublicLibraryShowScreenKey(
                            id = id,
                            isPrivate = false
                        )
                    )

                    "sync" -> viewModel.library.id?.let { viewModel.syncToPublic(it) }
                    "edit" -> {
                        val libJson =
                            Json.encodeToString(LibraryFunction.serializer(), viewModel.library)
                        navController?.navigate(
                            CPLUploadScreenKey(
                                editLibraryId = id,
                                editLibraryJson = libJson
                            )
                        )
                    }

                    "delete" -> showDeleteConfirmDialog = true
                    "back" -> showMainMenu = true
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        ChoosingDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            data = arrayOf("确认删除 (不可恢复)" to "confirm", "取消" to "cancel"),
            onChoose = { action ->
                if (action == "confirm") {
                    viewModel.library.id?.let {
                        viewModel.deleteLibrary(it)
                    }
                }
                showDeleteConfirmDialog = false
            }
        )
    }

    // 发布验证码流程
    if (showCaptchaDialog) {
        CaptchaDialog(
            action = "publish",
            onDismissRequest = { showCaptchaDialog = false },
            onSuccess = { specialCode ->
                viewModel.library.id?.let { viewModel.releaseToPublic(it, specialCode) }
            }
        )
    }

    // 逐行复制对话框
    if (showLineCopyDialog) {
        val commands = remember(viewModel.library, ambiguousLineDefault.value) {
            val parsed = parseMCD(viewModel.library.content, ambiguousLineDefault.value)
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
            LaunchedEffect(Unit) {
                Toast.makeText(context, "没有可复制的命令", Toast.LENGTH_SHORT).show()
                showLineCopyDialog = false
            }
        } else {
            LineCopyDialog(
                commands = commands,
                onDismiss = { showLineCopyDialog = false }
            )
        }
    }

    RootViewWithHeaderAndCopyright(
        title = viewModel.library.name ?: "加载中",
        headerRight = {
            Icon(
                id = R.drawable.more,
                modifier = Modifier
                    .clickable { showMainMenu = true }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = "菜单"
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载中...",
                            style = TextStyle(color = CHelperTheme.colors.textSecondary)
                        )
                    }
                }

                viewModel.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.errorMessage ?: "加载失败",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点击重试",
                                modifier = Modifier.clickable {
                                    viewModel.loadFunction(
                                        id,
                                        isPrivate
                                    )
                                },
                                style = TextStyle(color = CHelperTheme.colors.mainColor)
                            )
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ━━━ 元信息卡片 ━━━
                        @OptIn(ExperimentalLayoutApi::class)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = CHelperTheme.colors.backgroundComponent)
                                .padding(15.dp)
                        ) {
                            // 作者
                            viewModel.library.authorName?.let { author ->
                                val authorId = viewModel.library.author?.id
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .then(
                                            if (authorId != null) Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    navController?.navigate(UserProfileScreenKey(id = authorId))
                                                }
                                            else Modifier
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "作者  ",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = author,
                                        style = TextStyle(
                                            color = if (authorId != null) CHelperTheme.colors.mainColor else CHelperTheme.colors.textMain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    // 作者专属头衔徽章
                                    viewModel.library.author?.userTitle?.takeIf { it.isNotBlank() }
                                        ?.let { title ->
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        CHelperTheme.colors.mainColor.copy(
                                                            alpha = 0.12f
                                                        )
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = title,
                                                    style = TextStyle(
                                                        fontSize = 10.sp,
                                                        color = CHelperTheme.colors.mainColor,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            }
                                        }
                                    // 可点击进入主页的箭头指示
                                    if (authorId != null) {
                                        Spacer(Modifier.width(4.dp))
                                        Image(
                                            painter = painterResource(id = R.drawable.external_link),
                                            contentDescription = "进入主页",
                                            modifier = Modifier.size(12.dp),
                                            colorFilter = ColorFilter.tint(CHelperTheme.colors.mainColor)
                                        )
                                    }
                                }
                            }
                            // 版本
                            viewModel.library.version?.takeIf { it.isNotBlank() }?.let { ver ->
                                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        text = "版本  ",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = ver,
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textMain,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                            // 标签
                            viewModel.library.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        text = "标签  ",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        ),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CHelperTheme.colors.background)
                                                    .clickable {
                                                        navController?.navigate(
                                                            yancey.chelper.ui.LibrarySearchScreenKey(
                                                                tag
                                                            )
                                                        )
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = TextStyle(
                                                        color = CHelperTheme.colors.mainColor,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 点赞按钮 — 可交互
                            Row(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.library.id?.let { viewModel.toggleLike(it) }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isLiked = viewModel.isLiked
                                Image(
                                    painter = painterResource(
                                        if (isLiked) R.drawable.heart_filled else R.drawable.heart
                                    ),
                                    contentDescription = "点赞",
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (isLiked) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.textSecondary
                                    )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${viewModel.likeCount}",
                                    style = TextStyle(
                                        color = if (isLiked) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isLiked) "已赞" else "点赞",
                                    style = TextStyle(
                                        color = if (isLiked) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            // 备注
                            viewModel.library.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = note,
                                    style = TextStyle(
                                        color = CHelperTheme.colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // ━━━ 视图切换指示条 ━━━
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.showRawSource) "MCD 源码" else "可视化预览",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = CHelperTheme.colors.textSecondary
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (viewModel.showRawSource) "切换到可视化" else "切换到源码",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        viewModel.showRawSource = !viewModel.showRawSource
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CHelperTheme.colors.mainColor
                                )
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // ━━━ 内容区：可视化 / 原始源码 ━━━
                        if (viewModel.showRawSource) {
                            // 原始源码视图
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CHelperTheme.colors.backgroundComponent)
                                    .padding(12.dp)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = viewModel.library.content ?: "",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CHelperTheme.colors.textMain
                                    )
                                )
                            }
                        } else {
                            // MCD 可视化视图
                            MCDContentView(
                                content = viewModel.library.content,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp),
                                ambiguousDefault = ambiguousLineDefault.value,
                                showMetadata = !isHideMetadataPreview.value
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * 逐行复制对话框：逐条展示命令并自动复制到剪贴板。
 * 用户点击"下一条"前进并复制下一条，点击"完成"关闭。
 */
@Composable
private fun LineCopyDialog(
    commands: List<String>,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(0) }

    // 自动复制当前命令
    LaunchedEffect(currentIndex) {
        if (currentIndex < commands.size) {
            clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText(null, commands[currentIndex]))
            )
            Toast.makeText(
                context,
                "已复制 (${currentIndex + 1}/${commands.size})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    CustomDialog(onDismissRequest = onDismiss) {
        DialogContainer(
            backgroundNoTranslate = true
        ) {
            Column(modifier = Modifier)
            {
                // 标题
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 10.dp),
                    text = "逐行复制 (${currentIndex + 1}/${commands.size})",
                    style = TextStyle(
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                )
                // 当前命令预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (currentIndex < commands.size) commands[currentIndex] else "",
                        style = TextStyle(
                            color = CHelperTheme.colors.textMain,
                            fontSize = 14.sp
                        )
                    )
                }
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 4.dp, 0.dp, 8.dp),
                    text = "已自动复制到剪贴板",
                    style = TextStyle(
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = CHelperTheme.colors.textSecondary
                    )
                )
                Divider(0.dp)
                // 底部按钮行
                Row(Modifier.height(45.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { onDismiss() }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = "关闭",
                            style = TextStyle(
                                fontSize = 20.sp,
                                color = CHelperTheme.colors.mainColor,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    DividerVertical(0.dp)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable {
                                if (currentIndex < commands.size - 1) {
                                    currentIndex++
                                } else {
                                    onDismiss()
                                }
                            }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = if (currentIndex < commands.size - 1) "下一条 ▸" else "全部完成 ✓",
                            style = TextStyle(
                                fontSize = 20.sp,
                                color = CHelperTheme.colors.mainColor,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PublicLibraryShowScreenLightThemePreview() {
    val viewModel: PublicLibraryShowViewModel = viewModel()
    viewModel.library = LibraryFunction().apply {
        name = "Test Library"
        author = AuthorInfo(name = "Test Author")
        note = "This is a test description"
        likeCount = 42
        content = buildString {
            for (i in 1..10) {
                append("# Command${i * 2 - 1}\n")
                append("Description${i * 2}\n")
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        PublicLibraryShowScreen(id = 1, viewModel = viewModel)
    }
}

@Preview
@Composable
fun PublicLibraryShowScreenDarkThemePreview() {
    val viewModel: PublicLibraryShowViewModel = viewModel()
    viewModel.library = LibraryFunction().apply {
        name = "Test Library"
        author = AuthorInfo(name = "Test Author")
        note = "This is a test description"
        likeCount = 42
        content = buildString {
            for (i in 1..10) {
                append("# Command${i * 2 - 1}\n")
                append("Description${i * 2}\n")
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        PublicLibraryShowScreen(id = 1, viewModel = viewModel)
    }
}
