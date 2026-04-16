/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition. Copyright (C)
 * 2026 Akanyi
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */
package yancey.chelper.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import yancey.chelper.R
import yancey.chelper.network.library.data.AuthorInfo
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.PublicLibraryShowScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

@Composable
fun PublicLibraryListScreen(
    viewModel: PublicLibraryListViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    isFloatingWindow: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsDataStore = remember(context) { yancey.chelper.data.SettingsDataStore(context) }
    val tagClickBehavior = settingsDataStore.tagClickBehavior()
        .collectAsState(initial = "search")
    val listState = rememberLazyListState()

    // 初始加载
    LaunchedEffect(Unit) { viewModel.loadFunctions() }

    // 监听滚动到底部，自动加载更多
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && !viewModel.isLoading && viewModel.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }.collect { if (it) viewModel.loadMore() }
    }

    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_library_list_title_public),
        headerRight = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isFloatingWindow) {
                    Icon(
                        id = R.drawable.folder,
                        modifier =
                            Modifier
                                .clickable {
                                    navController.navigate(
                                        yancey.chelper.ui.CPLUserScreenKey
                                    )
                                }
                                .padding(5.dp)
                                .size(24.dp),
                        contentDescription = "用户中心"
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Icon(
                    id = R.drawable.refresh,
                    modifier =
                        Modifier
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
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 15.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .clickable {
                            navController.navigate(
                                yancey.chelper.ui.LibrarySearchScreenKey()
                            )
                        }
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "随手搜命令库或标签...",
                    style =
                        TextStyle(
                            color = CHelperTheme.colors.textSecondary,
                            fontSize = 14.sp
                        )
                )
            }
            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                if (viewModel.errorMessage != null && viewModel.libraries.isEmpty()) {
                    // 错误状态
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(15.dp, 0.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    color = CHelperTheme.colors.backgroundComponent
                                ),
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(15.dp, 0.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    color = CHelperTheme.colors.backgroundComponent
                                ),
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                    ) {
                        itemsIndexed(viewModel.libraries) { _, library ->
                            PublicLibraryItem(
                                library = library,
                                onClick = {
                                    library.id?.let { id ->
                                        navController.navigate(
                                            PublicLibraryShowScreenKey(id = id)
                                        )
                                    }
                                },
                                onTagClick = { tag ->
                                    if (tagClickBehavior.value == "detail") {
                                        // 按设置：点 tag 相当于点击卡片，进入详情
                                        library.id?.let { id ->
                                            navController.navigate(
                                                PublicLibraryShowScreenKey(id = id)
                                            )
                                        }
                                    } else {
                                        navController.navigate(
                                            yancey.chelper.ui.LibrarySearchScreenKey(tag)
                                        )
                                    }
                                }
                            )
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
                                        style =
                                            TextStyle(
                                                color =
                                                    CHelperTheme.colors
                                                        .textSecondary
                                            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicLibraryItem(
    library: LibraryFunction,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit = {}
) {
    // 热门或高Tier创作者加主题色亮边与背景高亮
    val isFeatured = (library.likeCount ?: 0) >= 10 || (library.author?.tier ?: 0) >= 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFeatured) CHelperTheme.colors.mainColor.copy(alpha = 0.08f) else CHelperTheme.colors.backgroundComponent)
            .run {
                if (isFeatured) this.border(
                    1.dp,
                    CHelperTheme.colors.mainColor.copy(alpha = 0.3f),
                    RoundedCornerShape(10.dp)
                ) else this
            }
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp, 12.dp)
            ) {
                // 标题行 + 版本号
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = library.name ?: "未命名",
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    library.version?.takeIf { it.isNotBlank() }?.let { ver ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "v$ver",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = CHelperTheme.colors.textSecondary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 作者 + 点赞
                Row(verticalAlignment = Alignment.CenterVertically) {
                    library.author?.let { author ->
                        AsyncImage(
                            model = "https://abyssous.site/avatar/${author.id}",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CHelperTheme.colors.backgroundComponent),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_user),
                            error = painterResource(id = R.drawable.ic_user)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = author.name ?: "Unknown",
                            style = TextStyle(
                                color = CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                        if ((author.tier ?: 0) >= 2) {
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(R.drawable.ic_verified_advanced),
                                contentDescription = "Advanced",
                                modifier = Modifier.size(12.dp)
                            )
                        } else if ((author.tier ?: 0) >= 1) {
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(R.drawable.ic_verified_normal),
                                contentDescription = "Normal",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    } ?: library.authorName?.let { author ->
                        Text(
                            text = author,
                            style = TextStyle(
                                color = CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                    library.likeCount?.let { likes ->
                        if (library.authorName != null) {
                            Text(
                                text = " · ",
                                style = TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Image(
                            painter = painterResource(
                                if (isFeatured) R.drawable.heart_filled else R.drawable.ic_heart
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(
                                if (isFeatured) CHelperTheme.colors.mainColor
                                else CHelperTheme.colors.textSecondary
                            )
                        )
                        Text(
                            text = " $likes",
                            style = TextStyle(
                                color = if (isFeatured) CHelperTheme.colors.mainColor
                                else CHelperTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 标签
                library.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    FlowRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CHelperTheme.colors.background)
                                    .clickable { onTagClick(tag) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = TextStyle(
                                        color = CHelperTheme.colors.mainColor,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                style = TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // 备注
                library.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(4.dp))
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

            // 右侧箭头
            Icon(
                id = R.drawable.arrow_right,
                contentDescription = "查看详情",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp)
                    .size(18.dp)
            )
        }
    }
}

@Preview
@Composable
fun PublicLibraryListScreenLightThemePreview() {
    val viewModel = remember {
        PublicLibraryListViewModel().apply {
            for (i in 0..10) {
                libraries.add(
                    LibraryFunction().apply {
                        id = i
                        name = "Library $i"
                        author = AuthorInfo(name = "Author $i")
                        note = "Description for library $i"
                        likeCount = i * 10
                    }
                )
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
                libraries.add(
                    LibraryFunction().apply {
                        id = i
                        name = "Library $i"
                        author = AuthorInfo(name = "Author $i")
                        note = "This is a longer description for library $i"
                        likeCount = i * 5
                    }
                )
            }
        }
    }
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        PublicLibraryListScreen(viewModel = viewModel)
    }
}
