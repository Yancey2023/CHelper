package yancey.chelper.ui.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppAlertDialog
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.dialog.AppDialogProperties
import yancey.chelper.ui.designsystem.dialog.DialogContainer
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.feature.about.AboutScreen
import yancey.chelper.ui.feature.command.CompletionScreen
import yancey.chelper.ui.feature.command.history.HistoryScreen
import yancey.chelper.ui.feature.conversion.Old2NewScreen
import yancey.chelper.ui.feature.document.AssetTextScreen
import yancey.chelper.ui.feature.enumeration.EnumerationScreen
import yancey.chelper.ui.feature.home.HomeScreen
import yancey.chelper.ui.feature.rawtext.RawtextScreen
import yancey.chelper.ui.feature.settings.SettingsScreen
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.ClientScreenBindings
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.clientScreens
import yancey.chelper.ui.navigation.openCommandLabDeepLink
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.LocalAppBackAction
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
    closing: Boolean = false,
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
    var openFailure by remember { mutableStateOf<String?>(null) }
    var openRetry by remember { mutableIntStateOf(0) }
    var completedOpen by remember { mutableStateOf<Long?>(null) }
    var opening by remember { mutableStateOf(false) }
    fun cancelOpen() {
        completedOpen = openRequest?.first
        openFailure = null
    }
    LaunchedEffect(openRequest, openRetry, closing) {
        openFailure = null
        if (!closing)
            openRequest
                ?.takeUnless { it.first == completedOpen }
                ?.let {
                    opening = true
                    try {
                        nav.currentBackStackEntryFlow.first()
                        services.session.flush()
                        nav.openCommandLabDeepLink(it.second)
                        completedOpen = it.first
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        openFailure = failure.message ?: "保存当前内容失败"
                    } finally {
                        opening = false
                    }
                }
    }
    CHelperTheme(if (dark) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light, background) {
        ClientInteractionHost {
            if (closing || opening) {
                AppDialog(
                    onDismissRequest = {},
                    properties =
                        AppDialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                ) {
                    DialogContainer {
                        Box(Modifier.padding(24.dp)) {
                            Text(if (closing) "正在保存并退出…" else "正在保存并打开链接…")
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalClientServices provides services,
                    LocalAppBackAction provides { nav.popBackStack() },
                    LocalWideLayout provides (maxWidth > maxHeight),
                ) {
                    ClientNavHost(
                        nav,
                        HomeScreenKey,
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
                }
            }
            openFailure?.let { message ->
                AppAlertDialog(
                    onDismissRequest = ::cancelOpen,
                    title = { Text("暂时无法打开链接") },
                    text = { Text("当前内容保存失败，已保留当前页面。\n$message") },
                    confirmButton = { TextButton(onClick = { openRetry++ }) { Text("重试") } },
                    dismissButton = { TextButton(onClick = ::cancelOpen) { Text("取消") } },
                )
            }
        }
    }
}
