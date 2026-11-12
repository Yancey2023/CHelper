package yancey.chelper.ui.app

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.first
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.about.AboutScreen
import yancey.chelper.ui.feature.command.CompletionScreen
import yancey.chelper.ui.feature.command.history.HistoryScreen
import yancey.chelper.ui.feature.conversion.Old2NewScreen
import yancey.chelper.ui.feature.document.AssetTextScreen
import yancey.chelper.ui.feature.enumeration.EnumerationScreen
import yancey.chelper.ui.feature.home.HomeScreen
import yancey.chelper.ui.feature.rawtext.RawtextScreen
import yancey.chelper.ui.feature.settings.SettingsScreen
import yancey.chelper.ui.navigation.ClientScreenBindings
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.clientScreens
import yancey.chelper.ui.navigation.openCommandLabDeepLink
import yancey.chelper.ui.platform.AppBackDispatcher
import yancey.chelper.ui.platform.ClientToastOverlay
import yancey.chelper.ui.platform.LocalAppBackAction
import yancey.chelper.ui.platform.LocalAppBackDispatcher
import yancey.chelper.ui.platform.LocalClientServices
import yancey.chelper.ui.platform.LocalWideLayout

/** Original app navigation, pages, ViewModels and stores; only native integrations vary by host. */
@Composable
fun CHelperApp(
    dependencies: AppDependencies,
    background: ImageBitmap? = null,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    onClose: () -> Unit,
    openRequest: Pair<Long, CommandLabDeepLink>? = null,
    initialSettings: Settings = Settings(),
) {
    val services = dependencies.services
    val preferences by
        services.settings
            .settings()
            .collectAsStateWithLifecycle(initialValue = initialSettings.withDefaults())
    val dark =
        when (preferences.themeId) {
            "MODE_NIGHT_YES" -> true
            "MODE_NIGHT_NO" -> false
            else -> isSystemInDarkTheme()
        }
    val nav = rememberNavController()
    LaunchedEffect(openRequest) {
        openRequest?.let {
            nav.currentBackStackEntryFlow.first()
            services.session.flush()
            nav.openCommandLabDeepLink(it.second)
        }
    }
    val destination by nav.currentBackStackEntryAsState()
    val back = remember { AppBackDispatcher() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(destination) { focus.requestFocus() }
    CHelperTheme(if (dark) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light, background) {
        BoxWithConstraints(
            Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                    back.back { nav.popBackStack() }
                    true
                } else false
            }
        ) {
            CompositionLocalProvider(
                LocalClientServices provides services,
                LocalAppBackAction provides { nav.popBackStack() },
                LocalAppBackDispatcher provides back,
                LocalWideLayout provides (maxWidth > maxHeight),
            ) {
                NavHost(
                    nav,
                    HomeScreenKey,
                    enterTransition = { slideInHorizontally { it } },
                    exitTransition = { slideOutHorizontally { -it } },
                    popEnterTransition = { slideInHorizontally { -it } },
                    popExitTransition = { slideOutHorizontally { it } },
                ) {
                    clientScreens(
                        nav,
                        ClientScreenBindings(
                            home = { HomeScreen(navController = it) },
                            completion = {
                                CompletionScreen(navController = it, shutdown = onClose)
                            },
                            history = { HistoryScreen() },
                            settings = { SettingsScreen(chooseBackground, restoreBackground) },
                            old2new = { Old2NewScreen(old2new = dependencies.convert) },
                            imeGuide = {},
                            enumeration = { EnumerationScreen() },
                            rawtext = { RawtextScreen() },
                            about = { AboutScreen(it) },
                            asset = { title, path -> AssetTextScreen(title, path) },
                        ),
                    )
                }
                ClientToastOverlay()
            }
        }
    }
}
