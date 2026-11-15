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
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import com.hjq.toast.Toaster
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import yancey.chelper.BuildConfig
import yancey.chelper.android.network.AndroidBrowserVerifier
import yancey.chelper.android.storage.BackgroundStore
import yancey.chelper.android.storage.EncryptedCredentialStore
import yancey.chelper.android.storage.clientDataStores
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.android.window.LoongFlowWindowManager
import yancey.chelper.android.work.LibrarySyncScheduler
import yancey.chelper.data.policy.PolicyGrantManager
import yancey.chelper.data.remote.NetworkConfig
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.data.startup.StartupState
import yancey.chelper.nativecore.network.jvmHttpEngine
import yancey.chelper.ui.platform.ClientMessages

class CHelperApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var network: NetworkServices
        private set

    lateinit var startup: StartupState
        private set

    override fun onCreate() {
        super.onCreate()
        // Toast feedback must also work before a Compose host exists or after it closes.
        Toaster.init(this)
        Toaster.setGravity(
            Gravity.BOTTOM,
            0,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)
                .toInt(),
        )
        ClientMessages.setPlatformPresenter { Toaster.show(it) }
        BackgroundStore.init(
            dataDir.resolve("theme"),
            maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                .coerceIn(720, 2048),
        )
        val credentialsReady = CompletableDeferred<Unit>()
        startup =
            StartupState(
                this.clientDataStores().settings.settings(),
                { withContext(Dispatchers.IO) { BackgroundStore.INSTANCE.load() } },
                applicationScope,
            ) {
                credentialsReady.await()
            }
        // 隐私政策管理初始化
        PolicyGrantManager.init(
            assets.open("about/privacy_policy.txt").bufferedReader().use { it.readText() },
            dataDir.resolve("lastReadContent.txt").toOkioPath(),
            MonitorUtil::onAgreePolicyGrant,
        )
        // 用于数据分析和性能监控的第三方库初始化，依赖于 PolicyGrantManager
        MonitorUtil.init(this)
        // 游龙悬浮窗单例初始化
        LoongFlowWindowManager.init(this)
        // 网络服务初始化
        val config = NetworkConfig(debug = BuildConfig.DEBUG)
        network =
            NetworkServices(
                { cached -> jvmHttpEngine(cacheDir, cached) },
                config,
                AndroidBrowserVerifier(this, config.labBaseUrl),
            )
        applicationScope.launch(Dispatchers.IO) {
            try {
                network.login.init(EncryptedCredentialStore(this@CHelperApplication)) { throwable ->
                    Log.e("LoginUtil", "Unable to restore protected session", throwable)
                    MonitorUtil.generateCustomLog(throwable, "ReadUserException")
                }
            } finally {
                credentialsReady.complete(Unit)
            }
        }
        LibrarySyncScheduler.observe(this, applicationScope)
        // 访客认证初始化（用于自动访客登录）
        network.guest.init(
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        )

        // 悬浮窗前台服务通知渠道
        createFloatingWindowNotificationChannel()
    }

    private fun createFloatingWindowNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                        FLOATING_WINDOW_CHANNEL_ID,
                        "悬浮窗服务",
                        NotificationManager.IMPORTANCE_LOW, // 低优先级，不发声不震动
                    )
                    .apply { description = "CHelper 悬浮窗运行时的常驻通知" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FLOATING_WINDOW_CHANNEL_ID = "floating_window_channel"
    }
}

val Context.startupState: StartupState
    get() = (applicationContext as CHelperApplication).startup

val Context.networkServices: NetworkServices
    get() = (applicationContext as CHelperApplication).network
