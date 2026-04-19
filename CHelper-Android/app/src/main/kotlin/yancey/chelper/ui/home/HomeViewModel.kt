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

package yancey.chelper.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.device.compat.DeviceOs
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import yancey.chelper.BuildConfig
import yancey.chelper.android.util.PolicyGrantManager
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.chelper.data.Announcement
import yancey.chelper.network.chelper.data.VersionInfo
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var policyGrantState by mutableStateOf(PolicyGrantManager.State.NOT_READ)
    var announcement by mutableStateOf<Announcement?>(null)
    var latestVersionInfo by mutableStateOf<VersionInfo?>(null)
    var isShowPermissionRequestWindow by mutableStateOf(false)
    var isShowXiaomiClipboardPermissionTips by mutableStateOf(false)
    val isShowPolicyGrantDialog get() = policyGrantState != PolicyGrantManager.State.AGREE
    var isShowAnnouncementDialog by mutableStateOf(false)
    var isShowUpdateNotificationsDialog by mutableStateOf(false)
    var isShowCommandLabVersionDialog by mutableStateOf(false)
    private val settingsDataStore = SettingsDataStore(application.applicationContext)
    private var isNeedToShowXiaomiClipboardPermissionTips: Boolean? = null
    private val skipXiaomiClipboardPermissionTipsFile: File =
        application.dataDir.resolve(SKIP_XIAOMI_CLIPBOARD_PERMISSION_TIPS_FILENAME)
    private val skipAnnouncementFile: File =
        application.dataDir.resolve(SKIP_ANNOUNCEMENT_FILENAME)
    private val skipVersionFile: File =
        application.dataDir.resolve(SKIP_VERSION_FILENAME)

    init {
        this.policyGrantState = PolicyGrantManager.INSTANCE.state
        if (policyGrantState == PolicyGrantManager.State.AGREE) {
            showAnnouncementDialog()
        }
    }

    fun isUsingFloatingWindow(floatingWindowManager: FloatingWindowManager?): Boolean {
        return floatingWindowManager?.isUsingFloatingWindow == true
    }

    fun startFloatingWindow(
        context: Context,
        isSkipXiaomiClipboardPermissionTips: Boolean,
        floatingWindowIconSize: Int,
        floatingWindowIconAlpha: Float,
        floatingWindowScreenAlpha: Float,
        isFloatingWindowFontAlphaSync: Boolean,
        floatingWindowManager: FloatingWindowManager?,
    ) {
        if (!XXPermissions.isGrantedPermission(
                context,
                PermissionLists.getSystemAlertWindowPermission()
            )
        ) {
            isShowPermissionRequestWindow = true
            return
        }
        if (!isSkipXiaomiClipboardPermissionTips) {
            if (isNeedToShowXiaomiClipboardPermissionTips == null) {
                isNeedToShowXiaomiClipboardPermissionTips =
                    !skipXiaomiClipboardPermissionTipsFile.exists() &&
                            (DeviceOs.isHyperOs() || DeviceOs.isMiui())
            }
            if (isNeedToShowXiaomiClipboardPermissionTips!!) {
                isShowXiaomiClipboardPermissionTips = true
                return
            }
        }
        floatingWindowManager?.startFloatingWindow(
            context,
            floatingWindowIconSize,
            floatingWindowIconAlpha,
            floatingWindowScreenAlpha,
            isFloatingWindowFontAlphaSync,
        )
    }

    fun dismissShowXiaomiClipboardPermissionTipsForever() {
        this.isNeedToShowXiaomiClipboardPermissionTips = false
        try {
            skipXiaomiClipboardPermissionTipsFile.parentFile?.mkdirs()
            skipXiaomiClipboardPermissionTipsFile.outputStream().use { it.write("".toByteArray()) }
        } catch (_: Exception) {

        }
    }

    fun stopFloatingWindow(floatingWindowManager: FloatingWindowManager?) {
        floatingWindowManager?.stopFloatingWindow()
    }

    fun agreePolicy() {
        this.policyGrantState = PolicyGrantManager.State.AGREE
        PolicyGrantManager.INSTANCE.agree()
        showAnnouncementDialog()
    }

    fun showAnnouncementDialog() {
        if (isShowPolicyGrantDialog) {
            return
        }
        viewModelScope.launch {
            try {
                ServiceManager.CHELPER_SERVICE.getAnnouncement().let { it ->
                    announcement = it
                    settingsDataStore.setIsShowPublicLibrary(it.isEnableCommandLab ?: true)
                    it.publicLibraryMinVersion?.let {
                        settingsDataStore.setPublicLibraryMinVersion(it)
                    }
                    var isShow = true
                    val isForce = it.isForce ?: false
                    if (!isForce) {
                        isShow = it.isEnable ?: false
                        if (isShow) {
                            try {
                                val ignoreAnnouncement = withContext(Dispatchers.IO) {
                                    return@withContext skipAnnouncementFile.inputStream()
                                        .bufferedReader()
                                        .use { it.readText() }
                                }
                                val announcementHashCode = announcement.hashCode().toString()
                                if (announcementHashCode == ignoreAnnouncement) {
                                    isShow = false
                                }
                            } catch (_: Exception) {

                            }
                        }
                    }
                    if (isShow) {
                        isShowAnnouncementDialog = true
                    } else {
                        checkUpdate()
                    }
                }
            } catch (_: Exception) {

            }
        }
    }

    fun ignoreCurrentAnnouncement() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    skipAnnouncementFile.outputStream()
                        .write(announcement.hashCode().toString().toByteArray())
                }
            } catch (_: Exception) {

            }
        }
    }

    fun checkUpdate() {
        if (!runBlocking { settingsDataStore.isEnableUpdateNotifications().first() }) {
            return
        }
        viewModelScope.launch {
            try {
                ServiceManager.CHELPER_SERVICE.getLatestVersionInfo().let { it ->
                    latestVersionInfo = it
                    var isShow = it.versionName != BuildConfig.VERSION_NAME
                    if (isShow) {
                        try {
                            val ignoreVersion = withContext(Dispatchers.IO) {
                                skipVersionFile.bufferedReader()
                                    .use { it.readText() }
                            }
                            if (it.versionName == ignoreVersion) {
                                isShow = false
                            }
                        } catch (_: Exception) {

                        }
                    }
                    if (isShow) {
                        isShowUpdateNotificationsDialog = true
                    }
                }
            } catch (_: Exception) {

            }
        }
    }

    fun ignoreLatestVersion() {
        viewModelScope.launch {
            try {
                latestVersionInfo?.versionName?.let {
                    withContext(Dispatchers.IO) {
                        skipVersionFile.outputStream()
                            .write(it.toByteArray())
                    }
                }
            } catch (_: Exception) {

            }
        }
    }

    fun checkCommandLabVersion(publicLibraryMinVersion: Int, onVersionOk: () -> Unit) {
        if (BuildConfig.VERSION_CODE < publicLibraryMinVersion) {
            isShowCommandLabVersionDialog = true
        } else {
            onVersionOk()
        }
    }

    fun dismissCommandLabVersionDialog() {
        isShowCommandLabVersionDialog = false
    }

    companion object {
        private const val SKIP_XIAOMI_CLIPBOARD_PERMISSION_TIPS_FILENAME =
            "xiaomi_clipboard_permission_no_tips.txt"
        private const val SKIP_ANNOUNCEMENT_FILENAME = "ignore_announcement.txt"
        private const val SKIP_VERSION_FILENAME = "ignore_version.txt"
    }
}
