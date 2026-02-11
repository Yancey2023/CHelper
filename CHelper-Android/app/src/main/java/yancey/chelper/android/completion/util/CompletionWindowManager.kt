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

package yancey.chelper.android.completion.util

import android.app.Application
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedDispatcher
import com.hjq.device.compat.DeviceOs
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.toast.Toaster
import com.hjq.window.EasyWindow
import com.hjq.window.draggable.MovingWindowDraggableRule
import yancey.chelper.R
import yancey.chelper.android.common.dialog.IsConfirmDialog
import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.completion.view.CompletionView
import yancey.chelper.fws.view.FWSMainView
import yancey.chelper.fws.view.FWSView
import yancey.chelper.fws.view.FWSView.FWSContext
import java.io.File

/**
 * 悬浮窗管理
 */
class CompletionWindowManager private constructor(
    private val application: Application,
    private val xiaomiClipboardPermissionTipsFile: File
) {
    private var mainViewWindow: EasyWindow<*>? = null
    private var iconViewWindow: EasyWindow<*>? = null
    private var isShowXiaomiClipboardPermissionTips: Boolean? = null

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
        if (isShowXiaomiClipboardPermissionTips == null) {
            isShowXiaomiClipboardPermissionTips =
                !xiaomiClipboardPermissionTipsFile.exists() && (DeviceOs.isHyperOs() || DeviceOs.isMiui())
        }
        startFloatingWindow(context, isShowXiaomiClipboardPermissionTips!!)
    }

    /**
     * 开启悬浮窗
     *
     * @param context                             上下文
     * @param isShowXiaomiClipboardPermissionTips 是否为小米用户或红米用户显示剪切板权限提示
     */
    @Suppress("deprecation")
    private fun startFloatingWindow(
        context: Context,
        isShowXiaomiClipboardPermissionTips: Boolean
    ) {
        if (!XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getSystemAlertWindowPermission()
            )
        ) {
            IsConfirmDialog(context, false)
                .message("需要悬浮窗权限，请进入设置进行授权")
                .onConfirm("打开设置") {
                    XXPermissions.with(context)
                        .permission(PermissionLists.getSystemAlertWindowPermission())
                        .request { _, deniedList ->
                            if (deniedList.isEmpty()) {
                                Toaster.show("悬浮窗权限获取成功")
                            } else {
                                Toaster.show("悬浮窗权限获取失败")
                            }
                        }
                }
                .show()
            return
        }
        if (isShowXiaomiClipboardPermissionTips) {
            IsConfirmDialog(context, false)
                .message("对于小米手机和红米手机，需要将写入剪切板权限设置为始终允许才能在悬浮窗复制文本。具体设置方式如下：设置-应用设置-权限管理-应用权限管理-CHelper-写入剪切板-始终允许。")
                .onConfirm { startFloatingWindow(context, false) }
                .onCancel("不再提示") {
                    this.isShowXiaomiClipboardPermissionTips = false
                    FileUtil.writeString(xiaomiClipboardPermissionTipsFile, "")
                    startFloatingWindow(context, false)
                }
                .show()
            return
        }
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
            { customContext: FWSContext? ->
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
            val FWSFloatingMainView = mainViewWindow!!.contentView as FWSMainView<*>?
            if (FWSFloatingMainView != null) {
                FWSFloatingMainView.onPause()
                FWSFloatingMainView.onDestroy()
            }
            mainViewWindow!!.recycle()
            mainViewWindow = null
        }
        if (iconViewWindow != null) {
            iconViewWindow!!.recycle()
            iconViewWindow = null
        }
    }

    companion object {
        var INSTANCE: CompletionWindowManager? = null

        /**
         * 初始化
         */
        @JvmStatic
        fun init(application: Application, xiaomiClipboardPermissionTipsFile: File) {
            INSTANCE = CompletionWindowManager(application, xiaomiClipboardPermissionTipsFile)
        }
    }
}
