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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.CPLUploadScreenKey
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CaptchaDialog
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun CPLUserScreen(
    viewModel: CPLUserViewModel = viewModel(),
    navController: NavHostController
) {
    // Captcha State
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaAction by remember { mutableStateOf("") }
    var captchaCallback by remember { mutableStateOf<(String) -> Unit>({}) }

    if (showCaptchaDialog) {
        CaptchaDialog(
            action = captchaAction,
            onDismissRequest = { showCaptchaDialog = false },
            onSuccess = { code -> captchaCallback(code) }
        )
    }

    // Helper for Captcha
    fun showCaptcha(action: String, onSuccess: (String) -> Unit) {
        captchaAction = action
        captchaCallback = onSuccess
        showCaptchaDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.refreshUserState()
    }

    RootViewWithHeaderAndCopyright(
        title = "用户中心",
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(CHelperTheme.colors.background)) {
            if (viewModel.currentUser != null && !viewModel.isGuest) {
                UserProfileView(viewModel, navController)
            } else if (viewModel.isGuest) {
                GuestUserProfileView(viewModel)
            } else {
                LoginRegisterView(viewModel, onCaptchaRequest = { action, callback ->
                    showCaptcha(action, callback)
                })
            }
        }
    }
}

@Composable
fun UserProfileView(viewModel: CPLUserViewModel, navController: NavHostController) {
    val user = viewModel.currentUser ?: return

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
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
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(id = R.drawable.ic_user, modifier = Modifier.size(40.dp))
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(id = R.drawable.folder, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "我的云端库",
                style = TextStyle(
                    color = CHelperTheme.colors.textMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(CHelperTheme.colors.backgroundComponent, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            items(viewModel.myLibraries) { lib ->
                MyLibraryItem(
                    lib = lib,
                    onClick = {
                        navController.navigate(
                            PublicLibraryShowScreenKey(
                                lib.id!!,
                                isPrivate = true
                            )
                        )
                    },
                    onDelete = {
                        viewModel.deleteLibrary(lib.id!!)
                    }
                )
                Divider(0.5.dp)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (!viewModel.myLibrariesHasMore && viewModel.myLibraries.isEmpty()) {
            Text(
                text = "没有更多了",
                style = TextStyle(color = CHelperTheme.colors.textHint),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else if (viewModel.myLibraries.isEmpty()) {
            Text(
                text = "暂无上传",
                style = TextStyle(color = CHelperTheme.colors.textHint),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                text = "退出登录",
                onClick = { viewModel.logout() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = true // TODO: Add logout loading state?
            )
            Button(
                text = "上传新指令",
                onClick = {
                    navController.navigate(CPLUploadScreenKey)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun GuestUserProfileView(viewModel: CPLUserViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(id = R.drawable.ic_user, modifier = Modifier
            .size(80.dp)
            .alpha(0.5f))
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
        Spacer(Modifier.height(32.dp))
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
        Icon(id = R.drawable.chevron_right, modifier = Modifier
            .size(16.dp)
            .alpha(0.5f))
    }
}

@Composable
fun LoginRegisterView(
    viewModel: CPLUserViewModel,
    onCaptchaRequest: (String, (String) -> Unit) -> Unit
) {
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
                    TextField(
                        state = viewModel.loginAccount,
                        hint = "用户邮箱 / 账号",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_user, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        state = viewModel.loginPassword,
                        hint = "密码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        leadingIcon = { Icon(R.drawable.ic_lock, Modifier.size(20.dp)) },
                        // TODO: Add toggle password visibility using trailingIcon
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
                    TextField(
                        state = viewModel.registerAccount,
                        hint = "电子邮箱 / 手机号",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_mail, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        TextField(
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
                    TextField(
                        state = viewModel.registerNickname,
                        hint = "用户昵称",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        leadingIcon = { Icon(R.drawable.ic_user, Modifier.size(20.dp)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        state = viewModel.registerPassword,
                        hint = "设置密码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        leadingIcon = { Icon(R.drawable.ic_lock, Modifier.size(20.dp)) }
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

// 以前这里有一个自定义的 Modifier 扩展，现在已经删掉了，直接用 androidx.compose.ui.draw.alpha 就行
