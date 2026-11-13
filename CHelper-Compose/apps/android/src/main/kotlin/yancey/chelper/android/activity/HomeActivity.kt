/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yancey.chelper.android.navigation.NavHost
import yancey.chelper.android.storage.BackgroundStore
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.navigation.CommandLabDeepLinkParser
import yancey.chelper.ui.navigation.openCommandLabDeepLink
import yancey.chelper.ui.platform.ClientMessages

/** 首页 */
class HomeActivity : BaseComposeActivity() {
    private lateinit var floatingWindowManager: FloatingWindowManager
    private var backgroundJob: Job? = null
    private var isSavingBackground by mutableStateOf(false)
    private lateinit var photoPicker: ActivityResultLauncher<PickVisualMediaRequest>
    private var isShowSavingBackgroundDialog = mutableStateOf(false)
    private var pendingDeepLink by mutableStateOf<CommandLabDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        floatingWindowManager = FloatingWindowManager(application)
        photoPicker =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                setBackground(it)
            }
        queueDeepLink(intent)
        setContent {
            val navController = rememberNavController()
            val deepLink = pendingDeepLink
            LaunchedEffect(deepLink) {
                deepLink?.let {
                    navController.currentBackStackEntryFlow.first()
                    navController.openCommandLabDeepLink(it)
                    pendingDeepLink = null
                }
            }
            NavHost(
                navController = navController,
                floatingWindowManager = floatingWindowManager,
                chooseBackground = this::chooseBackground,
                restoreBackground = this::restoreBackground,
                isShowSavingBackgroundDialog = isShowSavingBackgroundDialog,
                isSavingBackground = isSavingBackground,
                shutdown = this::finishAffinity,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        queueDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindowManager.stopFloatingWindow()
    }

    private fun queueDeepLink(intent: Intent?) {
        CommandLabDeepLinkParser.parse(intent?.dataString)?.let { pendingDeepLink = it }
    }

    private fun chooseBackground() {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun setBackground(uri: Uri?) {
        if (uri == null) return
        changeBackground { BackgroundStore.INSTANCE.replace(contentResolver, uri) }
    }

    private fun restoreBackground() {
        changeBackground { BackgroundStore.INSTANCE.restore() }
    }

    private fun changeBackground(action: suspend () -> Unit) {
        backgroundJob?.cancel()
        backgroundJob =
            lifecycleScope.launch {
                isSavingBackground = true
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ClientMessages.show(e.message ?: "背景设置失败")
                    MonitorUtil.generateCustomLog(e, "BackgroundChangeException")
                } finally {
                    if (currentCoroutineContext().isActive) {
                        isSavingBackground = false
                        isShowSavingBackgroundDialog.value = false
                    }
                }
            }
    }
}
