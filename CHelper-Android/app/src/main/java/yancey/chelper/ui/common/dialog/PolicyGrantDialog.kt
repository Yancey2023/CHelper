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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.DividerVertical
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

@Composable
fun PolicyGrantDialog(
    content: String = stringResource(R.string.dialog_policy_grant_message_if_unread),
    readPolicy: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    var isAgree by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 10.dp),
                text = stringResource(R.string.dialog_policy_grant_title),
                style = TextStyle(
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                modifier = Modifier
                    .padding(20.dp, 10.dp)
                    .fillMaxWidth()
                    .defaultMinSize(Dp.Infinity, 40.dp),
                text = content,
                style = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center)
            )
            Row(
                modifier = Modifier
                    .padding(20.dp, 5.dp)
                    .fillMaxWidth()
            ) {
                Switch(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    checked = isAgree,
                    onCheckedChange = { isAgree = !isAgree })
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterVertically),
                    text = stringResource(R.string.dialog_policy_grant_confirm_read),
                )
            }
            Divider(0.dp)
            Row(Modifier.height(45.dp)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable {
                            readPolicy()
                        }) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.dialog_policy_grant_read_privacy_policy),
                        style = TextStyle(
                            fontSize = 20.sp,
                            color = CHelperTheme.colors.mainColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                DividerVertical(0.dp)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable {
                            if (isAgree) {
                                onConfirm()
                            }
                        }) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.dialog_is_confirm_confirm),
                        style = TextStyle(
                            fontSize = 20.sp,
                            color = CHelperTheme.colors.mainColor,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PolicyGrantDialogLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        PolicyGrantDialog()
    }
}

@Preview
@Composable
fun PolicyGrantDialogDarkThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Dark,
        backgroundBitmap = null
    ) {
        PolicyGrantDialog()
    }
}
