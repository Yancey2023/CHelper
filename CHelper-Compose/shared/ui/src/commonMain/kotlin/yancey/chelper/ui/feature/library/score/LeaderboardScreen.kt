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

package yancey.chelper.ui.feature.library.score

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.LeaderboardEntry
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.navigation.UserProfileScreenKey
import yancey.chelper.ui.platform.LocalClientServices

@Composable
fun LeaderboardScreen(
    navController: NavHostController?,
    libraries: LibraryContentRepository = LocalClientServices.current.libraryContent,
    viewModel: LeaderboardViewModel = viewModel { LeaderboardViewModel(libraries) },
) {
    var publishMode by rememberSaveable { mutableStateOf(false) }
    val sortedUsers =
        remember(viewModel.leaderboard, publishMode) {
            if (publishMode) {
                viewModel.leaderboard.sortedWith(
                    compareByDescending<LeaderboardEntry> { it.totalFunctions ?: 0 }
                        .thenByDescending { it.totalLikes ?: 0 }
                )
            } else {
                viewModel.leaderboard.sortedWith(
                    compareByDescending<LeaderboardEntry> { it.totalLikes ?: 0 }
                        .thenByDescending { it.totalFunctions ?: 0 }
                )
            }
        }
    RootViewWithHeaderAndCopyright(title = "创作者排行榜") {
        if (viewModel.isLoading && viewModel.leaderboard.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", style = TextStyle(color = CHelperTheme.colors.textSecondary))
            }
        } else if (viewModel.errorMessage != null && viewModel.leaderboard.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter =
                            painterResource(resource = Res.drawable.triangle_exclamation_point),
                        contentDescription = "Error",
                        modifier = Modifier.size(48.dp),
                        colorFilter = ColorFilter.tint(CHelperTheme.colors.mainColor),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = viewModel.errorMessage!!,
                        style = TextStyle(color = CHelperTheme.colors.textSecondary),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier =
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(CHelperTheme.colors.mainColor)
                                .clickable { viewModel.refresh() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("重试", style = TextStyle(color = Color.White))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(resource = Res.drawable.shield_check),
                            contentDescription = "Leaderboard",
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "CommandLab 创作者",
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CHelperTheme.colors.textMain,
                                ),
                        )
                        Text(
                            text = "致敬最杰出的命令书写者们。",
                            style =
                                TextStyle(
                                    fontSize = 14.sp,
                                    color = CHelperTheme.colors.textSecondary,
                                ),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                item {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CHelperTheme.colors.backgroundComponent)
                                .padding(4.dp)
                    ) {
                        LeaderboardModeTab(
                            title = "点赞榜",
                            selected = !publishMode,
                            modifier = Modifier.weight(1f),
                            onClick = { publishMode = false },
                        )
                        LeaderboardModeTab(
                            title = "发布榜",
                            selected = publishMode,
                            modifier = Modifier.weight(1f),
                            onClick = { publishMode = true },
                        )
                    }
                }

                itemsIndexed(sortedUsers) { index, user ->
                    LeaderboardItem(
                        rank = index + 1,
                        user = user,
                        publishMode = publishMode,
                        onClick = {
                            user.id?.let { navController?.navigate(UserProfileScreenKey(it)) }
                        },
                    )
                }

                if (viewModel.leaderboard.isEmpty() && !viewModel.isLoading) {
                    item {
                        Text(
                            text = "暂无数据",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            style =
                                TextStyle(
                                    textAlign = TextAlign.Center,
                                    color = CHelperTheme.colors.textSecondary,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardModeTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) CHelperTheme.colors.mainColor else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else CHelperTheme.colors.textSecondary,
                ),
        )
    }
}

@Composable
private fun LeaderboardItem(
    rank: Int,
    user: LeaderboardEntry,
    publishMode: Boolean,
    onClick: () -> Unit,
) {
    val isTop3 = rank <= 3
    val rankColor =
        when (rank) {
            1 -> Color(0xFFFFD700)
            2 -> Color(0xFFC0C0C0)
            3 -> Color(0xFFCD7F32)
            else -> CHelperTheme.colors.textSecondary
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isTop3) CHelperTheme.colors.mainColor.copy(alpha = 0.05f)
                    else CHelperTheme.colors.backgroundComponent
                )
                .clickable { onClick() }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style =
                TextStyle(
                    fontSize = if (isTop3) 24.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                ),
            modifier = Modifier.width(36.dp),
        )

        AsyncImage(
            model = user.avatarUrl ?: "https://abyssous.site/avatar/${user.id}",
            contentDescription = "Avatar",
            modifier =
                Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(CHelperTheme.colors.backgroundComponent),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(resource = Res.drawable.user_round),
            error = painterResource(resource = Res.drawable.user_round),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.nickname ?: "Unknown",
                    style =
                        TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = CHelperTheme.colors.textMain,
                        ),
                )
                if ((user.tier ?: 0) >= 2) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Image(
                        painter = painterResource(resource = Res.drawable.shield_check),
                        contentDescription = "Tier",
                        modifier = Modifier.size(16.dp),
                    )
                } else if ((user.tier ?: 0) >= 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Image(
                        painter = painterResource(resource = Res.drawable.badge_check),
                        contentDescription = "Tier",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = user.userTitle ?: "Tier ${user.tier ?: 0}",
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.mainColor),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter =
                        painterResource(
                            resource = if (publishMode) Res.drawable.box else Res.drawable.heart
                        ),
                    contentDescription = if (publishMode) "Functions" else "Likes",
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(CHelperTheme.colors.mainColor),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${if (publishMode) user.totalFunctions ?: 0 else user.totalLikes ?: 0}",
                    style = TextStyle(fontSize = 14.sp, color = CHelperTheme.colors.textMain),
                )
            }
            Text(
                text =
                    if (publishMode) "${user.totalLikes ?: 0} 个赞"
                    else "${user.totalFunctions ?: 0} 库",
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
    }
}
