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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.domain.library.authorName
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun PublicLibraryCard(
    modifier: Modifier = Modifier,
    library: LibraryEntry,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onFavorite: () -> Unit = {},
) {
    // 热门或高Tier创作者加主题色亮边与背景高亮
    val isFeatured = (library.likeCount ?: 0) >= 10 || (library.author?.tier ?: 0) >= 2

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isFeatured) CHelperTheme.colors.mainColor.copy(alpha = 0.08f)
                    else CHelperTheme.colors.backgroundComponent
                )
                .run {
                    if (isFeatured)
                        this.border(
                            1.dp,
                            CHelperTheme.colors.mainColor.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp),
                        )
                    else this
                }
                .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(14.dp, 12.dp)) {
                // 标题行 + 版本号
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = library.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    library.version
                        .takeIf { it.isNotBlank() }
                        ?.let { ver ->
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "v$ver",
                                style =
                                    TextStyle(
                                        fontSize = 11.sp,
                                        color = CHelperTheme.colors.textSecondary,
                                    ),
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
                            modifier =
                                Modifier.size(24.dp)
                                    .clip(CircleShape)
                                    .background(CHelperTheme.colors.backgroundComponent),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(Res.drawable.user_round),
                            error = painterResource(Res.drawable.user_round),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = author.name ?: "Unknown",
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                ),
                        )
                        if ((author.tier ?: 0) >= 2) {
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(Res.drawable.shield_check),
                                contentDescription = "Advanced",
                                modifier = Modifier.size(12.dp),
                            )
                        } else if ((author.tier ?: 0) >= 1) {
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(Res.drawable.badge_check),
                                contentDescription = "Normal",
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                        ?: Text(
                            text = library.authorName,
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                ),
                        )
                    library.likeCount?.let { likes ->
                        Text(
                            text = " · ",
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                ),
                        )
                        Image(
                            painter = painterResource(Res.drawable.heart),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter =
                                ColorFilter.tint(
                                    if (isFeatured) CHelperTheme.colors.mainColor
                                    else CHelperTheme.colors.textSecondary
                                ),
                        )
                        Text(
                            text = " $likes",
                            style =
                                TextStyle(
                                    color =
                                        if (isFeatured) CHelperTheme.colors.mainColor
                                        else CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                ),
                        )
                    }
                }

                // 标签
                library.tags
                    .takeIf { it.isNotEmpty() }
                    ?.let { tags ->
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.take(3).forEach { tag ->
                                Box(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(CHelperTheme.colors.background)
                                            .clickable { onTagClick(tag) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style =
                                            TextStyle(
                                                color = CHelperTheme.colors.mainColor,
                                                fontSize = 11.sp,
                                            ),
                                    )
                                }
                            }
                            if (tags.size > 3) {
                                Text(
                                    text = "+${tags.size - 3}",
                                    style =
                                        TextStyle(
                                            color = CHelperTheme.colors.textSecondary,
                                            fontSize = 11.sp,
                                        ),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }

                // 备注
                library.note
                    .takeIf { it.isNotBlank() }
                    ?.let { note ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = note,
                            modifier = Modifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = CHelperTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                ),
                            maxLines = 2,
                        )
                    }
            }

            Image(
                painter =
                    painterResource(
                        if (library.isFavorited == true) Res.drawable.bookmark_check
                        else Res.drawable.bookmark
                    ),
                contentDescription = if (library.isFavorited == true) "取消收藏" else "收藏",
                modifier =
                    Modifier.align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onFavorite)
                        .padding(8.dp)
                        .size(18.dp),
                colorFilter =
                    ColorFilter.tint(
                        if (library.isFavorited == true) CHelperTheme.colors.mainColor
                        else CHelperTheme.colors.textSecondary
                    ),
            )

            // 右侧箭头
            Icon(
                id = Res.drawable.arrow_right,
                contentDescription = "查看详情",
                modifier =
                    Modifier.align(Alignment.CenterVertically).padding(end = 12.dp).size(18.dp),
            )
        }
    }
}
