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

package yancey.chelper.android.util

import android.app.Application
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import com.umeng.umcrash.UMCrash
import yancey.chelper.BuildConfig

object MonitorUtil {
    private var application: Application? = null
    private var isInit = false

    fun init(application: Application?) {
        if (BuildConfig.DEBUG) {
            return
        }
        MonitorUtil.application = application
        if (PolicyGrantManager.INSTANCE.state == PolicyGrantManager.State.AGREE) {
            UMConfigure.init(
                application,
                "6836aa2bbc47b67d8374e464",
                "official",
                UMConfigure.DEVICE_TYPE_PHONE,
                ""
            )
            isInit = true
        } else {
            UMConfigure.preInit(application, "6836aa2bbc47b67d8374e464", "official")
            isInit = false
        }
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)
    }

    fun onAgreePolicyGrant() {
        if (BuildConfig.DEBUG) {
            return
        }
        if (isInit) {
            return
        }
        UMConfigure.init(
            application,
            "6836aa2bbc47b67d8374e464",
            "official",
            UMConfigure.DEVICE_TYPE_PHONE,
            ""
        )
        isInit = true
    }

    fun generateCustomLog(e: Throwable?, type: String?) {
        if (BuildConfig.DEBUG) {
            return
        }
        UMCrash.generateCustomLog(e, type)
    }
}
