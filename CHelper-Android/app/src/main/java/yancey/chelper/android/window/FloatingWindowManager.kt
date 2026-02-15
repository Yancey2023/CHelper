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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedDispatcher
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import yancey.chelper.R
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.window.completion.view.CompletionView
import yancey.chelper.android.window.fws.view.FWSMainView
import yancey.chelper.android.window.fws.view.FWSView

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
/**
 * 悬浮窗管理
 */
class FloatingWindowManager(
    private val application: Application,
) {
    private var mainViewWindow: EasyWindow<*>? = null
    private var iconViewWindow: EasyWindow<*>? = null

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
            val fwsFloatingMainView = mainViewWindow!!.contentView as FWSMainView<*>?
            if (fwsFloatingMainView != null) {
                fwsFloatingMainView.onPause()
                fwsFloatingMainView.onDestroy()
            }
            mainViewWindow!!.recycle()
            mainViewWindow = null
        }
        if (iconViewWindow != null) {
            iconViewWindow!!.recycle()
            iconViewWindow = null
        }
    }
}