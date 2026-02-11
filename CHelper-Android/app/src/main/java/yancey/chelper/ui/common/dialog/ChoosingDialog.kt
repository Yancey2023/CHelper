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

package yancey.chelper.ui.common.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Text

@Composable
fun ChoosingDialog(
    onDismissRequest: () -> Unit,
    data: Array<Pair<String, String>>,
    onChoose: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
                .verticalScroll(rememberScrollState())
        ) {
            for ((index, pair) in data.withIndex()) {
                if (index != 0) {
                    Divider(0.dp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable {
                            onChoose(pair.second)
                            onDismissRequest.invoke()
                        }) {
                    Text(
                        text = pair.first,
                        modifier = Modifier.align(Alignment.Center),
                        style = TextStyle(
                            fontSize = 20.sp
                        )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ChoosingDialogLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        ChoosingDialog(
            onDismissRequest = { },
            data = arrayOf(
                "浅色模式" to "MODE_NIGHT_NO",
                "深色模式" to "MODE_NIGHT_YES",
                "跟随系统" to "MODE_NIGHT_FOLLOW_SYSTEM",
            ),
            onChoose = {}
        )
    }
}

@Preview
@Composable
fun ChoosingDialogDarkThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Dark,
        backgroundBitmap = null
    ) {
        ChoosingDialog(
            onDismissRequest = { },
            data = arrayOf(
                "浅色模式" to "MODE_NIGHT_NO",
                "深色模式" to "MODE_NIGHT_YES",
                "跟随系统" to "MODE_NIGHT_FOLLOW_SYSTEM",
            ),
            onChoose = {}
        )
    }
}
