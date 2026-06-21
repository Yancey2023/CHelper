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

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.AuthorInfo
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.LibrarySearchScreenKey
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
    isTab: Boolean = false
) {
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val tagClickBehavior = settingsDataStore.tagClickBehavior()
        .collectAsState(initial = "search")
    // 用 nullable 初始值区分"DataStore 还没读到"和"读到 false"。
    // 之前用 collectAsState(initial = true) 等价于"还没读到默认按猜你喜欢拉"，
    // 但用户在设置里改成"最新发布"时，第一次合成 UI 已经用假值 true 触发了 switchMode(true)
    // → refresh 起飞、isLoading=true，真实值 false 到达再切 switchMode(false) 会被
    // loadFunctions 的 isLoading 拦截，结果 toggle 改了、信息流仍是猜你喜欢。
    val isPublicLibraryHomeRecommend by produceState<Boolean?>(
        initialValue = null,
        settingsDataStore
    ) {
        settingsDataStore.isPublicLibraryHomeRecommend().collect {
            Log.d("CPL_Tab", "DataStore emit: $it (previous: $value)")
            value = it
        }
    }
    val listState = rememberLazyListState()

    // 首次进入按设置初始化模式；之后用户在 tab 内的切换即为最终态，
    // 不要在每次重新进入页面时强行覆盖回设置默认（之前的 bug：切回云端总是变成猜你喜欢）
    LaunchedEffect(isPublicLibraryHomeRecommend) {
        val realValue = isPublicLibraryHomeRecommend
        Log.d(
            "CPL_Tab",
            "LaunchedEffect triggered: realValue=$realValue, currentLoadedMode=${viewModel.currentLoadedMode}, isRecommendMode=${viewModel.isRecommendMode}"
        )
        if (realValue == null) {
            Log.d("CPL_Tab", "realValue is null, skipping")
            return@LaunchedEffect
        }
        if (viewModel.currentLoadedMode == null) {
            Log.d("CPL_Tab", "First load, calling switchMode(isRecommend=$realValue)")
            viewModel.switchMode(isRecommend = realValue)
        } else {
            Log.d(
                "CPL_Tab",
                "Already loaded (currentLoadedMode=${viewModel.currentLoadedMode}), NOT calling switchMode"
            )
        }
    }

    // 检查是否有未读站内信
    LaunchedEffect(Unit) {
        try {
            val response =
                ServiceManager.COMMAND_LAB_USER_SERVICE.getUnreadCount()
            if (response.status == 0) {
                val count = response.data?.count ?: 0
                if (count > 0) {
                    Toast.makeText(
                        context,
                        "您有 $count 条未读站内信，可前往用户中心查看",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            // ignore error silently
        }
    }

    // 监听滚动到底部，自动加载更多
    // libraries.isNotEmpty() 防止空列表时 shouldLoadMore 立即为 true，
    // 抢先 loadMore 占住 isLoading 锁，导致后续 switchMode -> refresh 被拦截
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && !viewModel.isLoading && viewModel.hasMore && viewModel.libraries.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }.collect { if (it) viewModel.loadMore(isRecommend = viewModel.isRecommendMode) }
    }

    // 全屏共用的"刷新图标旋转角度"。infiniteRepeatable 一启动就常驻，
    // 在 isLoading 期间通过 Modifier.rotate 应用到对应图标上即可
    val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing)
        ),
        label = "refreshSpinAngle"
    )

    RootViewWithHeaderAndCopyright(
        title = stringResource(R.string.layout_library_list_title_public),
        // tab 模式下也展示返回箭头，但走 navController.popBackStack（回到上一个路由），
        // 而不是系统返回派发，避免误退到外部
        showBack = if (isTab) true else !isTab,
        onBack = if (isTab) ({ navController.popBackStack() }) else null,
        headerRight = {
            Icon(
                id = R.drawable.refresh,
                modifier =
                    Modifier
                        .clickable { viewModel.refresh(isRecommend = viewModel.isRecommendMode) }
                        .padding(5.dp)
                        .size(24.dp)
                        .rotate(if (viewModel.isLoading) spinAngle else 0f),
                contentDescription = "刷新"
            )
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
                                LibrarySearchScreenKey()
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isRecommend = viewModel.isRecommendMode
                // Tab 背景色动画：300ms 平滑过渡
                val latestBg by animateColorAsState(
                    targetValue = if (!isRecommend) CHelperTheme.colors.mainColor else Color.Transparent,
                    animationSpec = tween(300),
                    label = "latestTabBg"
                )
                val recommendBg by animateColorAsState(
                    targetValue = if (isRecommend) CHelperTheme.colors.mainColor else Color.Transparent,
                    animationSpec = tween(300),
                    label = "recommendTabBg"
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(latestBg)
                            // 切到"最新发布"：直接走 switchMode，会自动用缓存或拉取，
                            // 不需要用户再去点刷新
                            .clickable {
                                if (viewModel.isRecommendMode) {
                                    viewModel.switchMode(isRecommend = false)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "最新发布",
                            style = TextStyle(
                                color = if (!isRecommend) Color.White else CHelperTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (!isRecommend) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(recommendBg)
                            .clickable {
                                // 已经在猜你喜欢里：再点 = 重新洗一批；否则切过去
                                if (viewModel.isRecommendMode) {
                                    viewModel.refresh(isRecommend = true)
                                } else {
                                    viewModel.switchMode(isRecommend = true)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "猜你喜欢",
                                style = TextStyle(
                                    color = if (isRecommend) Color.White else CHelperTheme.colors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isRecommend) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (isRecommend) {
                                Spacer(Modifier.width(4.dp))
                                Image(
                                    painter = painterResource(R.drawable.refresh),
                                    contentDescription = "刷新猜你喜欢",
                                    modifier = Modifier
                                        .size(12.dp)
                                        .rotate(if (viewModel.isLoading) spinAngle else 0f),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }
                    }
                }
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
                                modifier = Modifier.clickable { viewModel.refresh(isRecommend = viewModel.isRecommendMode) },
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
                        itemsIndexed(
                            viewModel.libraries,
                            key = { _, library -> library.id ?: System.identityHashCode(library) }
                        ) { _, library ->
                            PublicLibraryItem(
                                modifier = Modifier.animateItem(),
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
                                            LibrarySearchScreenKey(tag)
                                        )
                                    }
                                }
                            )
                        }

                        // 加载更多指示器
                        if (viewModel.isLoading) {
                            item {
                                // 呼吸式脉冲：Reverse 保证 0.3→1→0.3 平滑往返，
                                // 默认的 Restart 会让 alpha 从 1 瞬间跳回 0.3，视觉上一闪一闪
                                val infiniteTransition =
                                    rememberInfiniteTransition(label = "loading")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "loadingAlpha"
                                )
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
                                                        .textSecondary.copy(alpha = alpha)
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
    modifier: Modifier = Modifier,
    library: LibraryFunction,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit = {}
) {
    // 热门或高Tier创作者加主题色亮边与背景高亮
    val isFeatured = (library.likeCount ?: 0) >= 10 || (library.author?.tier ?: 0) >= 2

    Column(
        modifier = modifier
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
