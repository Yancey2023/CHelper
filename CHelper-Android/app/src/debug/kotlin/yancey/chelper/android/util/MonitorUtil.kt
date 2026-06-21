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
import android.util.Log

/**
 * debug 包的 no-op 版本——故意不依赖 Umeng，让 debug 编译完全不需要拉这套 SDK，
 * 节省几千个类的 dex 化和构建时间。
 *
 * 主代码到处 import 的是 `yancey.chelper.android.util.MonitorUtil`，因为 main / release / debug
 * 三个 sourceSet 合并的关系，每个 variant 都能拿到同名同 API 的实现：
 *   - debug → 这里（空跑 + logcat 留底）
 *   - release / beta → src/release/kotlin/.../MonitorUtil.kt（真上 Umeng）
 *
 * 既然是 debug，干脆顺便把 type 和 throwable 打到 logcat，
 * 本地排查问题时能直接 `adb logcat -s MonitorUtil` 看到，比 release 还方便。
 */
object MonitorUtil {

    private const val TAG = "MonitorUtil"

    fun init(application: Application?) {
        Log.d(TAG, "debug stub init: application=$application")
    }

    fun onAgreePolicyGrant() {
        Log.d(TAG, "debug stub onAgreePolicyGrant")
    }

    fun generateCustomLog(e: Throwable?, type: String?) {
        // 即使是 stub，开发期间该曝光的异常仍然要曝光——只是不上报远端
        Log.w(TAG, "debug stub generateCustomLog: type=$type", e)
    }
}
