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

package yancey.chelper.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import com.hjq.toast.Toaster
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.android.util.PolicyGrantManager
import yancey.chelper.data.BackgroundStore
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.ServiceManager
import yancey.chelper.android.window.LoongFlowWindowManager
import yancey.chelper.network.library.util.GuestAuthUtil
import yancey.chelper.network.library.util.LoginUtil

class CHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 隐私政策管理初始化
        PolicyGrantManager.init(
            assets.open("about/privacy_policy.txt").bufferedReader().use { it.readText() },
            dataDir.resolve("lastReadContent.txt")
        )
        // 用于数据分析和性能监控的第三方库初始化，依赖于 PolicyGrantManager
        MonitorUtil.init(this)
        // Toast初始化
        Toaster.init(this)
        Toaster.setGravity(
            Gravity.BOTTOM,
            0,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            ).toInt()
        )
        // 游龙悬浮窗单例初始化
        LoongFlowWindowManager.init(this)
        // 网络服务初始化
        ServiceManager.init(this)
        LoginUtil.init(dataDir.resolve("library").resolve("user.json")) { throwable ->
            Log.e("LoginUtil", "fail to read user from json", throwable)
            MonitorUtil.generateCustomLog(throwable, "ReadUserException")
        }
        // 访客认证初始化（用于自动访客登录）
        GuestAuthUtil.init(this)
        // 设置初始化
        SettingsDataStore(this).init()
        // 自定义主题初始化
        BackgroundStore.init(dataDir.resolve("theme"))

        // 悬浮窗前台服务通知渠道
        createFloatingWindowNotificationChannel()
    }

    private fun createFloatingWindowNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FLOATING_WINDOW_CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不发声不震动
            ).apply {
                description = "CHelper 悬浮窗运行时的常驻通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FLOATING_WINDOW_CHANNEL_ID = "floating_window_channel"
    }
}
