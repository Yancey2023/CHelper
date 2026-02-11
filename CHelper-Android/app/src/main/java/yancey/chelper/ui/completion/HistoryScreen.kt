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

package yancey.chelper.ui.completion

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    LaunchedEffect(viewModel) {
        viewModel.init(context)
    }
    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_library_list_title_local)
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(15.dp, 0.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color = CHelperTheme.colors.backgroundComponent)
            ) {
                items(viewModel.contents) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp, 10.dp)
                            .defaultMinSize(minHeight = 24.dp)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterVertically)
                                .weight(1f),
                            style = TextStyle(
                                color = CHelperTheme.colors.mainColor,
                            )
                        )
                        Icon(
                            id = R.drawable.copy,
                            contentDescription = stringResource(R.string.common_icon_copy_content_description),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clickable {
                                    viewModel.viewModelScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText(
                                                    null,
                                                    it
                                                )
                                            )
                                        )
                                    }
                                }
                                .padding(start = 5.dp)
                                .size(24.dp)
                        )
                    }
                    Divider(padding = 0.dp)
                }
            }
        }
    }
}

@Preview
@Composable
fun HistoryScreenLightThemePreview() {
    val viewModel = remember {
        HistoryViewModel().apply {
            contents = mutableListOf<String>().apply {
                for (i in 0..100) {
                    add("Command $i")
                }
            }.toList()
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        HistoryScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
fun HistoryScreenDarkThemePreview() {
    val viewModel = remember {
        HistoryViewModel().apply {
            contents = mutableListOf<String>().apply {
                for (i in 0..100) {
                    add("Command $i")
                }
            }.toList()
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        HistoryScreen(viewModel = viewModel)
    }
}