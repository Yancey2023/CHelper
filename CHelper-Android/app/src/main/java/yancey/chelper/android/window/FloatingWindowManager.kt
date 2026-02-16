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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import yancey.chelper.R
import yancey.chelper.android.common.util.CustomTheme
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.window.completion.view.CompletionView
import yancey.chelper.android.window.fws.view.FWSMainView
import yancey.chelper.android.window.fws.view.FWSView
import yancey.chelper.android.window.util.ComposeLifecycleOwner
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
    private var isCompose = false

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
    fun startFloatingWindow(context: Context) {
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            Settings.INSTANCE.floatingWindowSize.toFloat(),
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
        if (isCompose) {
            val mainView = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                    if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        floatBackPressedOwner?.onBackPressedDispatcher?.onBackPressed()
                        requestFocus()
                        return true
                    } else {
                        return false
                    }
                }
            }.apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            val composeView = ComposeView(context).apply {
                setContent {
                    CHelperTheme(
                        when (Settings.INSTANCE.themeId) {
                            "MODE_NIGHT_NO" -> CHelperTheme.Theme.Light
                            "MODE_NIGHT_YES" -> CHelperTheme.Theme.Dark
                            else -> if ((application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) CHelperTheme.Theme.Dark else CHelperTheme.Theme.Light
                        }, CustomTheme.INSTANCE.backgroundBitmap.imageBitmap
                    ) {
                        val lifecycleOwner = rememberLifecycleOwner()
                        val navigationEventDispatcher = remember { NavigationEventDispatcher() }
                        val navigationEventOwner =
                            remember { FloatWindowNavigationEventOwner(navigationEventDispatcher) }
                        val navController = rememberNavController()
                        LaunchedEffect(navController) {
                            navController.setLifecycleOwner(lifecycleOwner)
                            navController.setOnBackPressedDispatcher(floatBackPressedOwner!!.onBackPressedDispatcher)
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
                .setWindowAlpha(Settings.INSTANCE.floatingWindowAlpha)
            mainViewWindow = EasyWindow.with(application)
                .setContentView(mainView)
                .setWindowSize(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                .removeWindowFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                .setSystemUiVisibility(
                    (mainView.systemUiVisibility
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                )
                .setWindowAnim(0)
                .setWindowAlpha(Settings.INSTANCE.floatingWindowAlpha)
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
        } else {
            val fwsMainView = FWSMainView<CompletionView?>(
                context,
                FWSView.Environment.FLOATING_WINDOW,
                { customContext: FWSView.FWSContext? ->
                    CompletionView(
                        customContext!!,
                        { this.stopFloatingWindow() },
                        { iconView.callOnClick() })
                },
                OnBackPressedDispatcher { iconView.callOnClick() }
            )
            iconViewWindow = EasyWindow.with(application)
                .setContentView(iconView)
                .setWindowDraggableRule(MovingWindowDraggableRule())
                .setOutsideTouchable(true)
                .setWindowLocation(Gravity.START or Gravity.TOP, 0, 0)
                .setWindowAnim(0)
                .setWindowAlpha(Settings.INSTANCE.floatingWindowAlpha)
            mainViewWindow = EasyWindow.with(application)
                .setContentView(fwsMainView)
                .setWindowSize(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                .removeWindowFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                .setSystemUiVisibility(
                    (fwsMainView.systemUiVisibility
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                )
                .setWindowAnim(0)
                .setWindowAlpha(Settings.INSTANCE.floatingWindowAlpha)
            iconView.setOnClickListener {
                mainViewWindow?.apply {
                    if (windowViewVisibility == View.VISIBLE) {
                        fwsMainView.onPause()
                        windowViewVisibility = View.INVISIBLE
                    } else {
                        windowViewVisibility = View.VISIBLE
                        fwsMainView.onResume()
                    }
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
            if (isCompose) {
                composeLifecycleOwner?.apply {
                    onStop()
                    onDestroy()
                    detachFromDecorView(mainViewWindow!!.contentView)
                }
                composeLifecycleOwner = null
                floatBackPressedOwner = null
                mainViewWindow!!.recycle()
                mainViewWindow = null
            } else {
                val fwsFloatingMainView = mainViewWindow!!.contentView as FWSMainView<*>?
                if (fwsFloatingMainView != null) {
                    fwsFloatingMainView.onPause()
                    fwsFloatingMainView.onDestroy()
                }
                mainViewWindow!!.recycle()
                mainViewWindow = null
            }
        }
        if (iconViewWindow != null) {
            iconViewWindow!!.recycle()
            iconViewWindow = null
        }
    }
}