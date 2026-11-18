/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
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

package yancey.chelper.android.activity

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent as setComposeContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import yancey.chelper.BuildConfig
import yancey.chelper.android.performance.WindowFrameRecorder
import yancey.chelper.android.performance.WindowPerformanceState
import yancey.chelper.android.startupState
import yancey.chelper.android.storage.BackgroundStore
import yancey.chelper.android.storage.clientDataStores
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.platform.LocalPerformanceState

abstract class BaseComposeActivity : ComponentActivity() {

    protected val settingsDataStore by lazy { applicationContext.clientDataStores().settings }
    protected var theme by mutableStateOf(CHelperTheme.Theme.Light)
    private var startupReady by mutableStateOf(false)
    private var frameRecorder: WindowFrameRecorder? = null
    private val performanceState by lazy { WindowPerformanceState(window.decorView) }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.PERFORMANCE_COLLECTION) {
            frameRecorder = WindowFrameRecorder(this, window, javaClass.simpleName)
            performanceState.refresh()
        }
    }

    override fun onStop() {
        frameRecorder?.close()
        frameRecorder = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the launch window until asynchronous settings/background loading finishes.
        val firstFrame =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!startupReady) return false
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        window.decorView.viewTreeObserver.addOnPreDrawListener(firstFrame)
        lifecycleScope.launch {
            startupState.settings.collect { settings ->
                if (settings != null) {
                    applyTheme(settings.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM")
                    startupReady = true
                }
            }
        }
    }

    private fun isSystemDarkMode(): Boolean =
        (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun applyTheme(themeId: String) {
        val isDarkBefore =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        when (themeId) {
            "MODE_NIGHT_NO" ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "MODE_NIGHT_YES" ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        theme = CHelperTheme.themeOf(themeId, isSystemDarkMode())
        val isDarkMode = theme == CHelperTheme.Theme.Dark
        // The first theme arrives asynchronously; always apply both bar styles from that theme.
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkMode },
            navigationBarStyle =
                SystemBarStyle.auto(
                    Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                    Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                ) {
                    isDarkMode
                },
        )
        if (isDarkBefore != isDarkMode) {
            resources.configuration.uiMode =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (isDarkMode) Configuration.UI_MODE_NIGHT_YES
                    else Configuration.UI_MODE_NIGHT_NO
        }
    }

    protected fun setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
        setComposeContent(parent) {
            if (BuildConfig.PERFORMANCE_AUTOMATION || BuildConfig.PERFORMANCE_COLLECTION) {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    if (BuildConfig.PERFORMANCE_COLLECTION) {
                        CompositionLocalProvider(LocalPerformanceState provides performanceState) {
                            ThemedContent(content)
                        }
                    } else {
                        ThemedContent(content)
                    }
                }
            } else ThemedContent(content)
        }
    }

    @Composable
    private fun ThemedContent(content: @Composable () -> Unit) {
        ReportDrawnWhen { startupReady }
        if (!startupReady) return
        val background by
            BackgroundStore.INSTANCE.backgroundBitmapFlow.collectAsStateWithLifecycle(
                initialValue = BackgroundStore.INSTANCE.backgroundBitmap
            )
        CHelperTheme(theme, background) { content() }
    }
}
