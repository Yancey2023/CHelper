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

import MessageScreenKey
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.hjq.toast.Toaster
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.UserProfileScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextFieldWithIcon
import yancey.chelper.ui.library.profile.EditProfileDialog

@Composable
fun CPLUserScreen(
    viewModel: CPLUserViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    isTab: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        viewModel.refreshUserState()
    }

    // Captcha State
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaAction by remember { mutableStateOf("") }
    var captchaCallback by remember { mutableStateOf<(String) -> Unit>({}) }

    // 编辑个人资料 dialog 的开关。集中后头像、昵称、主页、签名都从这里改
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Helper for Captcha
    fun showCaptcha(action: String, onSuccess: (String) -> Unit) {
        captchaAction = action
        captchaCallback = onSuccess
        showCaptchaDialog = true
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                try {
                    val cr = context.contentResolver
                    cr.openInputStream(it)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val mimeType = cr.getType(it) ?: "image/jpeg"
                        viewModel.uploadAvatar(bytes, "avatar.jpg", mimeType)
                    }
                } catch (e: Exception) {
                    Toaster.show("读取图片失败: ${e.message}")
                }
            }
        }
    )

    // 启动系统选图。dialog 内的"从相册选择"和头像圆点击都走这里
    val triggerPickAvatar: () -> Unit = {
        if (!viewModel.isUploadingAvatar) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_user_profile_title),
        showBack = !isTab,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CHelperTheme.colors.background)
        ) {
            if (viewModel.currentUser != null && !viewModel.isGuest) {
                UserProfileView(
                    viewModel = viewModel,
                    navController = navController,
                    onUploadAvatarClick = triggerPickAvatar,
                    onEditProfileClick = {
                        // 没拉过 userProfile 时点开会显示加载占位，先触发拉取
                        if (viewModel.userProfile == null) viewModel.loadUserProfile()
                        showEditProfileDialog = true
                    }
                )
            } else if (viewModel.isGuest) {
                GuestUserProfileView(viewModel, navController)
            } else {
                LoginRegisterView(viewModel, onCaptchaRequest = { action, callback ->
                    showCaptcha(action, callback)
                })
            }
        }
    }

    if (showCaptchaDialog) {
        CaptchaDialog(
            action = captchaAction,
            onDismissRequest = { showCaptchaDialog = false },
            onSuccess = { code -> captchaCallback(code) }
        )
    }

    // 编辑资料对话框：依赖 userProfile（昵称/主页/签名/tier）。没加载完先提示等。
    if (showEditProfileDialog) {
        val profile = viewModel.userProfile
        if (profile == null) {
            // 拉取中——用一个极简的对话框占位，避免空白卡顿让用户疑惑
            Dialog(onDismissRequest = {
                showEditProfileDialog = false
            }) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "正在加载资料…",
                        style = TextStyle(color = CHelperTheme.colors.textMain)
                    )
                }
            }
        } else {
            EditProfileDialog(
                user = profile,
                isUpdating = viewModel.isUpdatingProfile,
                onDismiss = { showEditProfileDialog = false },
                onSave = { nickname, avatar, homepage, signature ->
                    viewModel.updateProfile(nickname, avatar, homepage, signature) {
                        showEditProfileDialog = false
                    }
                },
                onPickAvatarImage = triggerPickAvatar,
                isUploadingAvatar = viewModel.isUploadingAvatar,
                currentAvatarUrl = viewModel.currentUser?.gravatarUrl ?: profile.avatarUrl
            )
        }
    }
}

@Composable
fun UserProfileView(
    viewModel: CPLUserViewModel,
    navController: NavHostController,
    onUploadAvatarClick: () -> Unit,
    onEditProfileClick: () -> Unit
) {
    val user = viewModel.currentUser ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(
                    CHelperTheme.colors.backgroundComponentNoTranslate,
                    RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (viewModel.isUploadingAvatar) CHelperTheme.colors.backgroundComponentNoTranslate else CHelperTheme.colors.backgroundComponent)
                            .clickable { onUploadAvatarClick() }
                    ) {
                        AsyncImage(
                            model = user.gravatarUrl ?: "https://abyssous.site/avatar/${user.id}",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (viewModel.isUploadingAvatar) 0.5f else 1f),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_user),
                            error = painterResource(id = R.drawable.ic_user)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(CHelperTheme.colors.backgroundComponentNoTranslate),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            id = R.drawable.pencil,
                            modifier = Modifier.size(12.dp),
                            contentDescription = "上传头像"
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = user.nickname ?: "未知用户",
                        style = TextStyle(
                            color = CHelperTheme.colors.textMain,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = user.email ?: "",
                        style = TextStyle(color = CHelperTheme.colors.textSecondary)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 编辑个人资料：把"上传头像 / 改昵称 / 改主页 / 改签名"集中到一处。
        // 头像点击仍然能直接触发上传（快捷路径），但完整资料维护走这个卡片更直观。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(
                    CHelperTheme.colors.backgroundComponentNoTranslate,
                    RoundedCornerShape(16.dp)
                )
                .clickable { onEditProfileClick() }
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Icon(id = R.drawable.pencil, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "编辑个人资料",
                        style = TextStyle(
                            color = CHelperTheme.colors.textMain,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "上传头像、修改昵称、主页与签名",
                        style = TextStyle(
                            color = CHelperTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(
                    CHelperTheme.colors.backgroundComponentNoTranslate,
                    RoundedCornerShape(16.dp)
                )
                .clickable {
                    user.id?.let {
                        navController.navigate(UserProfileScreenKey(id = it))
                    }
                }
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Icon(id = R.drawable.ic_user, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "进入创作者主页 (云库管理)",
                    style = TextStyle(
                        color = CHelperTheme.colors.textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // 站内信入口
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(
                    CHelperTheme.colors.backgroundComponentNoTranslate,
                    RoundedCornerShape(16.dp)
                )
                .clickable {
                    navController.navigate(MessageScreenKey)
                }
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Icon(id = R.drawable.ic_mail, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "站内信",
                    style = TextStyle(
                        color = CHelperTheme.colors.textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 退出登录：单独占一行的危险按钮，跟资料/导航入口拉开距离，避免误触。
        // 原本下面还有"上传新指令"按钮，已经移除——上传入口在"我的库" header 和顶部快捷区都能直接找到，
        // 账户中心专注做账户管理而不是内容创作
        Button(
            text = "退出登录",
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = true
        )
    }
}

@Composable
fun GuestUserProfileView(
    viewModel: CPLUserViewModel,
    navController: NavHostController
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            id = R.drawable.ic_user, modifier = Modifier
                .size(80.dp)
                .alpha(0.5f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "访客模式",
            style = TextStyle(
                color = CHelperTheme.colors.textMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "您可以浏览和下载指令，但无法上传或评论。",
            style = TextStyle(
                color = CHelperTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        )
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(
                    CHelperTheme.colors.backgroundComponentNoTranslate,
                    RoundedCornerShape(16.dp)
                )
                .clickable { navController.navigate(MessageScreenKey) }
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(id = R.drawable.ic_mail, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "站内信",
                    style = TextStyle(
                        color = CHelperTheme.colors.textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            text = "登录 / 注册",
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(25.dp)
        )
    }
}

@Composable
fun MyLibraryItem(lib: LibraryFunction, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ChoosingDialog(
            onDismissRequest = { showDeleteConfirm = false },
            data = arrayOf("确认删除" to "confirm", "取消" to "cancel"),
            onChoose = { action ->
                showDeleteConfirm = false
                if (action == "confirm") {
                    onDelete()
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lib.name ?: "Unnamed",
                style = TextStyle(
                    color = CHelperTheme.colors.textMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ver: ${lib.version}",
                style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 12.sp)
            )
        }
        Icon(
            id = R.drawable.x,
            modifier = Modifier
                .size(20.dp)
                .alpha(0.5f)
                .clickable { showDeleteConfirm = true }
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            id = R.drawable.chevron_right, modifier = Modifier
                .size(16.dp)
                .alpha(0.5f)
        )
    }
}

@Composable
fun LoginRegisterView(
    viewModel: CPLUserViewModel,
    onCaptchaRequest: (String, (String) -> Unit) -> Unit
) {
    var isLoginPasswordVisible by remember { mutableStateOf(false) }
    var isRegisterPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        // Logo or Title
        Text(
            text = "Command Lab",
            style = TextStyle(
                color = CHelperTheme.colors.mainColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Card Container
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Custom Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(CHelperTheme.colors.background, RoundedCornerShape(24.dp))
                        .padding(4.dp)
                ) {
                    TabPill("登录", viewModel.currentTab == CPLUserViewModel.UserTab.LOGIN) {
                        viewModel.currentTab = CPLUserViewModel.UserTab.LOGIN
                    }
                    TabPill("注册", viewModel.currentTab == CPLUserViewModel.UserTab.REGISTER) {
                        viewModel.currentTab = CPLUserViewModel.UserTab.REGISTER
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (viewModel.currentTab == CPLUserViewModel.UserTab.LOGIN) {
                    // Login Inputs
                    TextFieldWithIcon(
                        state = viewModel.loginAccount,
                        hint = "用户邮箱 / 账号",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_user, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextFieldWithIcon(
                        state = viewModel.loginPassword,
                        hint = "密码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        leadingIcon = { Icon(R.drawable.ic_lock, Modifier.size(20.dp)) },
                        trailingIcon = {
                            Icon(
                                id = if (isLoginPasswordVisible) R.drawable.eye else R.drawable.eye_off,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { isLoginPasswordVisible = !isLoginPasswordVisible }
                                    .padding(14.dp),
                                contentDescription = "显示/隐藏密码"
                            )
                        },
                        outputTransformation = if (isLoginPasswordVisible) null else OutputTransformation {
                            replace(
                                0,
                                length,
                                "\u2022".repeat(length)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        text = if (viewModel.isLoading) "登录中..." else "立即登录",
                        onClick = { if (!viewModel.isLoading) viewModel.login() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(25.dp),
                        enabled = !viewModel.isLoading
                    )
                } else {
                    // Register Inputs
                    TextFieldWithIcon(
                        state = viewModel.registerAccount,
                        hint = "电子邮箱 / 手机号",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_mail, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        TextFieldWithIcon(
                            state = viewModel.registerCode,
                            hint = "验证码",
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            leadingIcon = { Icon(R.drawable.ic_key, Modifier.size(20.dp)) }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            text = if (viewModel.isCheckingCaptcha) "..." else "获取",
                            onClick = {
                                if (!viewModel.isCheckingCaptcha) {
                                    viewModel.isCheckingCaptcha = true
                                    onCaptchaRequest("注册账号") { code ->
                                        viewModel.sendVerifyCode(code)
                                    }
                                }
                            },
                            modifier = Modifier.width(80.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !viewModel.isLoading && !viewModel.isCheckingCaptcha
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextFieldWithIcon(
                        state = viewModel.registerNickname,
                        hint = "用户昵称",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_user, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextFieldWithIcon(
                        state = viewModel.registerPassword,
                        hint = "设置密码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        leadingIcon = { Icon(R.drawable.ic_lock, Modifier.size(20.dp)) },
                        trailingIcon = {
                            Icon(
                                id = if (isRegisterPasswordVisible) R.drawable.eye else R.drawable.eye_off,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        isRegisterPasswordVisible = !isRegisterPasswordVisible
                                    }
                                    .padding(14.dp),
                                contentDescription = "显示/隐藏密码"
                            )
                        },
                        outputTransformation = if (isRegisterPasswordVisible) null else OutputTransformation {
                            replace(
                                0,
                                length,
                                "\u2022".repeat(length)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        text = if (viewModel.isLoading) "注册中..." else "立即注册",
                        onClick = {
                            if (!viewModel.isLoading) {
                                viewModel.isCheckingCaptcha = true
                                onCaptchaRequest("注册账号") { code ->
                                    viewModel.register(code)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(25.dp),
                        enabled = !viewModel.isLoading && !viewModel.isCheckingCaptcha
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CHelperTheme.colors.backgroundComponentNoTranslate else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = if (selected) CHelperTheme.colors.textMain else CHelperTheme.colors.textSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        )
    }
}
