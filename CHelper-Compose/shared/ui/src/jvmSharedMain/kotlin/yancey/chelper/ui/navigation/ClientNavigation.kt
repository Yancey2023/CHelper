package yancey.chelper.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import yancey.chelper.ui.feature.library.LibraryMainScreen
import yancey.chelper.ui.feature.library.account.AccountScreen
import yancey.chelper.ui.feature.library.activity.ActivityCenterScreen
import yancey.chelper.ui.feature.library.browse.PublicLibraryListScreen
import yancey.chelper.ui.feature.library.browse.PublicLibraryShowScreen
import yancey.chelper.ui.feature.library.favorites.FavoriteLibraryListScreen
import yancey.chelper.ui.feature.library.local.LocalLibraryEditScreen
import yancey.chelper.ui.feature.library.local.LocalLibraryListScreen
import yancey.chelper.ui.feature.library.local.LocalLibraryShowScreen
import yancey.chelper.ui.feature.library.messages.MessageScreen
import yancey.chelper.ui.feature.library.profile.UserProfileScreen
import yancey.chelper.ui.feature.library.score.LeaderboardScreen
import yancey.chelper.ui.feature.library.search.LibrarySearchScreen
import yancey.chelper.ui.feature.library.upload.LibraryUploadScreen

/** Hosts supply native integrations; ordinary routes and feature screens are shared. */
class ClientScreenBindings(
    val home: @Composable (NavHostController) -> Unit,
    val completion: @Composable (NavHostController) -> Unit,
    val history: @Composable () -> Unit,
    val settings: @Composable () -> Unit,
    val old2new: @Composable () -> Unit,
    val imeGuide: @Composable () -> Unit,
    val enumeration: @Composable () -> Unit,
    val rawtext: @Composable () -> Unit,
    val about: @Composable (NavHostController) -> Unit,
    val asset: @Composable (String, String) -> Unit,
)

fun NavGraphBuilder.clientScreens(
    navController: NavHostController,
    bindings: ClientScreenBindings,
    isFloatingWindow: Boolean = false,
) {
    composable<HomeScreenKey> { bindings.home(navController) }
    composable<CompletionScreenKey> { bindings.completion(navController) }
    composable<HistoryScreenKey> { bindings.history() }
    if (!isFloatingWindow) composable<SettingsScreenKey> { bindings.settings() }
    composable<Old2NewScreenKey> { bindings.old2new() }
    composable<Old2NewIMEGuideScreenKey> { bindings.imeGuide() }
    composable<EnumerationScreenKey> { bindings.enumeration() }
    composable<LocalLibraryListScreenKey> { LocalLibraryListScreen(navController = navController) }
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
    composable<RawtextScreenKey> { bindings.rawtext() }
    composable<AboutScreenKey> { bindings.about(navController) }
    composable<ShowTextScreenKey> { navBackStackEntry ->
        val showText: ShowTextScreenKey = navBackStackEntry.toRoute()
        bindings.asset(showText.title, showText.assetPath)
    }
    composable<PublicLibraryListScreenKey> {
        PublicLibraryListScreen(navController = navController, isFloatingWindow = isFloatingWindow)
    }
    composable<LibraryMainScreenKey> {
        LibraryMainScreen(navController = navController, isFloatingWindow = isFloatingWindow)
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
    composable<AccountScreenKey> { AccountScreen(navController = navController) }
    composable<LibraryUploadScreenKey> { backStackEntry ->
        val customKey = backStackEntry.toRoute<LibraryUploadScreenKey>()
        LibraryUploadScreen(navController = navController, editLibraryId = customKey.editLibraryId)
    }
    composable<LeaderboardScreenKey> { LeaderboardScreen(navController) }
    composable<UserProfileScreenKey> { backStackEntry ->
        val customKey = backStackEntry.toRoute<UserProfileScreenKey>()
        UserProfileScreen(customKey.id, navController)
    }
    composable<MessageScreenKey> { MessageScreen() }
    composable<FavoriteLibraryListScreenKey> {
        FavoriteLibraryListScreen(navController = navController)
    }
    composable<ActivityCenterScreenKey> { backStackEntry ->
        val args = backStackEntry.toRoute<ActivityCenterScreenKey>()
        ActivityCenterScreen(navController = navController, initialSection = args.initialSection)
    }
}
