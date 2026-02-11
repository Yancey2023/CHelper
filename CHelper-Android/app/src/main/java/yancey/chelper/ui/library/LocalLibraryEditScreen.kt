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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.android.library.util.LocalLibraryManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun LocalLibraryEditScreen(viewModel: LocalLibraryEditViewModel = viewModel(), id: Int? = null) {
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(viewModel, id) {
        viewModel.id = id
        if (id != null) {
            viewModel.viewModelScope.launch {
                LocalLibraryManager.INSTANCE!!.ensureInit()
                val libraryFunction = LocalLibraryManager.INSTANCE!!.getFunctions()[id]
                viewModel.name.setTextAndPlaceCursorAtEnd(libraryFunction.name ?: "")
                viewModel.version.setTextAndPlaceCursorAtEnd(libraryFunction.version ?: "")
                viewModel.author.setTextAndPlaceCursorAtEnd(libraryFunction.author ?: "")
                viewModel.description.setTextAndPlaceCursorAtEnd(libraryFunction.note ?: "")
                viewModel.tags.setTextAndPlaceCursorAtEnd(
                    libraryFunction.tags?.joinToString(separator = ",") ?: ""
                )
                viewModel.commands.setTextAndPlaceCursorAtEnd(libraryFunction.content ?: "")
            }
        }
    }
    RootViewWithHeaderAndCopyright(
        title = when (viewModel.mode) {
            EditMode.ADD -> stringResource(R.string.layout_library_edit_title_add)
            EditMode.UPDATE -> stringResource(R.string.layout_library_edit_title_edit)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layout_library_edit_name_label))
                TextField(
                    state = viewModel.name,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layout_library_edit_version_label))
                TextField(
                    state = viewModel.version,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    hint = stringResource(R.string.layout_library_edit_version_hint),
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layout_library_edit_author_label))
                TextField(
                    state = viewModel.author,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layout_library_edit_description_label))
                TextField(
                    state = viewModel.description,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layout_library_edit_tags_label))
                TextField(
                    state = viewModel.tags,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    state = viewModel.commands,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp),
                    contentAlignment = Alignment.TopStart,
                    hint = stringResource(R.string.layout_library_edit_commands_hint)
                )
            }
            Spacer(Modifier.height(15.dp))
            Button(stringResource(R.string.layout_library_edit_save)) {
                viewModel.viewModelScope.launch {
                    val libraryFunction = LibraryFunction()
                    libraryFunction.name = viewModel.name.text.toString()
                    libraryFunction.version = viewModel.version.text.toString()
                    libraryFunction.author = viewModel.author.text.toString()
                    libraryFunction.note = viewModel.description.text.toString()
                    libraryFunction.tags =
                        viewModel.tags.text.toString().split(",").map { it.trim() }
                            .filter { !it.isEmpty() }.toList()
                    libraryFunction.content = viewModel.commands.text.toString()
                    LocalLibraryManager.INSTANCE!!.ensureInit()
                    val functions = LocalLibraryManager.INSTANCE!!.getFunctions()
                    when (viewModel.mode) {
                        EditMode.ADD -> {
                            functions.add(libraryFunction)
                        }

                        EditMode.UPDATE -> {
                            functions[id!!] = libraryFunction
                        }
                    }
                    LocalLibraryManager.INSTANCE!!.save()
                    onBackPressedDispatcher?.onBackPressed()
                }
            }
            if (viewModel.mode == EditMode.UPDATE) {
                Button(stringResource(R.string.layout_library_edit_delete)) {
                    viewModel.viewModelScope.launch {
                        LocalLibraryManager.INSTANCE!!.ensureInit()
                        val functions = LocalLibraryManager.INSTANCE!!.getFunctions()
                        functions.removeAt(id!!)
                        LocalLibraryManager.INSTANCE!!.save()
                        onBackPressedDispatcher?.onBackPressed()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun LocalLibraryEditScreenScreenLightThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryEditScreen()
    }
}

@Preview
@Composable
fun LocalLibraryEditScreenDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryEditScreen()
    }
}
