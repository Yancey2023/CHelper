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

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

@Composable
fun LocalLibraryShowScreen(viewModel: LocalLibraryShowViewModel = viewModel()) {
    RootViewWithHeaderAndCopyright(title = viewModel.library.name ?: "加载中") {
        val clipboard = LocalClipboard.current
        val contents: List<Pair<Boolean, String>> = remember(viewModel.library) {
            viewModel.library.content
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { !it.isEmpty() }
                ?.map {
                    if (it.startsWith("#")) {
                        true to it.substring(1).trim()
                    } else {
                        false to it
                    }
                }
                ?.filter { !it.second.isEmpty() }
                ?: listOf()
        }
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color = CHelperTheme.colors.backgroundComponent)
            ) {
                items(contents) { content ->
                    Row(
                        modifier = Modifier
                            .padding(20.dp, 10.dp)
                            .defaultMinSize(minHeight = 24.dp)
                    ) {
                        Text(
                            text = content.second,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterVertically)
                                .weight(1f),
                            style = TextStyle(
                                color = if (content.first) CHelperTheme.colors.mainColor else CHelperTheme.colors.textMain,
                            )
                        )
                        if (content.first) {
                            val scope = rememberCoroutineScope()
                            Icon(
                                id = R.drawable.copy,
                                contentDescription = stringResource(R.string.common_icon_copy_content_description),
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .clickable {
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(
                                                    ClipData.newPlainText(
                                                        null,
                                                        content.second
                                                    )
                                                )
                                            )
                                        }
                                    }
                                    .padding(start = 5.dp)
                                    .size(24.dp)
                            )
                        }
                    }
                    Divider(padding = 0.dp)
                }
            }
        }
    }
}

@Preview
@Composable
fun LibraryShowScreenLightThemePreview() {
    val viewModel: LocalLibraryShowViewModel = viewModel()
    viewModel.library = LibraryFunction().apply {
        name = "Library"
        author = "Author"
        content = buildString {
            for (i in 1..10) {
                append("# Content${i * 2 - 1}\n")
                append("Content${i * 2}\n")
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryShowScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
fun LibraryShowScreenDarkThemePreview() {
    val viewModel: LocalLibraryShowViewModel = viewModel()
    viewModel.library = LibraryFunction().apply {
        name = "Library"
        author = "Author"
        content = buildString {
            for (i in 1..10) {
                append("# Content${i * 2 - 1}\n")
                append("Content${i * 2}\n")
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryShowScreen(viewModel = viewModel)
    }
}
