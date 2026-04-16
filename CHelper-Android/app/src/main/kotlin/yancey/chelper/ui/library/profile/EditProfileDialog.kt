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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import yancey.chelper.R
import yancey.chelper.network.library.data.UserProfileData
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun EditProfileDialog(
    user: UserProfileData,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (nickname: String, avatarUrl: String?, homepage: String?, signature: String?) -> Unit
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
                    androidx.compose.foundation.text.BasicText(
                        text = "由于您当前为 Tier 0, 头像、主页和签名暂不可更改。",
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFD32F2F))
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    "头像 URL (以 http 开头)",
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextField(
                    state = avatarUrlState,
                    hint = "头像链接",
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
                Box(modifier = Modifier
                    .clickable(!isUpdating) { onDismiss() }
                    .padding(8.dp)) {
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
