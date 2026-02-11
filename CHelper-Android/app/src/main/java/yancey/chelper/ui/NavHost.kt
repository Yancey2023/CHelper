/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import yancey.chelper.android.library.util.LocalLibraryManager
import yancey.chelper.core.CHelperCore
import yancey.chelper.ui.about.AboutScreen
import yancey.chelper.ui.completion.CompletionScreen
import yancey.chelper.ui.completion.HistoryScreen
import yancey.chelper.ui.enumeration.EnumerationScreen
import yancey.chelper.ui.home.HomeScreen
import yancey.chelper.ui.library.LocalLibraryEditScreen
import yancey.chelper.ui.library.LocalLibraryListScreen
import yancey.chelper.ui.library.LocalLibraryShowScreen
import yancey.chelper.ui.library.LocalLibraryShowViewModel
import yancey.chelper.ui.old2new.Old2NewIMEGuideScreen
import yancey.chelper.ui.old2new.Old2NewScreen
import yancey.chelper.ui.rawtext.RawtextScreen
import yancey.chelper.ui.settings.SettingsScreen
import yancey.chelper.ui.showtext.ShowTextScreen

@Serializable
object HomeScreenKey

@Serializable
object CompletionScreenKey

@Serializable
object HistoryScreenKey

@Serializable
object SettingsScreenKey

@Serializable
object Old2NewScreenKey

@Serializable
object Old2NewIMEGuideScreenKey

@Serializable
object EnumerationScreenKey

@Serializable
object LocalLibraryListScreenKey

@Serializable
data class LibraryShowScreenKey(
    val id: Int
)

@Serializable
data class LibraryEditScreenKey(
    val id: Int?
)

@Serializable
object RawtextScreenKey

@Serializable
object AboutScreenKey

@Serializable
data class ShowTextScreenKey(
    val title: String,
    val content: String
)

@Composable
fun NavHost(
    navController: NavHostController,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    onChooseTheme: () -> Unit,
    shutdown: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = HomeScreenKey,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        composable<HomeScreenKey> {
            HomeScreen(navController = navController)
        }
        composable<CompletionScreenKey> {
            CompletionScreen(
                viewModel = viewModel(),
                navController = navController,
                shutdown = shutdown,
                hideView = {}
            )
        }
        composable<HistoryScreenKey> {
            HistoryScreen(viewModel = viewModel())
        }
        composable<SettingsScreenKey> {
            SettingsScreen(
                chooseBackground = chooseBackground,
                restoreBackground = restoreBackground,
                onChooseTheme = onChooseTheme,
            )
        }
        composable<Old2NewScreenKey> {
            val context = LocalContext.current
            Old2NewScreen(
                old2new = { old -> CHelperCore.old2new(context, old) }
            )
        }
        composable<Old2NewIMEGuideScreenKey> {
            Old2NewIMEGuideScreen()
        }
        composable<EnumerationScreenKey> {
            EnumerationScreen()
        }
        composable<LocalLibraryListScreenKey> {
            LocalLibraryListScreen(navController = navController)
        }
        composable<LibraryShowScreenKey> { navBackStackEntry ->
            val localLibraryShow: LibraryShowScreenKey = navBackStackEntry.toRoute()
            val viewModel: LocalLibraryShowViewModel = viewModel()
            LaunchedEffect(viewModel, localLibraryShow.id) {
                viewModel.viewModelScope.launch {
                    LocalLibraryManager.INSTANCE!!.ensureInit()
                    viewModel.library =
                        LocalLibraryManager.INSTANCE!!.getFunctions()[localLibraryShow.id]
                }
            }
            LocalLibraryShowScreen(viewModel = viewModel)
        }
        composable<LibraryEditScreenKey> { navBackStackEntry ->
            val localLibraryEdit: LibraryEditScreenKey = navBackStackEntry.toRoute()
            LocalLibraryEditScreen(id = localLibraryEdit.id)
        }
        composable<RawtextScreenKey> {
            RawtextScreen()
        }
        composable<AboutScreenKey> {
            AboutScreen(navController)
        }
        composable<ShowTextScreenKey> { navBackStackEntry ->
            val showText: ShowTextScreenKey = navBackStackEntry.toRoute()
            ShowTextScreen(
                title = showText.title,
                content = showText.content
            )
        }
    }
}

@Composable
fun FloatingWindowNavHost(
    navController: NavHostController,
    shutdown: () -> Unit,
    hideView: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = CompletionScreenKey,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        composable<HomeScreenKey> {
            HomeScreen(navController = navController)
        }
        composable<CompletionScreenKey> {
            CompletionScreen(
                viewModel = viewModel(),
                navController = navController,
                shutdown = shutdown,
                hideView = hideView
            )
        }
        composable<HistoryScreenKey> {
            HistoryScreen(viewModel = viewModel())
        }
        composable<Old2NewScreenKey> {
            val context = LocalContext.current
            Old2NewScreen(
                old2new = { old -> CHelperCore.old2new(context, old) }
            )
        }
        composable<Old2NewIMEGuideScreenKey> {
            Old2NewIMEGuideScreen()
        }
        composable<EnumerationScreenKey> {
            EnumerationScreen()
        }
        composable<LocalLibraryListScreenKey> {
            LocalLibraryListScreen(navController = navController)
        }
        composable<LibraryShowScreenKey> { navBackStackEntry ->
            val localLibraryShow: LibraryShowScreenKey = navBackStackEntry.toRoute()
            val viewModel: LocalLibraryShowViewModel = viewModel()
            LaunchedEffect(viewModel, localLibraryShow.id) {
                viewModel.viewModelScope.launch {
                    LocalLibraryManager.INSTANCE!!.ensureInit()
                    viewModel.library =
                        LocalLibraryManager.INSTANCE!!.getFunctions()[localLibraryShow.id]
                }
            }
            LocalLibraryShowScreen(viewModel = viewModel)
        }
        composable<LibraryEditScreenKey> { navBackStackEntry ->
            val localLibraryEdit: LibraryEditScreenKey = navBackStackEntry.toRoute()
            LocalLibraryEditScreen(id = localLibraryEdit.id)
        }
        composable<RawtextScreenKey> {
            RawtextScreen()
        }
        composable<AboutScreenKey> {
            AboutScreen(navController)
        }
        composable<ShowTextScreenKey> { navBackStackEntry ->
            val showText: ShowTextScreenKey = navBackStackEntry.toRoute()
            ShowTextScreen(
                title = showText.title,
                content = showText.content
            )
        }
    }
}
