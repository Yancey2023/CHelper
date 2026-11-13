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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.RadioButton
import yancey.chelper.ui.designsystem.widget.SelectionItem
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton

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

    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                data.forEachIndexed { index, pair ->
                    SelectionItem(
                        headlineContent = { Text(pair.first) },
                        leadingContent = {
                            RadioButton(selected = selectedIndex == index, onClick = null)
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = selectedIndex == index,
                                    role = Role.RadioButton,
                                    onClick = { selectedIndex = index },
                                ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onCancel()
                }
            ) {
                Text(cancelText)
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedValue != null,
                onClick = {
                    onDismissRequest()
                    selectedValue?.let(onChoose)
                },
            ) {
                Text(confirmText)
            }
        },
        containerColor = CHelperTheme.colors.backgroundComponentNoTranslate,
    )
}
