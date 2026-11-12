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

package yancey.chelper.ui.feature.library.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import org.jetbrains.compose.resources.*
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun LibraryGroupItem(index: Int, count: Int, content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = if (index == 0) 10.dp else 0.dp,
                        topEnd = if (index == 0) 10.dp else 0.dp,
                        bottomStart = if (index == count - 1) 10.dp else 0.dp,
                        bottomEnd = if (index == count - 1) 10.dp else 0.dp,
                    )
                )
                .background(CHelperTheme.colors.backgroundComponent)
    ) {
        content()
        if (index < count - 1) Divider(padding = 0.dp)
    }
}

/** 容量进度条：单纯展示 used/limit 的视觉条 limit == -1 表示无限制，进度条按固定 10% 显示，文案换成"无限制" 配额接近用满时颜色加深一档，给用户提个醒 */
@Composable
fun LibraryLoginHint(onLogin: () -> Unit, onMessages: () -> Unit = onLogin) {
    // 未登录态的引导卡片。
    // 顶部主区块引导去登录；下方留一个常驻的"站内信"入口——之前砍掉
    // "我的" tab 的时候，访客访问站内信的入口跟着丢了，这里补回来。
    // 站内信里有面向所有人的公告，登录态不是看消息的前置条件
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color = CHelperTheme.colors.backgroundComponent)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clickable { onLogin() }.padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "登录后可管理云端命令库",
                style =
                    TextStyle(
                        color = CHelperTheme.colors.mainColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
        Divider(padding = 0.dp)
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { onMessages() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                id = Res.drawable.ic_mail,
                modifier = Modifier.size(18.dp),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "站内信",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 14.sp),
            )
            Icon(
                id = Res.drawable.chevron_right,
                modifier = Modifier.size(16.dp),
                contentDescription = null,
            )
        }
    }
}
