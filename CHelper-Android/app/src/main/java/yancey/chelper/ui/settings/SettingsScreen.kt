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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import yancey.chelper.R
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
    viewModel: SettingsViewModel = viewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.setOnThemeChangedCallback(onChooseTheme)
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
                    checked = viewModel.isEnableUpdateNotifications,
                    onCheckedChange = { viewModel.isEnableUpdateNotifications = it },
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
                    viewModel.isShowResumeBackgroundDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_theme),
                    description = stringResource(R.string.layout_settings_choose_theme_description),
                ) {
                    viewModel.isShowChooseThemeDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_alpha),
                    description = stringResource(R.string.layout_settings_floating_window_alpha_description),
                ) {
                    viewModel.isShowInputFloatingWindowAlphaDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_size),
                    description = stringResource(R.string.layout_settings_floating_window_size_description),
                ) {
                    viewModel.isShowInputFloatingWindowSizeDialog = true
                }
            }
            CollectionName(stringResource(R.string.layout_settings_completion_settings))
            Collection {
                val currentCpackBranchTranslation = remember(viewModel.cpackBranch) {
                    for (i in viewModel.cpackBranches.indices) {
                        if (viewModel.cpackBranch == viewModel.cpackBranches[i]) {
                            return@remember viewModel.cpackBranchTranslations[i]
                        }
                    }
                }
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_cpack),
                    description = stringResource(
                        R.string.layout_settings_current_cpack,
                        currentCpackBranchTranslation
                    )
                ) {
                    viewModel.isShowChooseCpackBranchDialog = true
                }
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_checking_by_selection),
                    description = stringResource(R.string.layout_setting_checking_by_selection_description),
                    checked = viewModel.isCheckingBySelection,
                    onCheckedChange = { viewModel.isCheckingBySelection = it },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_hide_window_when_copying),
                    description = stringResource(R.string.layout_setting_is_hide_window_when_copying_description),
                    checked = viewModel.isHideWindowWhenCopying,
                    onCheckedChange = { viewModel.isHideWindowWhenCopying = it },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_saving_when_pausing),
                    description = stringResource(R.string.layout_setting_is_saving_when_pausing_description),
                    checked = viewModel.isSavingWhenPausing,
                    onCheckedChange = { viewModel.isSavingWhenPausing = it },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_crowed),
                    description = stringResource(R.string.layout_setting_is_crowed_description),
                    checked = viewModel.isCrowed,
                    onCheckedChange = { viewModel.isCrowed = it },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_show_error_reason),
                    description = stringResource(R.string.layout_setting_is_show_error_reason_description),
                    checked = viewModel.isShowErrorReason,
                    onCheckedChange = { viewModel.isShowErrorReason = it },
                )
                Divider()
                SettingsItem(
                    name = stringResource(R.string.layout_setting_is_syntax_highlight),
                    description = stringResource(R.string.layout_setting_is_syntax_highlight_description),
                    checked = viewModel.isSyntaxHighlight,
                    onCheckedChange = { viewModel.isSyntaxHighlight = it },
                )
            }
            // 测试设置
            CollectionName("测试设置")
            Collection {
                NameAndAction(
                    name = "自定义 API Base URL",
                    description = if (viewModel.customApiUrl.isEmpty()) "使用默认地址" else viewModel.customApiUrl,
                ) {
                    viewModel.isShowInputCustomApiUrlDialog = true
                }
            }
            // 实验性功能
            CollectionName("实验性功能")
            Collection {
                SettingsItem(
                    name = "显示公有命令库入口",
                    description = "在主页显示公有命令库入口（仍在开发中）",
                    checked = viewModel.isShowPublicLibrary,
                    onCheckedChange = { viewModel.isShowPublicLibrary = it }
                )
            }
        }
    }
    if (viewModel.isShowInputCustomApiUrlDialog) {
        val textFieldState = rememberTextFieldState(
            initialText = viewModel.customApiUrl
        )
        InputStringDialog(
            onDismissRequest = { viewModel.isShowInputCustomApiUrlDialog = false },
            title = "请输入 API Base URL",
            textFieldState = textFieldState,
            onConfirm = {
                viewModel.customApiUrl = textFieldState.text.toString().trim()
            }
        )
    }
    if (viewModel.isShowChooseThemeDialog) {
        val data = remember {
            arrayOf(
                "浅色模式" to "MODE_NIGHT_NO",
                "深色模式" to "MODE_NIGHT_YES",
                "跟随系统" to "MODE_NIGHT_FOLLOW_SYSTEM",
            )
        }
        ChoosingDialog(
            onDismissRequest = { viewModel.isShowChooseThemeDialog = false },
            data = data,
            onChoose = {
                viewModel.setThemeId(it)
            })
    }
    if (viewModel.isShowResumeBackgroundDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowResumeBackgroundDialog = false },
            content = "是否恢复背景？",
            onConfirm = {
                restoreBackground()
            }
        )
    }
    if (viewModel.isShowInputFloatingWindowSizeDialog) {
        val textFieldState = rememberTextFieldState(
            initialText = viewModel.floatingWindowSize.toString()
        )
        InputStringDialog(
            onDismissRequest = { viewModel.isShowInputFloatingWindowSizeDialog = false },
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
                    viewModel.floatingWindowSize = integer
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (viewModel.isShowInputFloatingWindowAlphaDialog) {
        val textFieldState = rememberTextFieldState(
            initialText = (viewModel.floatingWindowAlpha * 100).toInt().toString()
        )
        InputStringDialog(
            onDismissRequest = { viewModel.isShowInputFloatingWindowAlphaDialog = false },
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
                    viewModel.floatingWindowAlpha = integer / 100f
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (viewModel.isShowChooseCpackBranchDialog) {
        val data = remember {
            val result = mutableListOf<Pair<String, String>>()
            for (i in 0 until viewModel.cpackBranches.size) {
                result.add(viewModel.cpackBranchTranslations[i] to viewModel.cpackBranches[i])
            }
            result.toTypedArray()
        }
        ChoosingDialog(
            onDismissRequest = { viewModel.isShowChooseCpackBranchDialog = false },
            data = data,
            onChoose = {
                viewModel.cpackBranch = it
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
