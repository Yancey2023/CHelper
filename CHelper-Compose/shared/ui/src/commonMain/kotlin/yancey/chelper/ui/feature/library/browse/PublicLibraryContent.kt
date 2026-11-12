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

package yancey.chelper.ui.feature.library.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

data class PublicLibraryContentState(
    val libraries: List<LibraryEntry>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val isRecommendMode: Boolean,
    val hasMore: Boolean,
)

@Composable
fun PublicLibraryContent(
    state: PublicLibraryContentState,
    isFloatingWindow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onBookmarks: () -> Unit,
    onSearch: (String?) -> Unit,
    onMode: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFavorite: (LibraryEntry) -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onLoadMore: () -> Unit,
    tagOpensSearch: Boolean = true,
) {
    val listState = rememberLazyListState()
    val tagClickBehavior = rememberUpdatedState(if (tagOpensSearch) "search" else "detail")
    val reachedEnd by
        remember(listState, state) {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                state.libraries.isNotEmpty() &&
                    state.hasMore &&
                    !state.isLoading &&
                    state.errorMessage == null &&
                    last >= listState.layoutInfo.totalItemsCount - 3
            }
        }
    LaunchedEffect(reachedEnd) { if (reachedEnd) onLoadMore() }
    val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by
        spinTransition.animateFloat(
            0f,
            360f,
            infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
            label = "refreshSpinAngle",
        )
    RootViewWithHeaderAndCopyright(
        title = stringResource(Res.string.layout_library_list_title_public),
        // tab 模式下也展示返回箭头，但走 navController.popBackStack（回到上一个路由），
        // 而不是系统返回派发，避免误退到外部
        showBack = true,
        onBack = onBack,
        headerRight = {
            if (!isFloatingWindow) {
                Icon(
                    id = Res.drawable.bookmark,
                    modifier = Modifier.clickable { onBookmarks() }.padding(5.dp).size(24.dp),
                    contentDescription = "我的收藏",
                )
            }
            Icon(
                id = Res.drawable.refresh,
                modifier =
                    Modifier.clickable { onRefresh() }
                        .padding(5.dp)
                        .size(24.dp)
                        .rotate(if (state.isLoading) spinAngle else 0f),
                contentDescription = "刷新",
            )
        },
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 15.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .clickable { onSearch(null) }
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "随手搜命令库或标签...",
                    style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 14.sp),
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isRecommend = state.isRecommendMode
                // Tab 背景色动画：300ms 平滑过渡
                val latestBg by
                    animateColorAsState(
                        targetValue =
                            if (!isRecommend) CHelperTheme.colors.mainColor else Color.Transparent,
                        animationSpec = tween(300),
                        label = "latestTabBg",
                    )
                val recommendBg by
                    animateColorAsState(
                        targetValue =
                            if (isRecommend) CHelperTheme.colors.mainColor else Color.Transparent,
                        animationSpec = tween(300),
                        label = "recommendTabBg",
                    )
                Row(
                    modifier =
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(CHelperTheme.colors.backgroundComponent)
                            .padding(4.dp)
                ) {
                    Box(
                        modifier =
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(latestBg)
                                // 切到"最新发布"：直接走 switchMode，会自动用缓存或拉取，
                                // 不需要用户再去点刷新
                                .clickable {
                                    if (state.isRecommendMode) {
                                        onMode(false)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "最新发布",
                            style =
                                TextStyle(
                                    color =
                                        if (!isRecommend) Color.White
                                        else CHelperTheme.colors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight =
                                        if (!isRecommend) FontWeight.Bold else FontWeight.Normal,
                                ),
                        )
                    }
                    Box(
                        modifier =
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(recommendBg)
                                .clickable {
                                    // 已经在猜你喜欢里：再点 = 重新洗一批；否则切过去
                                    if (state.isRecommendMode) {
                                        onRefresh()
                                    } else {
                                        onMode(true)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "猜你喜欢",
                                style =
                                    TextStyle(
                                        color =
                                            if (isRecommend) Color.White
                                            else CHelperTheme.colors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight =
                                            if (isRecommend) FontWeight.Bold else FontWeight.Normal,
                                    ),
                            )
                            if (isRecommend) {
                                Spacer(Modifier.width(4.dp))
                                Image(
                                    painter = painterResource(Res.drawable.refresh),
                                    contentDescription = "刷新猜你喜欢",
                                    modifier =
                                        Modifier.size(12.dp)
                                            .rotate(if (state.isLoading) spinAngle else 0f),
                                    colorFilter = ColorFilter.tint(Color.White),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.errorMessage != null && state.libraries.isEmpty()) {
                    // 错误状态
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(15.dp, 0.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = CHelperTheme.colors.backgroundComponent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.errorMessage ?: "加载失败",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点击重试",
                                modifier = Modifier.clickable { onRetry() },
                                style = TextStyle(color = CHelperTheme.colors.mainColor),
                            )
                        }
                    }
                } else if (state.libraries.isEmpty() && !state.isLoading) {
                    // 空状态
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(15.dp, 0.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = CHelperTheme.colors.backgroundComponent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无数据",
                            style = TextStyle(color = CHelperTheme.colors.textSecondary),
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            state.libraries,
                            key = { _, library ->
                                library.remoteId ?: System.identityHashCode(library)
                            },
                        ) { _, library ->
                            PublicLibraryCard(
                                modifier = Modifier.animateItem(),
                                library = library,
                                onClick = { library.remoteId?.let { id -> onOpen(library) } },
                                onTagClick = { tag ->
                                    if (tagClickBehavior.value == "detail") {
                                        // 按设置：点 tag 相当于点击卡片，进入详情
                                        library.remoteId?.let { id -> onOpen(library) }
                                    } else {
                                        onSearch(tag)
                                    }
                                },
                                onFavorite = { onFavorite(library) },
                            )
                        }

                        // 加载更多指示器
                        if (state.errorMessage != null) {
                            item {
                                Text(
                                    text = "${state.errorMessage}，点击重试",
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clickable { onRetry() }
                                            .padding(15.dp),
                                    style = TextStyle(color = CHelperTheme.colors.mainColor),
                                )
                            }
                        }
                        if (state.isLoading) {
                            item {
                                // 呼吸式脉冲：Reverse 保证 0.3→1→0.3 平滑往返，
                                // 默认的 Restart 会让 alpha 从 1 瞬间跳回 0.3，视觉上一闪一闪
                                val infiniteTransition =
                                    rememberInfiniteTransition(label = "loading")
                                val alpha by
                                    infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1f,
                                        animationSpec =
                                            infiniteRepeatable(
                                                animation = tween(800, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse,
                                            ),
                                        label = "loadingAlpha",
                                    )
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "加载中...",
                                        style =
                                            TextStyle(
                                                color =
                                                    CHelperTheme.colors.textSecondary.copy(
                                                        alpha = alpha
                                                    )
                                            ),
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
