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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import yancey.chelper.R
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

/**
 * 统一的"我的资料"编辑面板。承担三件事：
 * 1. 头像：直接调用系统选图上传（通过 [onPickAvatarImage] 让宿主层把 photoPicker 启动起来）。
 * 2. 头像 URL：保留一个备用输入框，给"我就想填个 URL"的用户走（Tier 0 不允许）。
 * 3. 昵称 / 主页 / 签名。
 *
 * 入口可以来自 CPLUserScreen（账户中心）或 UserProfileScreen（自己的主页），
 * 集中到同一个 dialog 之后，用户不需要为了换头像和改昵称跑两个完全不同的页面。
 *
 * [onPickAvatarImage] 为 null 时不显示"上传图片"按钮——这里只是兜底，
 * 正常使用都应该把它传进来；不传等于把"集中入口"的核心功能砍掉。
 */
@Composable
fun EditProfileDialog(
    user: UserProfileData,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (nickname: String, avatarUrl: String?, homepage: String?, signature: String?) -> Unit,
    onPickAvatarImage: (() -> Unit)? = null,
    isUploadingAvatar: Boolean = false,
    currentAvatarUrl: String? = null
) {
    val nicknameState = rememberTextFieldState(user.nickname ?: "")
    val avatarUrlState = rememberTextFieldState(user.avatarUrl ?: "")
    val homepageState = rememberTextFieldState(user.homepage ?: "")
    val signatureState = rememberTextFieldState(user.signature ?: "")

    val isLocked = (user.tier ?: 0) < 1

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(24.dp)
                // 字段一多容易溢出，给个可滚动以避免小屏被截
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "编辑个人资料",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.textMain
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ---------- 头像区 ----------
            if (onPickAvatarImage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像预览圆。上传中半透明，点头像本身也能触发选图
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CHelperTheme.colors.background)
                            .clickable(enabled = !isUploadingAvatar && !isLocked) { onPickAvatarImage() }
                    ) {
                        AsyncImage(
                            model = currentAvatarUrl
                                ?: user.avatarUrl
                                ?: user.id?.let { "https://abyssous.site/avatar/$it" },
                            contentDescription = "当前头像",
                            modifier = Modifier
                                .size(56.dp)
                                .alpha(if (isUploadingAvatar) 0.45f else 1f),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_user),
                            error = painterResource(id = R.drawable.ic_user)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "头像",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = CHelperTheme.colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 不可上传时（Tier 0）这个按钮也禁用，统一交给下面的提示去解释原因
                        val uploadEnabled = !isUploadingAvatar && !isLocked
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (uploadEnabled) CHelperTheme.colors.mainColor.copy(alpha = 0.12f)
                                    else CHelperTheme.colors.background
                                )
                                .clickable(enabled = uploadEnabled) { onPickAvatarImage() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isUploadingAvatar) "上传中…" else "从相册选择",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = if (uploadEnabled) CHelperTheme.colors.mainColor
                                    else CHelperTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // ---------- 文本字段 ----------
            Text(
                "昵称",
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextField(
                state = nicknameState,
                hint = "请输入昵称",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF8B0000).copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lock),
                        contentDescription = "Lock",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(Color(0xFFD32F2F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicText(
                        text = "由于您当前为 Tier 0, 头像、主页和签名暂不可更改。",
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFD32F2F))
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    "头像 URL（可选，留空则用上方上传的图片）",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    state = avatarUrlState,
                    hint = "http(s)://... 也可以接外链头像",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "个人主页 URL",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    state = homepageState,
                    hint = "个人主页链接",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "个性签名",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    state = signatureState,
                    hint = "个性签名",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable(!isUpdating) { onDismiss() }
                        .padding(8.dp)
                ) {
                    Text("取消", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.width(100.dp)) {
                    Button(
                        text = if (isUpdating) "保存中..." else "保存",
                        enabled = !isUpdating,
                        onClick = {
                            onSave(
                                nicknameState.text.toString(),
                                avatarUrlState.text.toString(),
                                homepageState.text.toString(),
                                signatureState.text.toString()
                            )
                        }
                    )
                }
            }
        }
    }
}
