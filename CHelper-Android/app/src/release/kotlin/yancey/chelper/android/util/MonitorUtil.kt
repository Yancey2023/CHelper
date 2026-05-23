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

/**
 * release / beta 包的崩溃 & 行为埋点实现，依赖友盟 SDK。
 *
 * 之所以放在 release sourceSet 而非 main——
 * 让 debug 包能完全不引入 Umeng 三件套（common/asms/apm），从而省下大概几千个类的
 * dex 化和打包时间，构建提速明显。debug 用同包同名同 API 的 no-op 版本顶上。
 */
object MonitorUtil {
    private var application: Application? = null
    private var isInit = false

    fun init(application: Application?) {
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
        UMCrash.generateCustomLog(e, type)
    }
}
