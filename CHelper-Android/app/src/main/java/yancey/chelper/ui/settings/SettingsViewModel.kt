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

package yancey.chelper.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import yancey.chelper.android.common.style.CustomTheme
import yancey.chelper.android.common.util.Settings

class SettingsViewModel : ViewModel() {

    var isEnableUpdateNotifications by mutableStateOf(false)
    var floatingWindowAlpha by mutableFloatStateOf(1f)
    var floatingWindowSize by mutableIntStateOf(40)
    var isCheckingBySelection by mutableStateOf(false)
    var isHideWindowWhenCopying by mutableStateOf(false)
    var isSavingWhenPausing by mutableStateOf(false)
    var isCrowed by mutableStateOf(false)
    var isShowErrorReason by mutableStateOf(false)
    var isSyntaxHighlight by mutableStateOf(false)
    var cpackBranch by mutableStateOf("")
    private var _themeId = mutableStateOf("")
    private var onThemeChangedCallback: (() -> Unit)? = null

    var isShowResumeBackgroundDialog by mutableStateOf(false)
    var isShowChooseThemeDialog by mutableStateOf(false)
    var isShowInputFloatingWindowAlphaDialog by mutableStateOf(false)
    var isShowInputFloatingWindowSizeDialog by mutableStateOf(false)
    var isShowChooseCpackBranchDialog by mutableStateOf(false)

    val cpackBranches =
        arrayOf(
            "release-vanilla",
            "release-experiment",
            "beta-vanilla",
            "beta-experiment",
            "netease-vanilla",
            "netease-experiment"
        )
    val cpackBranchTranslations =
        arrayOf(
            "正式版-原版-" + Settings.versionReleaseVanilla,
            "正式版-实验性玩法-" + Settings.versionReleaseExperiment,
            "测试版-原版-" + Settings.versionBetaVanilla,
            "测试版-实验性玩法-" + Settings.versionBetaExperiment,
            "中国版-原版-" + Settings.versionNeteaseVanilla,
            "中国版-实验性玩法-" + Settings.versionNeteaseExperiment
        )

    init {
        try {
            this.isEnableUpdateNotifications = Settings.INSTANCE.isEnableUpdateNotifications
            this.isCheckingBySelection = Settings.INSTANCE.isCheckingBySelection
            this.isHideWindowWhenCopying = Settings.INSTANCE.isHideWindowWhenCopying
            this.isSavingWhenPausing = Settings.INSTANCE.isSavingWhenPausing
            this.isCrowed = Settings.INSTANCE.isCrowed
            this.isShowErrorReason = Settings.INSTANCE.isShowErrorReason
            this.isSyntaxHighlight = Settings.INSTANCE.isSyntaxHighlight
            this.cpackBranch = Settings.INSTANCE.cpackBranch
            this._themeId.value = Settings.INSTANCE.themeId
        } catch (_: Exception) {

        }

    }

    fun setThemeId(themeId: String) {
        this._themeId.value = themeId
        Settings.INSTANCE.themeId = themeId
        CustomTheme.refreshTheme()
        onThemeChangedCallback?.invoke()
    }

    fun setOnThemeChangedCallback(callback: () -> Unit) {
        onThemeChangedCallback = callback
    }

    override fun onCleared() {
        super.onCleared()
        Settings.INSTANCE.isEnableUpdateNotifications = isEnableUpdateNotifications
        Settings.INSTANCE.floatingWindowAlpha = floatingWindowAlpha
        Settings.INSTANCE.floatingWindowSize = floatingWindowSize
        Settings.INSTANCE.isCheckingBySelection = isCheckingBySelection
        Settings.INSTANCE.isHideWindowWhenCopying = isHideWindowWhenCopying
        Settings.INSTANCE.isSavingWhenPausing = isSavingWhenPausing
        Settings.INSTANCE.isCrowed = isCrowed
        Settings.INSTANCE.isShowErrorReason = isShowErrorReason
        Settings.INSTANCE.isSyntaxHighlight = isSyntaxHighlight
        Settings.INSTANCE.cpackBranch = cpackBranch
        Settings.INSTANCE.save()
    }
}
