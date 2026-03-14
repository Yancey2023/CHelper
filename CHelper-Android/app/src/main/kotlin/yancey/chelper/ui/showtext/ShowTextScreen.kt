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

package yancey.chelper.ui.showtext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Text

@Composable
fun ShowTextScreen(
    title: String,
    content: String
) {
    RootViewWithHeaderAndCopyright(title) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 15.dp, top = 10.dp, end = 15.dp, bottom = 0.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .verticalScroll(rememberScrollState())
        ) {
            SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    style = TextStyle(
                        fontSize = 14.sp
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
fun ShowTextScreenLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        ShowTextScreen(title = "title", content = buildString {
            for (i in 1..100) {
                append("Row $i\n")
            }
        })
    }
}

@Preview
@Composable
fun ShowTextScreenDarkThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Dark,
        backgroundBitmap = null
    ) {
        ShowTextScreen(title = "title", content = buildString {
            for (i in 1..100) {
                append("Row $i\n")
            }
        })
    }
}
