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

package yancey.chelper.android.common.activity

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import yancey.chelper.android.common.style.CustomTheme
import yancey.chelper.android.common.util.Settings
import yancey.chelper.ui.common.CHelperTheme

abstract class BaseComposeActivity : ComponentActivity() {

    private var backgroundUpdateTimes = 0
    protected var backgroundBitmap by mutableStateOf<ImageBitmap?>(null)
    protected var theme by mutableStateOf(CHelperTheme.Theme.Light)
    protected var isSystemDarkMode = false

    abstract val pageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        isSystemDarkMode =
            (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        refreshTheme()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                Color.argb(0x80, 0x1b, 0x1b, 0x1b)
            ) {
                theme == CHelperTheme.Theme.Dark
            }
        )
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val updateTimes = CustomTheme.INSTANCE.backgroundBitmap.updateTimes
        if (backgroundUpdateTimes != updateTimes) {
            backgroundUpdateTimes = updateTimes
            backgroundBitmap = CustomTheme.INSTANCE.backgroundBitmap.imageBitmap
        }
        refreshTheme()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshTheme()
    }

    protected fun refreshTheme() {
        val isDarkBefore =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        theme = when (Settings.INSTANCE.themeId) {
            "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
            "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
            else -> if (isSystemDarkMode) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
        }
        val isDarkMode = theme == CHelperTheme.Theme.Dark
        if (isDarkBefore != isDarkMode) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkMode
                isAppearanceLightNavigationBars = !isDarkMode
            }
            resources.configuration.uiMode =
                if (isDarkMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }

    protected fun setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
        val existingComposeView =
            window.decorView.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as? ComposeView

        if (existingComposeView != null) {
            with(existingComposeView) {
                setParentCompositionContext(parent)
                setContent(content)
            }
        } else {
            ComposeView(this).apply {
                setParentCompositionContext(parent)
                setContent {
                    CHelperTheme(theme, backgroundBitmap) {
                        content()
                    }
                }
                setOwners()
                setContentView(this, DefaultActivityContentLayoutParams)
            }
        }
    }

}

private val DefaultActivityContentLayoutParams =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

private fun ComponentActivity.setOwners() {
    val decorView = window.decorView
    if (decorView.findViewTreeLifecycleOwner() == null) {
        decorView.setViewTreeLifecycleOwner(this)
    }
    if (decorView.findViewTreeViewModelStoreOwner() == null) {
        decorView.setViewTreeViewModelStoreOwner(this)
    }
    if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }
}