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

package yancey.chelper.ui.designsystem.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.DividerVertical
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField

@Composable
fun InputStringDialog(
    onDismissRequest: () -> Unit,
    title: String = stringResource(Res.string.dialog_input_string_title),
    textFieldState: TextFieldState,
    onConfirm: () -> Unit,
) {
    AppDialog(onDismissRequest = onDismissRequest) {
        DialogContainer {
            Column {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(0.dp, 10.dp),
                    text = title,
                    style = TextStyle(fontSize = 18.sp, textAlign = TextAlign.Center),
                )
                TextField(
                    state = textFieldState,
                    modifier = Modifier.padding(20.dp, 10.dp).height(40.dp),
                    contentAlignment = Alignment.Center,
                    hint = stringResource(Res.string.dialog_input_string_input_hint),
                )
                Divider(0.dp)
                Row(Modifier.height(45.dp)) {
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clickable(role = Role.Button) {
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = stringResource(Res.string.dialog_input_string_cancel),
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                    DividerVertical(0.dp)
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clickable(role = Role.Button) {
                            onDismissRequest()
                            onConfirm()
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = stringResource(Res.string.dialog_input_string_confirm),
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                }
            }
        }
    }
}
