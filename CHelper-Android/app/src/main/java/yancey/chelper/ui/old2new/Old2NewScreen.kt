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

package yancey.chelper.ui.old2new

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.Surface
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun Old2NewScreen(viewModel: Old2NewViewModel = viewModel(), old2new: (String) -> String) {
    RootViewWithHeaderAndCopyright(stringResource(R.string.layout_old2new_title)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(15.dp))
                TextField(
                    state = viewModel.oldCommand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .height(200.dp),
                    hint = stringResource(R.string.layout_old2new_old_command_hint)
                )
                Spacer(modifier = Modifier.height(15.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .height(200.dp)
                ) {
                    Text(text = viewModel.newCommand)
                }
                Spacer(modifier = Modifier.height(15.dp))
            }
            val clipboard = LocalClipboard.current
            Button(stringResource(R.string.layout_old2new_clear_and_paste_old_command)) {
                viewModel.viewModelScope.launch {
                    clipboard.getClipEntry()?.clipData?.apply {
                        if (itemCount > 0) {
                            viewModel.oldCommand.setTextAndPlaceCursorAtEnd(getItemAt(0).text.toString())
                        }
                    }
                }
            }
            Button(stringResource(R.string.layout_old2new_copy_new_command)) {
                viewModel.viewModelScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                null,
                                viewModel.newCommand
                            )
                        )
                    )
                }
            }
        }
        LaunchedEffect(viewModel.oldCommand.text) {
            viewModel.newCommand = old2new(viewModel.oldCommand.text.toString())
        }
    }
}

@Preview
@Composable
fun Old2NewScreenLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        Old2NewScreen(viewModel = viewModel(), old2new = { old -> old })
    }
}

@Preview
@Composable
fun Old2NewScreenDarkThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Dark,
        backgroundBitmap = null
    ) {
        Old2NewScreen(viewModel = viewModel(), old2new = { old -> old })
    }
}