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
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import com.hjq.toast.Toaster
import yancey.chelper.android.common.util.CustomTheme
import yancey.chelper.android.common.util.LocalLibraryManager
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.android.common.util.PolicyGrantManager
import yancey.chelper.android.common.util.Settings
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.util.GuestAuthUtil
import yancey.chelper.network.library.util.LoginUtil
import java.io.File

class CHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 隐私政策管理初始化
        PolicyGrantManager.init(
            assets.open("about/privacy_policy.txt").bufferedReader().use { it.readText() },
            File(dataDir, "lastReadContent.txt")
        )
        // 用于数据分析和性能监控的第三方库初始化
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
        // 设置初始化 (Must be before ServiceManager)
        Settings.init(
            this,
            dataDir.resolve("settings").resolve("settings.json")
        ) { throwable ->
            Log.e("Settings", "fail to read settings from json", throwable)
            MonitorUtil.generateCustomLog(throwable, "ReadSettingException")
        }
        // 网络服务初始化
        ServiceManager.init(this)
        LoginUtil.init(dataDir.resolve("library").resolve("user.json")) { throwable ->
            Log.e("LoginUtil", "fail to read user from json", throwable)
            MonitorUtil.generateCustomLog(throwable, "ReadUserException")
        }
        // 访客认证初始化（用于自动访客登录）
        GuestAuthUtil.init(this)

        // 自定义主题初始化
        CustomTheme.init(dataDir.resolve("theme"))
        // 本地命令库初始化
        LocalLibraryManager.init(dataDir.resolve("localLibrary").resolve("data.json"))
    }
}
