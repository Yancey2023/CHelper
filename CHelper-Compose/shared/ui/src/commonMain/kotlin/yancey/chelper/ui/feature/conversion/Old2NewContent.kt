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

package yancey.chelper.ui.feature.conversion

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.layout.Surface
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField

@Composable
fun Old2NewContent(
    oldCommand: TextFieldState,
    newCommand: String,
    isConverting: Boolean,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
) {
    RootViewWithHeaderAndCopyright(stringResource(Res.string.layout_old2new_title)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(15.dp))
                TextField(
                    state = oldCommand,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(200.dp),
                    hint = stringResource(Res.string.layout_old2new_old_command_hint),
                )
                Spacer(modifier = Modifier.height(15.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(200.dp)
                ) {
                    Text(text = if (isConverting) "转换中..." else newCommand)
                }
                Spacer(modifier = Modifier.height(15.dp))
            }
            Button(
                stringResource(Res.string.layout_old2new_clear_and_paste_old_command),
                onClick = onPaste,
            )
            Button(
                stringResource(Res.string.layout_old2new_copy_new_command),
                enabled = !isConverting,
                onClick = onCopy,
            )
        }
    }
}
