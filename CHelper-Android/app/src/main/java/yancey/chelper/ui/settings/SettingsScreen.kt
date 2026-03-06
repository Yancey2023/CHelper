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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.android.common.util.CustomTheme
import yancey.chelper.android.common.util.SettingsDataStore
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.dialog.InputStringDialog
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.layout.Collection
import yancey.chelper.ui.common.layout.CollectionName
import yancey.chelper.ui.common.layout.NameAndAction
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.SettingsItem
import yancey.chelper.ui.common.widget.Divider

@Composable
fun SettingsScreen(
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    onChooseTheme: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    var isShowResumeBackgroundDialog by remember { mutableStateOf(false) }
    var isShowChooseThemeDialog by remember { mutableStateOf(false) }
    var isShowInputFloatingWindowAlphaDialog by remember { mutableStateOf(false) }
    var isShowInputFloatingWindowSizeDialog by remember { mutableStateOf(false) }
    var isShowChooseCpackBranchDialog by remember { mutableStateOf(false) }
    val isEnableUpdateNotifications by settingsDataStore.isEnableUpdateNotifications()
        .collectAsState(initial = false)
    val cpackBranch by settingsDataStore.cpackBranch()
        .collectAsState(initial = "release-experiment")
    val isCheckingBySelection by settingsDataStore.isCheckingBySelection()
        .collectAsState(initial = false)
    val isHideWindowWhenCopying by settingsDataStore.isHideWindowWhenCopying()
        .collectAsState(initial = false)
    val isSavingWhenPausing by settingsDataStore.isSavingWhenPausing()
        .collectAsState(initial = false)
    val isCrowded by settingsDataStore.isCrowded()
        .collectAsState(initial = false)
    val isShowErrorReason by settingsDataStore.isShowErrorReason()
        .collectAsState(initial = false)
    val isSyntaxHighlight by settingsDataStore.isSyntaxHighlight()
        .collectAsState(initial = false)
    val floatingWindowSize by settingsDataStore.floatingWindowSize()
        .collectAsState(initial = 40)
    val floatingWindowAlpha by settingsDataStore.floatingWindowAlpha()
        .collectAsState(initial = 1.0f)
    var cpackBranchesWithTranslate by remember {
        mutableStateOf(
            arrayOf(
                "release-vanilla" to "正式版-原版",
                "release-experiment" to "正式版-实验性玩法",
                "beta-vanilla" to "测试版-原版",
                "beta-experiment" to "测试版-实验性玩法",
                "netease-vanilla" to "中国版-原版",
                "netease-experiment" to "中国版-实验性玩法",
            )
        )
    }
    LaunchedEffect(context) {
        coroutineScope.launch {
            val filenames = withContext(Dispatchers.IO) { context.assets.list("cpack")!! }
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
                    "正式版-原版-",
                    "正式版-实验性玩法-",
                    "测试版-原版-",
                    "测试版-实验性玩法-",
                    "中国版-原版-",
                    "中国版-实验性玩法-"
                )
            val newCPackBranchesWithTranslate = mutableListOf<Pair<String, String>>()
            for (filename in filenames) {
                for (i in 0..<cpackBranches.size) {
                    if (filename!!.startsWith(cpackBranches[i])) {
                        val version = filename.substring(
                            cpackBranches[i].length,
                            filename.length - ".cpack".length
                        )
                        newCPackBranchesWithTranslate.add("${cpackBranchTranslations[i]}${version}" to cpackBranches[i])
                    }
                }
            }
            cpackBranchesWithTranslate = newCPackBranchesWithTranslate.toTypedArray()
        }
    }
    RootViewWithHeaderAndCopyright(stringResource(R.string.layout_settings_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CollectionName(stringResource(R.string.layout_settings_application_update))
            Collection {
                SettingsItem(
                    name = stringResource(R.string.layout_settings_is_enable_update_notification),
                    description = stringResource(R.string.layout_settings_is_enable_update_notification_description),
                    checked = isEnableUpdateNotifications,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsEnableUpdateNotifications(it)
                        }
                    },
                )
            }
            CollectionName(stringResource(R.string.layout_settings_theme_settings))
            Collection {
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_background),
                    description = stringResource(R.string.layout_settings_choose_background_description),
                ) {
                    chooseBackground()
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_restore_background),
                    description = stringResource(R.string.layout_settings_restore_background_description),
                ) {
                    isShowResumeBackgroundDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_theme),
                    description = stringResource(R.string.layout_settings_choose_theme_description),
                ) {
                    isShowChooseThemeDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_alpha),
                    description = stringResource(R.string.layout_settings_floating_window_alpha_description),
                ) {
                    isShowInputFloatingWindowAlphaDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_size),
                    description = stringResource(R.string.layout_settings_floating_window_size_description),
                ) {
                    isShowInputFloatingWindowSizeDialog = true
                }
            }
            CollectionName(stringResource(R.string.layout_settings_completion_settings))
            Collection {
                val currentCpackBranchTranslation = remember(cpackBranchesWithTranslate) {
                    for (pair in cpackBranchesWithTranslate) {
                        if (cpackBranch == pair.second) {
                            return@remember pair.first
                        }
                    }
                    return@remember cpackBranch
                }
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_cpack),
                    description = stringResource(
                        R.string.layout_settings_current_cpack,
                        currentCpackBranchTranslation
                    )
                ) {
                    isShowChooseCpackBranchDialog = true
                }
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_checking_by_selection),
                    description = stringResource(R.string.layout_setting_checking_by_selection_description),
                    checked = isCheckingBySelection,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsCheckingBySelection(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_hide_window_when_copying),
                    description = stringResource(R.string.layout_setting_is_hide_window_when_copying_description),
                    checked = isHideWindowWhenCopying,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsHideWindowWhenCopying(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_saving_when_pausing),
                    description = stringResource(R.string.layout_setting_is_saving_when_pausing_description),
                    checked = isSavingWhenPausing,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsSavingWhenPausing(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_crowed),
                    description = stringResource(R.string.layout_setting_is_crowed_description),
                    checked = isCrowded,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsCrowded(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_show_error_reason),
                    description = stringResource(R.string.layout_setting_is_show_error_reason_description),
                    checked = isShowErrorReason,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsShowErrorReason(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_syntax_highlight),
                    description = stringResource(R.string.layout_setting_is_syntax_highlight_description),
                    checked = isSyntaxHighlight,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsSyntaxHighlight(it)
                        }
                    },
                )
            }
        }
    }
    if (isShowChooseThemeDialog) {
        val data = remember {
            arrayOf(
                "浅色模式" to "MODE_NIGHT_NO",
                "深色模式" to "MODE_NIGHT_YES",
                "跟随系统" to "MODE_NIGHT_FOLLOW_SYSTEM",
            )
        }
        ChoosingDialog(
            onDismissRequest = { isShowChooseThemeDialog = false },
            data = data,
            onChoose = {
                coroutineScope.launch {
                    settingsDataStore.setThemeId(it)
                    CustomTheme.refreshTheme(it)
                    onChooseTheme()
                }
            })
    }
    if (isShowResumeBackgroundDialog) {
        IsConfirmDialog(
            onDismissRequest = { isShowResumeBackgroundDialog = false },
            content = "是否恢复背景？",
            onConfirm = {
                restoreBackground()
            }
        )
    }
    if (isShowInputFloatingWindowSizeDialog) {
        val textFieldState = rememberTextFieldState(
            initialText = floatingWindowSize.toString()
        )
        InputStringDialog(
            onDismissRequest = { isShowInputFloatingWindowSizeDialog = false },
            title = "请输入透明度",
            textFieldState = textFieldState,
            onConfirm = {
                try {
                    var integer = textFieldState.text.toString().toInt()
                    if (integer < 10) {
                        integer = 10
                    } else if (integer > 100) {
                        integer = 100
                    }
                    coroutineScope.launch {
                        settingsDataStore.setFloatingWindowSize(integer)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (isShowInputFloatingWindowAlphaDialog) {
        val textFieldState = rememberTextFieldState(
            initialText = (floatingWindowAlpha * 100).toInt().toString()
        )
        InputStringDialog(
            onDismissRequest = { isShowInputFloatingWindowAlphaDialog = false },
            title = "请输入透明度",
            textFieldState = textFieldState,
            onConfirm = {
                try {
                    var integer = textFieldState.text.toString().toInt()
                    if (integer < 10) {
                        integer = 10
                    } else if (integer > 100) {
                        integer = 100
                    }
                    coroutineScope.launch {
                        settingsDataStore.setFloatingWindowAlpha(integer / 100f)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (isShowChooseCpackBranchDialog) {
        ChoosingDialog(
            onDismissRequest = { isShowChooseCpackBranchDialog = false },
            data = cpackBranchesWithTranslate,
            onChoose = {
                coroutineScope.launch {
                    settingsDataStore.setCpackBranch(it)
                }
            })
    }
}

@Preview
@Composable
fun SettingsScreenLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        SettingsScreen(
            chooseBackground = {},
            restoreBackground = {},
            onChooseTheme = {},
        )
    }
}

@Preview
@Composable
fun SettingsScreenDarkThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Dark,
        backgroundBitmap = null
    ) {
        SettingsScreen(
            chooseBackground = {},
            restoreBackground = {},
            onChooseTheme = {},
        )
    }
}
