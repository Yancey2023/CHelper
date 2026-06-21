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

package yancey.chelper.ui.library.profile

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hjq.toast.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.network.library.data.formatUnixTime
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.ReportDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Text

@Composable
fun UserProfileScreen(
    paramId: Int,
    navController: NavHostController?,
    viewModel: UserProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    // 仅在浏览他人主页时启用举报入口；自己看自己的页面不显示
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var actionDialogTarget by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val listState = rememberLazyListState()

    val layoutInfo = listState.layoutInfo
    val totalItemsNumber = layoutInfo.totalItemsCount
    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

    DisposableEffect(lastVisibleItemIndex, totalItemsNumber) {
        if (totalItemsNumber > 0 && lastVisibleItemIndex >= totalItemsNumber - 3) {
            if (selectedTab == 0 || viewModel.currentUserId != paramId) {
                if (viewModel.hasMorePublic && !viewModel.isLoadingPublic) {
                    viewModel.loadPublicLibraries(true)
                }
            } else {
                if (viewModel.hasMorePrivate && !viewModel.isLoadingPrivate) {
                    viewModel.loadPrivateLibraries(true)
                }
            }
        }
        onDispose { }
    }

    viewModel.ensureProfileLoaded(paramId)

    DisposableEffect(viewModel.updateSuccessMessage, viewModel.updateErrorMessage) {
        viewModel.updateSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessages()
        }
        viewModel.updateErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessages()
        }
        onDispose { }
    }

    RootViewWithHeaderAndCopyright(
        title = "创作者主页",
        headerRight = {
            if (viewModel.currentUserId == paramId) {
                Image(
                    painter = painterResource(id = R.drawable.pencil),
                    contentDescription = "编辑资料",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showEditDialog = true }
                        .padding(2.dp),
                    colorFilter = ColorFilter.tint(CHelperTheme.colors.textMain)
                )
            } else {
                // 浏览别人 → 举报入口
                Image(
                    painter = painterResource(id = R.drawable.alert_triangle),
                    contentDescription = "举报用户",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showReportDialog = true }
                        .padding(2.dp),
                    colorFilter = ColorFilter.tint(CHelperTheme.colors.textMain)
                )
            }
        }
    ) {
        if (viewModel.isLoading && viewModel.userProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", style = TextStyle(color = CHelperTheme.colors.mainColor))
            }
        } else if (viewModel.errorMessage != null && viewModel.userProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Error: ${viewModel.errorMessage}",
                    style = TextStyle(color = CHelperTheme.colors.textSecondary)
                )
            }
        } else if (viewModel.userProfile != null) {
            val user = viewModel.userProfile!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                item {
                    ProfileHeader(user)
                }

                if (viewModel.currentUserId == paramId) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = "已上架的指令",
                                modifier = Modifier
                                    .clickable { selectedTab = 0 }
                                    .padding(8.dp),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary
                                )
                            )
                            Text(
                                text = "私有云库",
                                modifier = Modifier
                                    .clickable { selectedTab = 1 }
                                    .padding(8.dp),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary
                                )
                            )
                        }
                    }
                    if (selectedTab == 1) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "私有云库容量",
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CHelperTheme.colors.textMain
                                        )
                                    )
                                    Text(
                                        text = if (viewModel.quotaLimit == -1) "${viewModel.quotaUsed} / 无限制" else "${viewModel.quotaUsed} / ${viewModel.quotaLimit}",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = CHelperTheme.colors.textSecondary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val progress = if (viewModel.quotaLimit > 0) {
                                    (viewModel.quotaUsed.toFloat() / viewModel.quotaLimit.toFloat()).coerceIn(
                                        0f,
                                        1f
                                    )
                                } else {
                                    0.1f // 无限制时默认显示 10%
                                }
                                val progressColor =
                                    if (progress > 0.9f && viewModel.quotaLimit > 0) Color(
                                        0xFFE53935
                                    ) else CHelperTheme.colors.mainColor
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(CHelperTheme.colors.textSecondary.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(progressColor)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "最新公开命令库",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CHelperTheme.colors.textMain
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                if (selectedTab == 0 || viewModel.currentUserId != paramId) {
                    if (viewModel.isLoadingPublic && viewModel.publicLibraries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "加载中...",
                                    style = TextStyle(color = CHelperTheme.colors.mainColor)
                                )
                            }
                        }
                    } else if (viewModel.publicLibraries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "该用户还没有公开发布过任何命令库。",
                                    style = TextStyle(color = CHelperTheme.colors.textSecondary)
                                )
                            }
                        }
                    } else {
                        items(viewModel.publicLibraries) { func ->
                            ProfileLibraryItem(
                                library = func,
                                isOwner = viewModel.currentUserId == paramId,
                                isPrivate = false,
                                onUnpublish = {
                                    func.id?.let { actionDialogTarget = Pair(it, true) }
                                },
                                onDelete = null
                            ) {
                                func.id?.let {
                                    navController?.navigate(PublicLibraryShowScreenKey(id = it))
                                }
                            }
                        }
                        if (viewModel.isLoadingPublic) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "加载更多中...",
                                        style = TextStyle(
                                            color = CHelperTheme.colors.mainColor,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (viewModel.isLoadingPrivate && viewModel.privateLibraries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "加载中...",
                                    style = TextStyle(color = CHelperTheme.colors.mainColor)
                                )
                            }
                        }
                    } else if (viewModel.privateLibraries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "云库中暂未缓存任何私有记录。",
                                    style = TextStyle(color = CHelperTheme.colors.textSecondary)
                                )
                            }
                        }
                    } else {
                        items(viewModel.privateLibraries) { func ->
                            ProfileLibraryItem(
                                library = func,
                                isOwner = true,
                                isPrivate = true,
                                onUnpublish = null,
                                onDelete = {
                                    func.id?.let { actionDialogTarget = Pair(it, false) }
                                }
                            ) {
                                func.id?.let {
                                    navController?.navigate(
                                        PublicLibraryShowScreenKey(
                                            id = it,
                                            isPrivate = true
                                        )
                                    )
                                }
                            }
                        }
                        if (viewModel.isLoadingPrivate) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "加载更多中...",
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
            }
        }
    }

    if (showEditDialog && viewModel.userProfile != null) {
        val context = LocalContext.current
        // 自己的主页才会渲染编辑入口（外层已判断），这里复用一个 photoPicker
        // 让"编辑资料"对话框内嵌"从相册选择"按钮可用
        val avatarPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                uri?.let {
                    try {
                        val cr = context.contentResolver
                        cr.openInputStream(it)?.use { input ->
                            val bytes = input.readBytes()
                            val mimeType = cr.getType(it) ?: "image/jpeg"
                            viewModel.uploadAvatar(bytes, mimeType)
                        }
                    } catch (e: Exception) {
                        Toaster.show("读取图片失败: ${e.message}")
                    }
                }
            }
        )
        EditProfileDialog(
            user = viewModel.userProfile!!,
            isUpdating = viewModel.isUpdating,
            onDismiss = { showEditDialog = false },
            onSave = { nickname, avatar, homepage, signature ->
                viewModel.updateProfile(nickname, avatar, homepage, signature) {
                    showEditDialog = false
                }
            },
            onPickAvatarImage = {
                if (!viewModel.isUploadingAvatar) {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            isUploadingAvatar = viewModel.isUploadingAvatar,
            currentAvatarUrl = viewModel.userProfile?.avatarUrl
        )
    }

    actionDialogTarget?.let { (targetId, isPublic) ->
        IsConfirmDialog(
            title = if (isPublic) "下架公开库" else "删除私有库",
            content = if (isPublic) "确定要下架这个公开市场的命令库吗？私有云库内的原始文本不会受影响。" else "确定要从云端删除这个私有库草稿吗？此操作不可逆。",
            onDismissRequest = { actionDialogTarget = null },
            cancelText = "取消",
            onCancel = { actionDialogTarget = null },
            confirmText = "确定",
            onConfirm = {
                viewModel.deleteOrUnpublishLibrary(targetId, isPublic)
                actionDialogTarget = null
            }
        )
    }

    if (showReportDialog && viewModel.userProfile != null) {
        val target = viewModel.userProfile!!
        ReportDialog(
            onDismissRequest = { showReportDialog = false },
            title = "举报用户",
            targetDescription = target.nickname ?: "用户 #${target.id}",
            onConfirm = { reason ->
                val uid = target.id ?: return@ReportDialog
                coroutineScope.launch {
                    try {
                        val request = CommandLabUserService
                            .ReportRequest().apply {
                                targetType = "user"
                                targetId = uid.toString()
                                this.reason = reason
                            }
                        val resp =
                            withContext(Dispatchers.IO) {
                                ServiceManager.COMMAND_LAB_USER_SERVICE
                                    .submitReport(request)
                            }
                        Toaster.show(
                            resp.message ?: if (resp.isSuccess()) "举报已提交" else "举报失败"
                        )
                    } catch (e: Exception) {
                        Toaster.show("网络错误: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(user: UserProfileData) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = user.avatarUrl ?: "https://abyssous.site/avatar/${user.id}",
            contentDescription = "Avatar",
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CHelperTheme.colors.backgroundComponent),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_user),
            error = painterResource(id = R.drawable.ic_user)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = user.nickname ?: "User",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CHelperTheme.colors.textMain
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val tier = user.tier ?: 0
            val badgeName = "Tier $tier"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (tier > 0) CHelperTheme.colors.mainColor.copy(alpha = 0.15f) else CHelperTheme.colors.backgroundComponent)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeName,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = if (tier > 0) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            if (tier >= 2) {
                Spacer(modifier = Modifier.width(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_verified_advanced),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            } else if (tier == 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_verified_normal),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 用户专属头衔
        if (!user.userTitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = user.userTitle!!,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.mainColor,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        if (!user.signature.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "\"${user.signature}\"",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = CHelperTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            )
        }

        if (!user.homepage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, user.homepage!!.toUri())
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.external_link),
                    contentDescription = "Website",
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(CHelperTheme.colors.mainColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "个人网站",
                    style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.mainColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${user.totalPublicFunctions ?: 0}",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CHelperTheme.colors.textMain
                    )
                )
                Text(
                    "公开作品",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${user.totalLikes ?: 0}",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CHelperTheme.colors.textMain
                    )
                )
                Text(
                    "累计获赞",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
            }
        }
    }
}

@Composable
private fun ProfileLibraryItem(
    library: LibraryFunction,
    isOwner: Boolean = false,
    isPrivate: Boolean = false,
    onUnpublish: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .run {
                if ((library.likeCount ?: 0) >= 10 && !isPrivate) this.border(
                    1.dp,
                    CHelperTheme.colors.mainColor.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                ) else this
            }
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = library.name ?: "未命名",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CHelperTheme.colors.textMain
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (isPrivate) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (library.hasPublicVersion == true) CHelperTheme.colors.mainColor.copy(
                                alpha = 0.2f
                            ) else CHelperTheme.colors.background
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (library.hasPublicVersion == true) "已关联公开" else "完全私有",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = if (library.hasPublicVersion == true) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            library.version?.takeIf { it.isNotBlank() }?.let { ver ->
                Text(
                    text = "v$ver",
                    style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
                )
            }
        }
        library.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.replace("\n", " "),
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isPrivate) {
                Image(
                    painter = painterResource(id = R.drawable.heart_filled),
                    contentDescription = "Likes",
                    modifier = Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${library.likeCount ?: 0}",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = library.createdAt.formatUnixTime(true),
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isOwner) {
                if (!isPrivate && onUnpublish != null) {
                    Text(
                        text = "下架",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFE53935)),
                        modifier = Modifier
                            .clickable { onUnpublish() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else if (isPrivate && onDelete != null) {
                    Text(
                        text = "删除草稿",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFE53935)),
                        modifier = Modifier
                            .clickable { onDelete() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
