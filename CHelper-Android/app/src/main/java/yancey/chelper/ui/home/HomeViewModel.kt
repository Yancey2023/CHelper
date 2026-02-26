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

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hjq.device.compat.DeviceOs
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.BuildConfig
import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.android.common.util.PolicyGrantManager
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.chelper.data.Announcement
import yancey.chelper.network.chelper.data.VersionInfo
import java.io.File

class HomeViewModel : ViewModel() {
    var policyGrantState by mutableStateOf(PolicyGrantManager.State.NOT_READ)
    var announcement by mutableStateOf<Announcement?>(null)
    var latestVersionInfo by mutableStateOf<VersionInfo?>(null)
    var isShowPermissionRequestWindow by mutableStateOf(false)
    var isShowXiaomiClipboardPermissionTips by mutableStateOf(false)
    val isShowPolicyGrantDialog get() = policyGrantState != PolicyGrantManager.State.AGREE
    var isShowAnnouncementDialog by mutableStateOf(false)
    var isShowUpdateNotificationsDialog by mutableStateOf(false)
    var isShowPublicLibrary by mutableStateOf(true)
    private var floatingWindowManager: FloatingWindowManager? = null
    private var isNeedToShowXiaomiClipboardPermissionTips: Boolean? = null
    private lateinit var skipXiaomiClipboardPermissionTipsFile: File
    private lateinit var skipAnnouncementFile: File
    private lateinit var skipVersionFile: File

    init {
        this.policyGrantState = PolicyGrantManager.INSTANCE.state
        isShowPublicLibrary = Settings.INSTANCE?.isShowPublicLibrary ?: true
    }

    fun init(context: Context, floatingWindowManager: FloatingWindowManager?) {
        this.floatingWindowManager = floatingWindowManager
        this.skipXiaomiClipboardPermissionTipsFile =
            context.dataDir.resolve("xiaomi_clipboard_permission_no_tips.txt")
        this.skipAnnouncementFile =
            context.dataDir.resolve("ignore_announcement.txt")
        this.skipVersionFile =
            context.dataDir.resolve("ignore_version.txt")
        if (policyGrantState == PolicyGrantManager.State.AGREE) {
            showAnnouncementDialog()
        }
    }

    fun isUsingFloatingWindow(): Boolean {
        return floatingWindowManager?.isUsingFloatingWindow == true
    }

    fun startFloatingWindow(
        context: Context,
        isSkipXiaomiClipboardPermissionTips: Boolean = false
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
                    !skipXiaomiClipboardPermissionTipsFile.exists() && (DeviceOs.isHyperOs() || DeviceOs.isMiui())
            }
            if (isNeedToShowXiaomiClipboardPermissionTips!!) {
                isShowXiaomiClipboardPermissionTips = true
                return
            }
        }
        floatingWindowManager?.startFloatingWindow(context)
    }

    fun dismissShowXiaomiClipboardPermissionTipsForever() {
        this.isNeedToShowXiaomiClipboardPermissionTips = false
        FileUtil.writeString(skipXiaomiClipboardPermissionTipsFile, "")
    }

    fun stopFloatingWindow() {
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
                announcement = ServiceManager.CHELPER_SERVICE!!.getAnnouncement()
                isShowPublicLibrary = announcement!!.isEnableCommandLab ?: true
                if (Settings.INSTANCE.isShowPublicLibrary != isShowPublicLibrary) {
                    Settings.INSTANCE.isShowPublicLibrary = isShowPublicLibrary
                    Settings.INSTANCE.save()
                }
                var isShow = true
                val isForce = announcement!!.isForce ?: false
                if (!isForce) {
                    isShow = announcement!!.isEnable ?: false
                    if (isShow) {
                        val ignoreAnnouncement = withContext(Dispatchers.IO) {
                            return@withContext skipAnnouncementFile.inputStream().bufferedReader()
                                .use { it.readText() }
                        }
                        val announcementHashCode = announcement.hashCode().toString()
                        if (announcementHashCode == ignoreAnnouncement) {
                            isShow = false
                        }
                    }
                }
                if (isShow) {
                    isShowAnnouncementDialog = true
                } else {
                    checkUpdate()
                }
            } catch (_: Exception) {

            }
        }
    }

    fun ignoreCurrentAnnouncement() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileUtil.writeString(
                        skipAnnouncementFile,
                        announcement.hashCode().toString()
                    )
                }
            } catch (_: Exception) {

            }
        }
    }

    fun dismissAnnouncementDialog() {
        isShowAnnouncementDialog = false
        checkUpdate()
    }

    fun checkUpdate() {
        if (Settings.INSTANCE.isEnableUpdateNotifications) {
            viewModelScope.launch {
                try {
                    latestVersionInfo = ServiceManager.CHELPER_SERVICE!!.getLatestVersionInfo()
                    if (latestVersionInfo!!.version_name != BuildConfig.VERSION_NAME) {
                        val ignoreVersion = withContext(Dispatchers.IO) {
                            skipVersionFile.bufferedReader()
                                .use { it.readText() }
                        }
                        if (latestVersionInfo!!.version_name != ignoreVersion) {
                            isShowUpdateNotificationsDialog = true
                        }
                    }
                } catch (_: Exception) {

                }
            }
        }
    }

    fun dismissUpdateNotificationDialog() {
        isShowUpdateNotificationsDialog = false
    }

    fun ignoreLatestVersion() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileUtil.writeString(
                        skipVersionFile,
                        latestVersionInfo!!.version_name
                    )
                }
            } catch (_: Exception) {

            }
        }
    }
}
