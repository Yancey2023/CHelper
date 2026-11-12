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

package yancey.chelper.ui.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.platform.LocalAppBackAction
import yancey.chelper.ui.platform.LocalAppBackDispatcher
import yancey.chelper.ui.platform.LocalPlatformHeaderBackAction

@Composable
fun Header(
    title: String,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    right: @Composable () -> Unit = {},
) {
    val defaultBack = LocalAppBackAction.current
    val backDispatcher = LocalAppBackDispatcher.current
    val platformBack = LocalPlatformHeaderBackAction.current
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(50.dp)
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Icon(
                id = Res.drawable.chevron_left,
                modifier =
                    Modifier.clickable(
                            onClick = {
                                // 优先使用自定义 onBack（用于 tab 模式下显式返回上一级路由），
                                // 否则走系统返回派发，行为和原来一致
                                if (onBack != null) onBack()
                                else if (platformBack != null) platformBack()
                                else backDispatcher.back(defaultBack)
                            }
                        )
                        .padding(5.dp)
                        .size(24.dp),
                contentDescription = stringResource(Res.string.common_icon_back_content_description),
            )
        } else {
            Spacer(modifier = Modifier.size(5.dp))
        }
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().weight(1f),
            style = TextStyle(fontSize = 18.sp),
            maxLines = 1,
        )
        right()
    }
}
