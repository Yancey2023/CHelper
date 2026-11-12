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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.RadioButton
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun SelectionDialog(
    title: String,
    data: Array<Pair<String, String>>,
    initialValue: String? = null,
    cancelText: String = stringResource(Res.string.dialog_choosing_cancel),
    confirmText: String = stringResource(Res.string.dialog_choosing_confirm),
    onChoose: (String) -> Unit,
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    var selectedIndex by
        remember(initialValue) {
            mutableIntStateOf(data.indexOfFirst { it.second == initialValue })
        }
    val selectedValue = data.getOrNull(selectedIndex)?.second

    CustomDialog(onDismissRequest = onDismissRequest) {
        DialogContainer(cornerSize = 20.dp, backgroundNoTranslate = true) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(15.dp, 10.dp),
                    text = title,
                    style = TextStyle(fontSize = 18.sp, textAlign = TextAlign.Left),
                )
                Divider(0.dp)
                for ((index, pair) in data.withIndex()) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(45.dp)
                                .selectable(
                                    selected = selectedIndex == index,
                                    role = Role.RadioButton,
                                    onClick = { selectedIndex = index },
                                )
                                .padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedIndex == index, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(text = pair.first)
                    }
                }
                Divider(0.dp)
                Row(Modifier.height(45.dp).align(Alignment.End)) {
                    Box(
                        Modifier.fillMaxHeight().padding(10.dp).clickable {
                            onDismissRequest()
                            onCancel()
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = cancelText,
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textHint,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                    Box(
                        Modifier.fillMaxHeight()
                            .padding(5.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (selectedValue != null) {
                                    CHelperTheme.colors.mainColor
                                } else {
                                    CHelperTheme.colors.mainColorSecondary
                                }
                            )
                            .padding(horizontal = 15.dp)
                            .clickable(enabled = selectedValue != null) {
                                onDismissRequest()
                                selectedValue?.let(onChoose)
                            }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = confirmText,
                            style = TextStyle(color = Color.White, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}
