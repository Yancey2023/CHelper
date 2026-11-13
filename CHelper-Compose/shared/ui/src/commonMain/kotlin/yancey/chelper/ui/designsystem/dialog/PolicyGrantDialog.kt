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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Checkbox
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton

@Composable
fun PolicyGrantDialog(
    content: String = stringResource(Res.string.dialog_policy_grant_message_if_unread),
    readPolicy: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    var isAgree by remember { mutableStateOf(false) }
    AppAlertDialog(
        onDismissRequest = {},
        properties = AppDialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.dialog_policy_grant_title)) },
        text = {
            Column {
                Text(
                    content,
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAgree, onCheckedChange = { isAgree = it })
                    Text(stringResource(Res.string.dialog_policy_grant_confirm_read))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = readPolicy) {
                Text(stringResource(Res.string.dialog_policy_grant_read_privacy_policy))
            }
        },
        confirmButton = {
            TextButton(enabled = isAgree, onClick = onConfirm) {
                Text(stringResource(Res.string.dialog_is_confirm_confirm))
            }
        },
        containerColor = CHelperTheme.colors.backgroundComponentNoTranslate,
    )
}
