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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.DividerVertical
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

@Composable
fun PublicLibraryShowScreen(
    id: Int,
    isPrivate: Boolean = false,
    navController: androidx.navigation.NavHostController? = null,
    viewModel: PublicLibraryShowViewModel = viewModel()
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // 主菜单对话框
    if (showMainMenu) {
        val menuItems = if (isPrivate) {
            arrayOf(
                "管理 ▸" to "manage",
                "逐行复制" to "line_copy",
                "关闭" to "close"
            )
        } else {
            arrayOf(
                "逐行复制" to "line_copy",
                "关闭" to "close"
            )
        }
        ChoosingDialog(
            onDismissRequest = { showMainMenu = false },
            data = menuItems,
            onChoose = { action ->
                when (action) {
                    "manage" -> showManageMenu = true
                    "line_copy" -> showLineCopyDialog = true
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
                    "release" -> showCaptchaDialog = true
                    "view_public" -> navController?.navigate(PublicLibraryShowScreenKey(id = id, isPrivate = false))
                    "sync" -> viewModel.library.id?.let { viewModel.syncToPublic(it) }
                    "edit" -> Toast.makeText(context, "编辑功能开发中", Toast.LENGTH_SHORT).show()
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
                        viewModel.deleteLibrary(it, onSuccess = {
                            navController?.popBackStack()
                        })
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
        val commands = remember(viewModel.library) {
            viewModel.library.content
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { 
                    it.isNotEmpty() && 
                    !it.startsWith("#") && 
                    !it.equals("###Function###", ignoreCase = true) &&
                    !it.equals("###End###", ignoreCase = true)
                }
                ?: emptyList()
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
                                modifier = Modifier.clickable { viewModel.loadFunction(id, isPrivate) },
                                style = TextStyle(color = CHelperTheme.colors.mainColor)
                            )
                        }
                    }
                }
                else -> {
                    // 分类行：COMMENT(#开头), COMMAND(普通指令), META(@开头，不显示)
                    val contents: List<Pair<String, String>> = remember(viewModel.library) {
                        viewModel.library.content
                            ?.split("\n")
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.mapNotNull {
                                when {
                                    it.equals("###Function###", ignoreCase = true) || it.equals("###End###", ignoreCase = true) -> null
                                    it.startsWith("@") -> null // @元数据行不在指令区显示
                                    it.startsWith("#") -> "comment" to it.substring(1).trim()
                                    else -> "command" to it
                                }
                            }
                            ?.filter { it.second.isNotEmpty() }
                            ?: listOf()
                    }
                    
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        // 元信息卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = CHelperTheme.colors.backgroundComponent)
                                .padding(15.dp)
                        ) {
                            // 作者
                            viewModel.library.author?.let { author ->
                                Row(modifier = Modifier.padding(bottom = 4.dp)) {
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
                                            color = CHelperTheme.colors.textMain,
                                            fontSize = 13.sp
                                        )
                                    )
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
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        text = "标签  ",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        ),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    androidx.compose.foundation.layout.FlowRow {
                                        tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .padding(end = 6.dp, bottom = 4.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CHelperTheme.colors.background)
                                                    .clickable { navController?.navigate(yancey.chelper.ui.LibrarySearchScreenKey(tag)) }
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
                            // 创建时间
                            viewModel.library.created_at?.takeIf { it.isNotBlank() }?.let { time ->
                                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        text = "时间  ",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = time,
                                        style = TextStyle(
                                            color = CHelperTheme.colors.textMain,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                            // 点赞
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                viewModel.library.like_count?.let { likes ->
                                    Icon(
                                        id = R.drawable.ic_heart,
                                        modifier = Modifier.size(14.dp),
                                        contentDescription = "likes"
                                    )
                                    Spacer(Modifier.defaultMinSize(minWidth = 4.dp))
                                    Text(
                                        text = "$likes",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.mainColor,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
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
                        
                        // 指令内容列表
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 15.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = CHelperTheme.colors.backgroundComponent)
                        ) {
                            items(contents) { content ->
                                val isCommand = content.first == "command"
                                Row(
                                    modifier = Modifier
                                        .padding(20.dp, 10.dp)
                                        .defaultMinSize(minHeight = 24.dp)
                                ) {
                                    Text(
                                        text = content.second,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.CenterVertically)
                                            .weight(1f),
                                        style = TextStyle(
                                            // 注释行用主题色，指令行用普通文字色
                                            color = if (!isCommand) CHelperTheme.colors.mainColor else CHelperTheme.colors.textMain,
                                        )
                                    )
                                    // 只有指令行显示复制按钮
                                    if (isCommand) {
                                        val scope = rememberCoroutineScope()
                                        Icon(
                                            id = R.drawable.copy,
                                            contentDescription = stringResource(R.string.common_icon_copy_content_description),
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .clickable {
                                                    scope.launch {
                                                        clipboard.setClipEntry(
                                                            ClipEntry(
                                                                ClipData.newPlainText(
                                                                    null,
                                                                    content.second
                                                                )
                                                            )
                                                        )
                                                    }
                                                }
                                                .padding(start = 5.dp)
                                                .size(24.dp)
                                        )
                                    }
                                }
                                Divider(padding = 0.dp)
                            }
                        }
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
    val scope = rememberCoroutineScope()
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
    
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
        ) {
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

@Preview
@Composable
fun PublicLibraryShowScreenLightThemePreview() {
    val viewModel: PublicLibraryShowViewModel = viewModel()
    viewModel.library = LibraryFunction().apply {
        name = "Test Library"
        author = "Test Author"
        note = "This is a test description"
        like_count = 42
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
        author = "Test Author"
        note = "This is a test description"
        like_count = 42
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
