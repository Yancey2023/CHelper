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

package yancey.chelper.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import yancey.chelper.R
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.common.widget.TextField

@Composable
fun PublicLibraryListScreen(
    viewModel: PublicLibraryListViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val listState = rememberLazyListState()
    
    // 初始加载
    LaunchedEffect(Unit) {
        viewModel.loadFunctions()
    }
    
    // 监听滚动到底部，自动加载更多
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
        title = stringResource(R.string.layout_library_list_title_public),
        headerRight = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    id = R.drawable.folder,
                    modifier = Modifier
                        .clickable { navController.navigate(yancey.chelper.ui.CPLUserScreenKey) }
                        .padding(5.dp)
                        .size(24.dp),
                    contentDescription = "用户中心"
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    id = R.drawable.refresh,
                    modifier = Modifier
                        .clickable { viewModel.refresh() }
                        .padding(5.dp)
                        .size(24.dp),
                    contentDescription = "刷新"
                )
            }
        }
    ) {
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
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (viewModel.errorMessage != null && viewModel.libraries.isEmpty()) {
                    // 错误状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.errorMessage ?: "加载失败",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点击重试",
                                modifier = Modifier.clickable { viewModel.refresh() },
                                style = TextStyle(color = CHelperTheme.colors.mainColor)
                            )
                        }
                    }
                } else if (viewModel.libraries.isEmpty() && !viewModel.isLoading) {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无数据",
                            style = TextStyle(color = CHelperTheme.colors.textSecondary)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(15.dp, 0.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color = CHelperTheme.colors.backgroundComponent)
                    ) {
                        itemsIndexed(viewModel.libraries) { _, library ->
                            PublicLibraryItem(
                                library = library,
                                onClick = {
                                    library.id?.let { id ->
                                        navController.navigate(PublicLibraryShowScreenKey(id = id))
                                    }
                                }
                            )
                            Divider(padding = 0.dp)
                        }
                        
                        // 加载更多指示器
                        if (viewModel.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "加载中...",
                                        style = TextStyle(color = CHelperTheme.colors.textSecondary)
                                    )
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
private fun PublicLibraryItem(
    library: LibraryFunction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(20.dp, 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name ?: "未命名",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Row {
                library.author?.let { author ->
                    Text(
                        text = "作者: $author",
                        style = TextStyle(
                            color = CHelperTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
                library.like_count?.let { likes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = " · ",
                            style = TextStyle(
                                color = CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(CHelperTheme.colors.textSecondary)
                        )
                        Text(
                            text = " $likes",
                             style = TextStyle(
                                color = CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
            library.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = CHelperTheme.colors.textSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            }
        }
        Icon(
            id = R.drawable.arrow_right,
            contentDescription = "查看详情",
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(20.dp)
        )
    }
}

@Preview
@Composable
fun PublicLibraryListScreenLightThemePreview() {
    val viewModel = remember {
        PublicLibraryListViewModel().apply {
            for (i in 0..10) {
                libraries.add(LibraryFunction().apply {
                    id = i
                    name = "Library $i"
                    author = "Author $i"
                    note = "Description for library $i"
                    like_count = i * 10
                })
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        PublicLibraryListScreen(viewModel = viewModel)
    }
}

@Preview
@Composable
fun PublicLibraryListScreenDarkThemePreview() {
    val viewModel = remember {
        PublicLibraryListViewModel().apply {
            for (i in 0..10) {
                libraries.add(LibraryFunction().apply {
                    id = i
                    name = "Library $i"
                    author = "Author $i"
                    note = "This is a longer description for library $i"
                    like_count = i * 5
                })
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        PublicLibraryListScreen(viewModel = viewModel)
    }
}
