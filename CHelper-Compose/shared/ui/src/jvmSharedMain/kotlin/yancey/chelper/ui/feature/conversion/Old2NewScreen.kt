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

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.ui.platform.ClientToast as Toaster

@Composable
fun Old2NewScreen(
    viewModel: Old2NewViewModel = viewModel { Old2NewViewModel() },
    old2new: suspend (String) -> String,
) {
    val clipboard = yancey.chelper.ui.platform.LocalClientServices.current.platform
    val context = yancey.chelper.ui.platform.LocalClientServices.current
    Old2NewContent(
        oldCommand = viewModel.oldCommand,
        newCommand = viewModel.newCommand,
        isConverting = viewModel.isConverting,
        onPaste = {
            viewModel.viewModelScope.launch {
                clipboard.clipboardText()?.let {
                    viewModel.oldCommand.setTextAndPlaceCursorAtEnd(it.toString())
                }
            }
        },
        onCopy = {
            viewModel.viewModelScope.launch {
                clipboard.copyText(viewModel.newCommand)
                Toaster.show("已复制")
            }
        },
    )
    LaunchedEffect(viewModel, old2new) {
        snapshotFlow { viewModel.oldCommand.text.toString() }
            .collect { viewModel.convert(it, old2new) }
    }
}
