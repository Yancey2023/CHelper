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
import androidx.lifecycle.ViewModel

enum class EditMode {
    ADD,
    UPDATE
}

class LocalLibraryEditViewModel : ViewModel() {
    var id by mutableStateOf<Int?>(null)
    val mode get() = if (id == null) EditMode.ADD else EditMode.UPDATE
    var name by mutableStateOf(TextFieldState())
    var version by mutableStateOf(TextFieldState())
    var author by mutableStateOf(TextFieldState())
    var description by mutableStateOf(TextFieldState())
    var tags by mutableStateOf(TextFieldState())
    var commands by mutableStateOf(TextFieldState())
}
