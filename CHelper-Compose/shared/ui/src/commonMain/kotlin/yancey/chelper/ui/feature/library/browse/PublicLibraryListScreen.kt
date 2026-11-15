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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map
import yancey.chelper.data.library.document.toLibraryEntry
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.ui.navigation.FavoriteLibraryListScreenKey
import yancey.chelper.ui.navigation.LibrarySearchScreenKey
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.LocalClientServices as LocalContext
import yancey.chelper.ui.platform.LocalClientServices

@Composable
fun PublicLibraryListScreen(
    network: NetworkServices = LocalClientServices.current.network,
    viewModel: PublicLibraryListViewModel = viewModel {
        PublicLibraryListViewModel(network.publicLibraries)
    },
    navController: NavHostController = rememberNavController(),
    isFloatingWindow: Boolean = false,
    isTab: Boolean = false,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsDataStore = remember(context) { context.settings }
    val tagClickBehavior =
        settingsDataStore.tagClickBehavior().collectAsStateWithLifecycle(initialValue = "search")
    val isPublicLibraryHomeRecommend by
        remember(settingsDataStore) {
                settingsDataStore.isPublicLibraryHomeRecommend().map<Boolean, Boolean?> { it }
            }
            .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            ClientMessages.show(it)
            viewModel.consumeActionMessage()
        }
    }
    LaunchedEffect(isPublicLibraryHomeRecommend) {
        isPublicLibraryHomeRecommend?.let { viewModel.initialize(it) }
    }
    LaunchedEffect(viewModel) { viewModel.checkUnreadMessages() }

    PublicLibraryContent(
        state =
            PublicLibraryContentState(
                state.libraries.map { it.toLibraryEntry() },
                state.isLoading,
                state.errorMessage,
                state.isRecommendMode,
                state.hasMore,
            ),
        isFloatingWindow = isFloatingWindow,
        onBack = if (isTab) ({ navController.popBackStack() }) else null,
        onBookmarks = { navController.navigate(FavoriteLibraryListScreenKey) },
        onSearch = { tag -> navController.navigate(LibrarySearchScreenKey(tag)) },
        onMode = { viewModel.switchMode(it) },
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onFavorite = { entry ->
            state.libraries.firstOrNull { it.id == entry.remoteId }?.let(viewModel::toggleFavorite)
        },
        onOpen = { entry ->
            entry.remoteId?.let { navController.navigate(PublicLibraryShowScreenKey(it)) }
        },
        onLoadMore = viewModel::loadMore,
        tagOpensSearch = tagClickBehavior.value == "search",
    )
}

@Composable
private fun PublicLibraryItem(
    modifier: Modifier = Modifier,
    library: LibraryFunction,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onFavorite: () -> Unit = {},
) {
    PublicLibraryCard(modifier, library.toLibraryEntry(), onClick, onTagClick, onFavorite)
}
