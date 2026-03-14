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

package yancey.chelper.android.window

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import kotlinx.coroutines.launch
import yancey.chelper.R
import yancey.chelper.data.BackgroundStore
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.FloatingWindowNavHost
import yancey.chelper.ui.common.CHelperTheme

class FloatWindowBackPressedOwner(override val lifecycle: Lifecycle) :
    OnBackPressedDispatcherOwner {
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
}

class FloatWindowNavigationEventOwner(override val navigationEventDispatcher: NavigationEventDispatcher) :
    NavigationEventDispatcherOwner

/**
 * 悬浮窗管理
 */
class FloatingWindowManager(
    private val application: Application,
) {
    private var mainViewWindow: EasyWindow<*>? = null
    private var iconViewWindow: EasyWindow<*>? = null
    private var composeLifecycleOwner: ComposeLifecycleOwner? = null
    private var floatBackPressedOwner: FloatWindowBackPressedOwner? = null
    private var navController: NavController? = null

    val isUsingFloatingWindow: Boolean
        /**
         * 是否正在使用悬浮窗
         *
         * @return 是否正在使用悬浮窗
         */
        get() = iconViewWindow != null

    /**
     * 开启悬浮窗
     *
     * @param context 上下文
     */
    @Suppress("deprecation")
    fun startFloatingWindow(
        context: Context,
        floatingWindowSize: Int,
        floatingWindowAlpha: Float
    ) {
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            floatingWindowSize.toFloat(),
            application.resources.displayMetrics
        ).toInt()
        val iconView = ImageView(context)
        iconView.setImageResource(R.drawable.pack_icon)
        iconView.setLayoutParams(
            FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.START or Gravity.TOP
            )
        )
        val mainView = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (navController != null && navController?.currentBackStackEntry != null && navController?.previousBackStackEntry == null) {
                        iconView.callOnClick()
                    } else {
                        floatBackPressedOwner?.onBackPressedDispatcher?.onBackPressed()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val composeView = ComposeView(context).apply {
            val settingsDataStore = SettingsDataStore(context)
            var themeId = "MODE_NIGHT_FOLLOW_SYSTEM"
            composeLifecycleOwner?.lifecycleScope?.launch {
                settingsDataStore.themeId().collect {
                    themeId = it
                }
            }
            setContent {
                val backgroundBitmap =
                    BackgroundStore.INSTANCE?.backgroundBitmapFlow?.collectAsState(initial = null)
                CHelperTheme(
                    when (themeId) {
                        "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
                        "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
                        else -> if ((application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
                    }, backgroundBitmap?.value
                ) {
                    val lifecycleOwner = rememberLifecycleOwner()
                    val navigationEventDispatcher = remember { NavigationEventDispatcher() }
                    val navigationEventOwner =
                        remember { FloatWindowNavigationEventOwner(navigationEventDispatcher) }
                    val navController = rememberNavController()
                    val softwareKeyboardController = LocalSoftwareKeyboardController.current
                    LaunchedEffect(navController) {
                        this@FloatingWindowManager.navController = navController
                        navController.setLifecycleOwner(lifecycleOwner)
                        navController.setOnBackPressedDispatcher(floatBackPressedOwner!!.onBackPressedDispatcher)
                        navController.addOnDestinationChangedListener { _, _, _ ->
                            mainView.clearFocus()
                            mainView.requestFocus()
                            softwareKeyboardController?.hide()
                        }
                    }
                    CompositionLocalProvider(
                        LocalOnBackPressedDispatcherOwner provides floatBackPressedOwner!!,
                        LocalNavigationEventDispatcherOwner provides navigationEventOwner,
                    ) {
                        FloatingWindowNavHost(
                            navController = navController,
                            shutdown = { stopFloatingWindow() },
                            hideView = { iconView.callOnClick() },
                        )
                    }
                }
            }
        }
        mainView.addView(composeView)
        iconViewWindow = EasyWindow.with(application)
            .setContentView(iconView)
            .setWindowDraggableRule(MovingWindowDraggableRule())
            .setOutsideTouchable(true)
            .setWindowLocation(Gravity.START or Gravity.TOP, 0, 0)
            .setWindowAnim(0)
            .setWindowAlpha(floatingWindowAlpha)
        mainViewWindow = EasyWindow.with(application)
            .setContentView(mainView)
            .setWindowSize(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            .removeWindowFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            .setSystemUiVisibility(
                (mainView.systemUiVisibility
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            )
            .setWindowAnim(0)
            .setWindowAlpha(floatingWindowAlpha)
        composeLifecycleOwner = ComposeLifecycleOwner().apply {
            attachToDecorView(mainViewWindow!!.rootLayout)
            onCreate()
            onStart()
        }
        floatBackPressedOwner = FloatWindowBackPressedOwner(composeLifecycleOwner!!.lifecycle)
        mainView.requestFocus()
        iconView.setOnClickListener {
            mainViewWindow?.apply {
                if (windowViewVisibility == View.VISIBLE) {
                    composeLifecycleOwner?.onPause()
                    mainView.clearFocus()
                    windowViewVisibility = View.INVISIBLE
                } else {
                    composeLifecycleOwner?.onResume()
                    mainView.requestFocus()
                    windowViewVisibility = View.VISIBLE
                }
            }
        }
        if (mainViewWindow != null && iconViewWindow != null) {
            mainViewWindow!!.windowViewVisibility = View.INVISIBLE
            mainViewWindow!!.show()
            iconViewWindow!!.show()
        } else {
            stopFloatingWindow()
        }
    }

    /**
     * 关闭悬浮窗
     */
    fun stopFloatingWindow() {
        if (mainViewWindow != null) {
            composeLifecycleOwner?.apply {
                onStop()
                onDestroy()
                detachFromDecorView(mainViewWindow!!.contentView)
            }
            composeLifecycleOwner = null
            floatBackPressedOwner = null
            navController = null
            mainViewWindow!!.recycle()
            mainViewWindow = null
        }
        if (iconViewWindow != null) {
            iconViewWindow!!.recycle()
            iconViewWindow = null
        }
    }
}
