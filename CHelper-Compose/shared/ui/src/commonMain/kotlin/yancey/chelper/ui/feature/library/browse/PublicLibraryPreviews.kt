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

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.ui.designsystem.CHelperTheme

@Preview
@Composable
fun PublicLibraryListScreenLightThemePreview() = PublicLibraryListPreview(CHelperTheme.Theme.Light)

@Preview
@Composable
fun PublicLibraryListScreenDarkThemePreview() = PublicLibraryListPreview(CHelperTheme.Theme.Dark)

@Composable
private fun PublicLibraryListPreview(theme: CHelperTheme.Theme) {
    CHelperTheme(theme, null) {
        PublicLibraryContent(
            state =
                PublicLibraryContentState(
                    libraries =
                        (1..5).map {
                            LibraryEntry(
                                remoteId = it,
                                name = "示例命令库 $it",
                                note = "常用命令与实用工具",
                                likeCount = it * 10,
                                tags = listOf("实用工具"),
                            )
                        },
                    isLoading = false,
                    errorMessage = null,
                    isRecommendMode = true,
                    hasMore = false,
                ),
            onBookmarks = {},
            onSearch = {},
            onMode = {},
            onRefresh = {},
            onRetry = {},
            onFavorite = {},
            onOpen = {},
            onLoadMore = {},
        )
    }
}
