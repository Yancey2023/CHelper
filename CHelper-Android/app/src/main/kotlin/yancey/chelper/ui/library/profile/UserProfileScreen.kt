package yancey.chelper.ui.library.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import yancey.chelper.ui.common.widget.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright

@Composable
fun UserProfileScreen(
    paramId: Int,
    navController: NavHostController?,
    viewModel: UserProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var actionDialogTarget by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    LaunchedEffect(paramId) {
        viewModel.loadProfile(paramId)
    }

    LaunchedEffect(viewModel.updateSuccessMessage, viewModel.updateErrorMessage) {
        viewModel.updateSuccessMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessages()
        }
        viewModel.updateErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessages()
        }
    }

    if (showEditDialog && viewModel.userProfile != null) {
        EditProfileDialog(
            user = viewModel.userProfile!!,
            isUpdating = viewModel.isUpdating,
            onDismiss = { showEditDialog = false },
            onSave = { nickname, avatar, homepage, signature ->
                viewModel.updateProfile(nickname, avatar, homepage, signature) {
                    showEditDialog = false
                }
            }
        )
    }

    actionDialogTarget?.let { (targetId, isPublic) ->
        yancey.chelper.ui.common.dialog.IsConfirmDialog(
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
            }
        }
    ) {
        if (viewModel.isLoading && viewModel.userProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", style = TextStyle(color = CHelperTheme.colors.mainColor))
            }
        } else if (viewModel.errorMessage != null && viewModel.userProfile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${viewModel.errorMessage}", style = TextStyle(color = CHelperTheme.colors.textSecondary))
            }
        } else if (viewModel.userProfile != null) {
            val user = viewModel.userProfile!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                    if (user.recentFunctions.isNullOrEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("该用户还没有公开发布过任何命令库。", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                            }
                        }
                    } else {
                        items(user.recentFunctions!!) { func ->
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
                    }
                } else {
                    if (viewModel.isLoadingPrivate) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("加载中...", style = TextStyle(color = CHelperTheme.colors.mainColor))
                            }
                        }
                    } else if (viewModel.privateLibraries.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("云库中暂未缓存任何私有记录。", style = TextStyle(color = CHelperTheme.colors.textSecondary))
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
                                    navController?.navigate(PublicLibraryShowScreenKey(id = it, isPrivate = true))
                                }
                            }
                        }
                    }
                }
            }
        }
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
        
        if (!user.signature.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "\"${user.signature}\"",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(user.homepage))
                        context.startActivity(intent)
                    } catch (e: Exception) {}
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
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CHelperTheme.colors.textMain)
                )
                Text("公开作品", style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${user.totalLikes ?: 0}",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CHelperTheme.colors.textMain)
                )
                Text("累计获赞", style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
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
            .run { if ((library.likeCount ?: 0) >= 10 && !isPrivate) this.border(1.dp, CHelperTheme.colors.mainColor.copy(alpha=0.3f), RoundedCornerShape(8.dp)) else this }
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = library.name ?: "未命名",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CHelperTheme.colors.textMain),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (isPrivate) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (library.hasPublicVersion == true) CHelperTheme.colors.mainColor.copy(alpha=0.2f) else CHelperTheme.colors.background)
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
                Text(text = "v$ver", style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary))
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
                text = library.createdAt?.take(10) ?: "未知",
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
