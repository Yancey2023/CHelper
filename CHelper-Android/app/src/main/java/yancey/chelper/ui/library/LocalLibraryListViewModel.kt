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

package yancey.chelper.ui.library

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import yancey.chelper.android.common.util.LocalLibraryManager
import yancey.chelper.network.library.data.LibraryFunction

class LocalLibraryListViewModel : ViewModel() {
    var keyword by mutableStateOf(TextFieldState())
    var libraries: SnapshotStateList<LibraryFunction> =
        LocalLibraryManager.INSTANCE!!.getFunctions()
    var isShowImportDialog by mutableStateOf(false)
    var isShowExportDialog by mutableStateOf(false)

    init {
        viewModelScope.launch {
            LocalLibraryManager.INSTANCE!!.ensureInit()
        }
    }
}