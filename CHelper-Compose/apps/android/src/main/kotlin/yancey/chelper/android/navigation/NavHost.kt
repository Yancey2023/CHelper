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

package yancey.chelper.android.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import yancey.chelper.android.command.OldCommandConverter
import yancey.chelper.android.di.rememberAndroidClientServices
import yancey.chelper.android.ui.ime.Old2NewIMEGuideScreen
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
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
import yancey.chelper.ui.navigation.CompletionScreenKey
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.clientScreens
import yancey.chelper.ui.platform.AppBackHandler
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.LocalAppBackAction
import yancey.chelper.ui.platform.LocalClientServices
import yancey.chelper.ui.platform.LocalWideLayout

private data class NavigationEnvironment(
    val isFloatingWindow: Boolean = false,
    val isWindowVisible: Boolean = true,
    val floatingWindowManager: FloatingWindowManager? = null,
    val shutdown: () -> Unit,
    val hideView: () -> Unit = {},
    val chooseBackground: () -> Unit = {},
    val restoreBackground: () -> Unit = {},
    val isSavingBackground: Boolean = false,
    val showSavingBackground: () -> Unit = {},
    val savingDialog: @Composable () -> Unit = {},
)

@Composable
fun NavHost(
    navController: NavHostController,
    floatingWindowManager: FloatingWindowManager,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    isShowSavingBackgroundDialog: MutableState<Boolean> = mutableStateOf(false),
    isSavingBackground: Boolean = false,
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
            isSavingBackground = isSavingBackground,
            showSavingBackground = { isShowSavingBackgroundDialog.value = true },
            savingDialog = {
                if (isShowSavingBackgroundDialog.value) {
                    IsConfirmDialog(
                        onDismissRequest = { isShowSavingBackgroundDialog.value = false },
                        content = "背景图片正在保存中，请稍候",
                    )
                }
            },
        ),
    )
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
    ClientInteractionHost(
        onBack = if (environment.isFloatingWindow) environment.hideView else null
    ) {
        BoxWithConstraints {
            CompositionLocalProvider(
                LocalClientServices provides
                    rememberAndroidClientServices(environment.floatingWindowManager),
                LocalAppBackAction provides { navController.popBackStack() },
                LocalWideLayout provides (maxWidth > maxHeight),
            ) {
                ClientNavHost(
                    navController = navController,
                    startDestination = start,
                ) {
                    clientScreens(
                        navController,
                        ClientScreenBindings(
                            home = { HomeScreen(navController = it) },
                            completion = {
                                CompletionScreen(
                                    navController = it,
                                    shutdown = environment.shutdown,
                                    hideView = environment.hideView,
                                    isScreenVisible = environment.isWindowVisible,
                                )
                            },
                            history = { HistoryScreen() },
                            settings = {
                                SettingsScreen(
                                    environment.chooseBackground,
                                    environment.restoreBackground,
                                )
                            },
                            old2new = {
                                val context = LocalContext.current
                                val converter = remember(context) { OldCommandConverter(context) }
                                Old2NewScreen(old2new = converter::convert)
                            },
                            imeGuide = { Old2NewIMEGuideScreen() },
                            enumeration = { EnumerationScreen() },
                            rawtext = { RawtextScreen() },
                            about = { AboutScreen(it) },
                            asset = { title, path ->
                                AssetTextScreen(title = title, assetPath = path)
                            },
                        ),
                        environment.isFloatingWindow,
                    )
                }
                AppBackHandler(
                    enabled = environment.isSavingBackground,
                    onBack = environment.showSavingBackground,
                )
                environment.savingDialog()
            }
        }
    }
}
