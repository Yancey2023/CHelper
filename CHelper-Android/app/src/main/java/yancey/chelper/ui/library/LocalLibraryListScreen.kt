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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.gson.reflect.TypeToken
import com.hjq.toast.Toaster
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.android.library.util.LocalLibraryManager
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.LibraryEditScreenKey
import yancey.chelper.ui.LibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun LocalLibraryListScreen(
    viewModel: LocalLibraryListViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val clipboard = LocalClipboard.current
    val filteredLibraries = remember(viewModel.keyword.text, viewModel.libraries) {
        if (viewModel.keyword.text.isEmpty()) {
            viewModel.libraries
        } else {
            viewModel.libraries.filter { it.name != null && it.name!!.contains(viewModel.keyword.text) }
        }
    }
    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_library_list_title_local),
        headerRight = {
            Icon(
                id = R.drawable.file_arrow_left,
                modifier = Modifier
                    .clickable {
                        viewModel.isShowImportDialog = true
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = stringResource(R.string.layout_library_list_icon_import_content_description)
            )
            Icon(
                id = R.drawable.share,
                modifier = Modifier
                    .clickable {
                        viewModel.isShowExportDialog = true
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = stringResource(R.string.layout_library_list_icon_export_content_description)
            )
            Icon(
                id = R.drawable.plus,
                modifier = Modifier
                    .clickable {
                        navController.navigate(LibraryEditScreenKey(id = null))
                    }
                    .padding(5.dp)
                    .size(24.dp),
                contentDescription = stringResource(R.string.layout_library_list_icon_add_content_description)
            )
        }) {
        Column {
            Spacer(Modifier.height(10.dp))
            TextField(
                state = viewModel.keyword,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart,
                hint = stringResource(R.string.layout_library_list_search_hint),
                horizontalPadding = 20.dp,
                verticalPadding = 0.dp,
                clipCornerSize = 20.dp,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(15.dp, 0.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color = CHelperTheme.colors.backgroundComponent)
            ) {
                itemsIndexed(filteredLibraries) { index, library ->
                    Row(
                        modifier = Modifier
                            .clickable(onClick = {
                                navController.navigate(LibraryShowScreenKey(id = index))
                            })
                            .padding(20.dp, 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = library.name!!,
                                modifier = Modifier
                                    .fillMaxWidth(),
                                style = TextStyle(
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = library.note!!,
                                modifier = Modifier
                                    .fillMaxWidth(),
                                style = TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Icon(
                            id = R.drawable.pencil,
                            contentDescription = stringResource(R.string.layout_library_list_icon_edit_content_description),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clickable {
                                    navController.navigate(LibraryEditScreenKey(id = index))
                                }
                                .size(24.dp)
                        )
                    }
                    Divider(padding = 0.dp)
                }
            }
        }
    }
    if (viewModel.isShowImportDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowImportDialog = false },
            title = "从剪切板导入",
            content = "请把要导入的数据放到剪切板",
            confirmText = "导入",
            onConfirm = {
                viewModel.viewModelScope.launch {
                    clipboard.getClipEntry()?.clipData?.apply {
                        if (itemCount > 0) {
                            val text = getItemAt(0).text.toString()
                            try {
                                viewModel.libraries.addAll(
                                    ServiceManager.GSON!!.fromJson(
                                        text,
                                        object : TypeToken<MutableList<LibraryFunction>>() {
                                        }.type
                                    )
                                )
                                LocalLibraryManager.INSTANCE!!.save()
                                Toaster.show("导入成功")
                            } catch (_: Throwable) {
                                Toaster.show("导入失败")
                            }
                        }
                    }
                }
            }
        )
    }
    if (viewModel.isShowExportDialog) {
        val output = remember(viewModel.libraries) {
            ServiceManager.GSON!!.toJson(viewModel.libraries)
        }
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowExportDialog = false },
            title = "导出",
            content = output,
            confirmText = "复制",
            onConfirm = {
                viewModel.viewModelScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                null,
                                output
                            )
                        )
                    )
                }
            }
        )
    }
}

@Preview
@Composable
fun LocalLibraryListScreenLightThemePreview() {
    val viewModel = remember {
        LocalLibraryListViewModel().apply {
            for (i in 0..100) {
                libraries.add(LibraryFunction().apply {
                    name = "Library $i"
                    note = "Author $i"
                })
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        LocalLibraryListScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
fun LocalLibraryListScreenDarkThemePreview() {
    val viewModel = remember {
        LocalLibraryListViewModel().apply {
            for (i in 0..100) {
                libraries.add(LibraryFunction().apply {
                    name = "Library $i"
                    note = "Description $i"
                })
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        LocalLibraryListScreen(viewModel = viewModel)
    }
}