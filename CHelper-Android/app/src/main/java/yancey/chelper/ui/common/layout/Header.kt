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

package yancey.chelper.ui.common.layout

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

@Composable
fun Header(title: String, right: @Composable () -> Unit = {}) {
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(CHelperTheme.colors.backgroundComponent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            id = R.drawable.chevron_left,
            modifier = Modifier
                .clickable(onClick = {
                    onBackPressedDispatcher?.onBackPressed()
                })
                .padding(5.dp)
                .size(24.dp),
            contentDescription = stringResource(R.string.common_icon_back_content_description)
        )
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            style = TextStyle(
                fontSize = 18.sp,
            ),
            maxLines = 1
        )
        right()
    }
}

@Preview
@Composable
fun HeaderLightThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        Header("Title") {
            Icon(
                id = R.drawable.plus, modifier = Modifier
                    .padding(5.dp)
                    .size(24.dp)
            )
        }
    }
}

@Preview
@Composable
fun HeaderDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        Header("Title") {
            Icon(
                id = R.drawable.plus, modifier = Modifier
                    .padding(5.dp)
                    .size(24.dp)
            )
        }
    }
}
