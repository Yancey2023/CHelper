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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.core.OldCommandConverter
import yancey.chelper.ui.about.AboutScreen
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.completion.CompletionScreen
import yancey.chelper.ui.completion.HistoryScreen
import yancey.chelper.ui.enumeration.EnumerationScreen
import yancey.chelper.ui.home.HomeScreen
import yancey.chelper.ui.library.CPLUploadScreen
import yancey.chelper.ui.library.CPLUserScreen
import yancey.chelper.ui.library.FavoriteLibraryListScreen
import yancey.chelper.ui.library.LibraryMainScreen
import yancey.chelper.ui.library.LocalLibraryEditScreen
import yancey.chelper.ui.library.LocalLibraryListScreen
import yancey.chelper.ui.library.LocalLibraryShowScreen
import yancey.chelper.ui.library.MessageScreen
import yancey.chelper.ui.library.PublicLibraryListScreen
import yancey.chelper.ui.library.PublicLibraryShowScreen
import yancey.chelper.ui.library.activity.ActivityCenterScreen
import yancey.chelper.ui.library.profile.UserProfileScreen
import yancey.chelper.ui.library.score.LeaderboardScreen
import yancey.chelper.ui.library.search.LibrarySearchScreen
import yancey.chelper.ui.old2new.Old2NewIMEGuideScreen
import yancey.chelper.ui.old2new.Old2NewScreen
import yancey.chelper.ui.rawtext.RawtextScreen
import yancey.chelper.ui.settings.SettingsScreen
import yancey.chelper.ui.showtext.AssetTextScreen

@Serializable object HomeScreenKey

@Serializable object CompletionScreenKey

@Serializable object HistoryScreenKey

@Serializable object SettingsScreenKey

@Serializable object Old2NewScreenKey

@Serializable object Old2NewIMEGuideScreenKey

@Serializable object EnumerationScreenKey

@Serializable object LocalLibraryListScreenKey

@Serializable
data class LocalLibraryShowScreenKey(
    val localEntryId: String? = null,
    val id: Int? = null,
)

@Serializable
data class LibraryEditScreenKey(
    val localEntryId: String? = null,
    val id: Int? = null,
)

@Serializable object RawtextScreenKey

@Serializable object AboutScreenKey

@Serializable object PublicLibraryListScreenKey

@Serializable object LibraryMainScreenKey

@Serializable
data class PublicLibraryShowScreenKey(
    val id: Int,
    val isPrivate: Boolean = false,
    val importToLocal: Boolean = false,
)

@Serializable
data class ShowTextScreenKey(
    val title: String,
    val assetPath: String,
)

@Serializable data class LibrarySearchScreenKey(val initialKeyword: String? = null)

@Serializable object CPLUserScreenKey

@Serializable data class CPLUploadScreenKey(val editLibraryId: Int = -1)

@Serializable object LeaderboardScreenKey

@Serializable data class UserProfileScreenKey(val id: Int)

@Serializable object MessageScreenKey

@Serializable object FavoriteLibraryListScreenKey

@Serializable data class ActivityCenterScreenKey(val initialSection: Int = 0)

private data class NavigationEnvironment(
    val isFloatingWindow: Boolean = false,
    val isWindowVisible: Boolean = true,
    val floatingWindowManager: FloatingWindowManager? = null,
    val shutdown: () -> Unit,
    val hideView: () -> Unit = {},
    val chooseBackground: () -> Unit = {},
    val restoreBackground: () -> Unit = {},
)

@Composable
fun NavHost(
    navController: NavHostController,
    floatingWindowManager: FloatingWindowManager,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    isShowSavingBackgroundDialog: MutableState<Boolean> = mutableStateOf(false),
    shutdown: () -> Unit,
) {
    CHelperNavigation(
        navController,
        HomeScreenKey,
        NavigationEnvironment(
            floatingWindowManager = floatingWindowManager,
            shutdown = shutdown,
            chooseBackground = chooseBackground,
            restoreBackground = restoreBackground,
        ),
    )
    if (isShowSavingBackgroundDialog.value) {
        IsConfirmDialog(
            onDismissRequest = { isShowSavingBackgroundDialog.value = false },
            content = "背景图片正在保存中，请稍候",
        )
    }
}

@Composable
fun FloatingWindowNavHost(
    navController: NavHostController,
    shutdown: () -> Unit,
    hideView: () -> Unit,
    isWindowVisible: Boolean,
) {
    CHelperNavigation(
        navController,
        CompletionScreenKey,
        NavigationEnvironment(
            isFloatingWindow = true,
            isWindowVisible = isWindowVisible,
            shutdown = shutdown,
            hideView = hideView,
        ),
    )
}

@Composable
private fun CHelperNavigation(
    navController: NavHostController,
    start: Any,
    environment: NavigationEnvironment,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    DisposableEffect(navController, focus, keyboard) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            focus.clearFocus()
            keyboard?.hide()
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) },
    ) {
        chelperScreens(navController, environment)
    }
}

private fun NavGraphBuilder.chelperScreens(
    navController: NavHostController,
    environment: NavigationEnvironment,
) {
    composable<HomeScreenKey> {
        HomeScreen(
            navController = navController,
            floatingWindowManager = environment.floatingWindowManager,
        )
    }
    composable<CompletionScreenKey> {
        CompletionScreen(
            viewModel = viewModel(),
            navController = navController,
            shutdown = environment.shutdown,
            hideView = environment.hideView,
            isScreenVisible = environment.isWindowVisible,
        )
    }
    composable<HistoryScreenKey> {
        HistoryScreen()
    }
    if (!environment.isFloatingWindow) {
        composable<SettingsScreenKey> {
            SettingsScreen(
                chooseBackground = environment.chooseBackground,
                restoreBackground = environment.restoreBackground,
            )
        }
    }
    composable<Old2NewScreenKey> {
        val context = LocalContext.current
        val converter = remember(context) { OldCommandConverter(context) }
        Old2NewScreen(old2new = converter::convert)
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
    composable<LocalLibraryShowScreenKey> { navBackStackEntry ->
        val localLibraryShow: LocalLibraryShowScreenKey = navBackStackEntry.toRoute()
        LocalLibraryShowScreen(
            localEntryId = localLibraryShow.localEntryId,
            id = localLibraryShow.id,
            navController = navController,
        )
    }
    composable<LibraryEditScreenKey> { navBackStackEntry ->
        val localLibraryEdit: LibraryEditScreenKey = navBackStackEntry.toRoute()
        LocalLibraryEditScreen(
            localEntryId = localLibraryEdit.localEntryId,
            id = localLibraryEdit.id,
        )
    }
    composable<RawtextScreenKey> {
        RawtextScreen()
    }
    composable<AboutScreenKey> {
        AboutScreen(navController)
    }
    composable<ShowTextScreenKey> { navBackStackEntry ->
        val showText: ShowTextScreenKey = navBackStackEntry.toRoute()
        AssetTextScreen(title = showText.title, assetPath = showText.assetPath)
    }
    composable<PublicLibraryListScreenKey> {
        PublicLibraryListScreen(
            navController = navController,
            isFloatingWindow = environment.isFloatingWindow,
        )
    }
    composable<LibraryMainScreenKey> {
        LibraryMainScreen(
            navController = navController,
            isFloatingWindow = environment.isFloatingWindow,
        )
    }
    composable<PublicLibraryShowScreenKey> { navBackStackEntry ->
        val publicLibraryShow: PublicLibraryShowScreenKey = navBackStackEntry.toRoute()
        PublicLibraryShowScreen(
            id = publicLibraryShow.id,
            isPrivate = publicLibraryShow.isPrivate,
            navController = navController,
            importToLocal = publicLibraryShow.importToLocal,
        )
    }
    composable<LibrarySearchScreenKey> { navBackStackEntry ->
        val args: LibrarySearchScreenKey = navBackStackEntry.toRoute()
        LibrarySearchScreen(navController = navController, initialKeyword = args.initialKeyword)
    }
    composable<CPLUserScreenKey> {
        CPLUserScreen(navController = navController)
    }
    composable<CPLUploadScreenKey> { backStackEntry ->
        val customKey = backStackEntry.toRoute<CPLUploadScreenKey>()
        CPLUploadScreen(
            navController = navController,
            editLibraryId = customKey.editLibraryId,
        )
    }
    composable<LeaderboardScreenKey> {
        LeaderboardScreen(navController)
    }
    composable<UserProfileScreenKey> { backStackEntry ->
        val customKey = backStackEntry.toRoute<UserProfileScreenKey>()
        UserProfileScreen(customKey.id, navController)
    }
    composable<MessageScreenKey> {
        MessageScreen()
    }
    composable<FavoriteLibraryListScreenKey> {
        FavoriteLibraryListScreen(navController = navController)
    }
    composable<ActivityCenterScreenKey> { backStackEntry ->
        val args = backStackEntry.toRoute<ActivityCenterScreenKey>()
        ActivityCenterScreen(
            navController = navController,
            initialSection = args.initialSection,
        )
    }
}
