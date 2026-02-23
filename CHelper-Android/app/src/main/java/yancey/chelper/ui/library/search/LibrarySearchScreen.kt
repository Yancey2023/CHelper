/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

package yancey.chelper.ui.library.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.LibraryShowScreenKey
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun LibrarySearchScreen(
    initialKeyword: String? = null,
    viewModel: LibrarySearchViewModel = viewModel(),
    navController: NavHostController
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // 初始带关键字搜索
    LaunchedEffect(initialKeyword) {
        viewModel.setInitialKeyword(initialKeyword)
        if (!initialKeyword.isNullOrBlank()) {
            viewModel.search()
        } else {
            // 如果没带关键字，自动弹起键盘给输入框焦点
            focusRequester.requestFocus()
        }
    }

    // 滑动加载更多公开库
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && !viewModel.isLoading && viewModel.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .collect { if (it) viewModel.loadMore() }
    }

    RootViewWithHeaderAndCopyright(
        title = "搜索命令库"
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    state = viewModel.keyword,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .focusRequester(focusRequester),
                    contentAlignment = Alignment.CenterStart,
                    hint = "搜索命令库或标签...",
                    horizontalPadding = 20.dp,
                    verticalPadding = 0.dp,
                    clipCornerSize = 20.dp
                )
                Spacer(Modifier.width(10.dp))
                // 使用文字代替不存在的搜索按钮
                Text(
                    text = "搜索",
                    style = TextStyle(
                        color = CHelperTheme.colors.mainColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .clickable {
                            focusManager.clearFocus()
                            viewModel.search()
                        }
                        .padding(5.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (viewModel.isLoading && viewModel.localAndPrivateLibraries.isEmpty() && viewModel.publicLibraries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("搜索中...", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                    }
                } else if (!viewModel.isLoading && viewModel.localAndPrivateLibraries.isEmpty() && viewModel.publicLibraries.isEmpty() && viewModel.keyword.text.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到相关匹配项", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(15.dp, 0.dp)
                    ) {
                        // Section 1: 本地与私有
                        if (viewModel.localAndPrivateLibraries.isNotEmpty()) {
                            item {
                                Text(
                                    text = "我的本地与云端私有",
                                    style = TextStyle(
                                        color = CHelperTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CHelperTheme.colors.backgroundComponent)
                                ) {
                                    viewModel.localAndPrivateLibraries.forEachIndexed { index, lib ->
                                        SearchLibraryItem(
                                            library = lib,
                                            isPrivateOrLocal = true,
                                            onClick = {
                                                lib.id?.let { id ->
                                                    // 根据标识判断是本地包还是私有库
                                                    if (lib.author == "[本地包]") {
                                                        navController.navigate(LibraryShowScreenKey(id = id))
                                                    } else {
                                                        navController.navigate(PublicLibraryShowScreenKey(id = id, isPrivate = true))
                                                    }
                                                }
                                            }
                                        )
                                        if (index < viewModel.localAndPrivateLibraries.size - 1) {
                                            Divider(padding = 0.dp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }

                        // Section 2: 公开市场
                        if (viewModel.publicLibraries.isNotEmpty()) {
                            item {
                                Text(
                                    text = "公共市场搜索结果",
                                    style = TextStyle(
                                        color = CHelperTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(bottom = 5.dp)
                                )
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                        .background(CHelperTheme.colors.backgroundComponent)
                                        // 为了美观预留一点内边距效果，目前用 Divider 分割
                                )
                            }
                            
                            itemsIndexed(viewModel.publicLibraries) { _, library ->
                                Box(modifier = Modifier.background(CHelperTheme.colors.backgroundComponent)) {
                                    Column {
                                        SearchLibraryItem(
                                            library = library,
                                            isPrivateOrLocal = false,
                                            onClick = {
                                                library.id?.let { id ->
                                                    navController.navigate(PublicLibraryShowScreenKey(id = id, isPrivate = false))
                                                }
                                            }
                                        )
                                        Divider(padding = 0.dp)
                                    }
                                }
                            }
                            
                            // 闭合背景
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(CHelperTheme.colors.background)
                                )
                            }
                        }

                        if (viewModel.isLoading && (viewModel.localAndPrivateLibraries.isNotEmpty() || viewModel.publicLibraries.isNotEmpty())) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("加载更多...", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLibraryItem(
    library: LibraryFunction,
    isPrivateOrLocal: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = library.name ?: "未命名",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.textMain
                    )
                )
                // 如果是特殊标致给个 Tag
                if (isPrivateOrLocal && !library.author.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CHelperTheme.colors.mainColor.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = library.author!!,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = CHelperTheme.colors.mainColor
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isPrivateOrLocal && !library.author.isNullOrBlank()) {
                    Text(text = "作者: ${library.author}", style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 12.sp))
                    Spacer(Modifier.width(10.dp))
                }
                
                library.like_count?.let { likes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(CHelperTheme.colors.textSecondary)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(text = "$likes", style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 12.sp))
                    }
                }
            }
            library.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.replace("\n", " "),
                    style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }
        Icon(
            id = R.drawable.arrow_right,
            contentDescription = "查看详情",
            modifier = Modifier.size(20.dp)
        )
    }
}
