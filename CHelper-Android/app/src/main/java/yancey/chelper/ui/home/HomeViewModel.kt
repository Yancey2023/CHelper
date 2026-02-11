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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.BuildConfig
import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.android.common.util.PolicyGrantManager
import yancey.chelper.android.common.util.Settings
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.chelper.data.Announcement
import yancey.chelper.network.chelper.data.VersionInfo
import java.io.File

class HomeViewModel : ViewModel() {
    var policyGrantState by mutableStateOf(PolicyGrantManager.State.NOT_READ)
    var announcement by mutableStateOf<Announcement?>(null)
    var latestVersionInfo by mutableStateOf<VersionInfo?>(null)
    val isShowPolicyGrantDialog get() = policyGrantState != PolicyGrantManager.State.AGREE
    var isShowAnnouncementDialog by mutableStateOf(false)
    var isShowUpdateNotificationsDialog by mutableStateOf(false)

    init {
        this.policyGrantState = PolicyGrantManager.INSTANCE.state
    }

    fun agreePolicy() {
        this.policyGrantState = PolicyGrantManager.State.AGREE
        PolicyGrantManager.INSTANCE.agree()
    }

    fun showAnnouncementDialog(context: Context) {
        if (isShowPolicyGrantDialog) {
            return
        }
        viewModelScope.launch {
            try {
                announcement = ServiceManager.CHELPER_SERVICE!!.getAnnouncement()
                var isShow = true
                val isForce = announcement!!.isForce ?: false
                if (!isForce) {
                    isShow = announcement!!.isEnable ?: false
                    if (isShow) {
                        val ignoreAnnouncement = withContext(Dispatchers.IO) {
                            return@withContext File(
                                context.dataDir,
                                "ignore_announcement.txt"
                            ).inputStream().bufferedReader()
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
                    checkUpdate(context)
                }
            } catch (_: Exception) {

            }
        }
    }

    fun ignoreCurrentAnnouncement(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileUtil.writeString(
                        File(context.dataDir, "ignore_announcement.txt"),
                        announcement.hashCode().toString()
                    )
                }
            } catch (_: Exception) {

            }
        }
    }

    fun dismissAnnouncementDialog(context: Context) {
        isShowAnnouncementDialog = false
        checkUpdate(context)
    }

    fun checkUpdate(context: Context) {
        if (Settings.INSTANCE.isEnableUpdateNotifications) {
            viewModelScope.launch {
                try {
                    latestVersionInfo = ServiceManager.CHELPER_SERVICE!!.getLatestVersionInfo()
                    if (latestVersionInfo!!.version_name != BuildConfig.VERSION_NAME) {
                        val ignoreVersion = withContext(Dispatchers.IO) {
                            File(context.dataDir, "ignore_version.txt").bufferedReader()
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

    fun dismissUpdateNotificationDialog(context: Context) {
        isShowAnnouncementDialog = false
        checkUpdate(context)
    }

    fun ignoreLatestVersion(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileUtil.writeString(
                        File(context.dataDir, "ignore_version.txt"),
                        latestVersionInfo!!.version_name
                    )
                }
            } catch (_: Exception) {

            }
        }
    }
}
