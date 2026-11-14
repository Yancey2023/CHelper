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

package yancey.chelper.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.domain.settings.Settings
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.dialog.InputStringDialog
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.dialog.SelectionDialog
import yancey.chelper.ui.designsystem.layout.Collection
import yancey.chelper.ui.designsystem.layout.CollectionName
import yancey.chelper.ui.designsystem.layout.NameAndAction
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.layout.SettingsItem
import yancey.chelper.ui.designsystem.widget.Divider

@Composable
fun SettingsContent(
    settings: Settings,
    cpackBranchesWithTranslate: Array<Pair<String, String>>,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    floatingWindowSettings: Boolean = true,
    loongFlowSettings: Boolean = floatingWindowSettings,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    var isShowResumeBackgroundDialog by remember { mutableStateOf(false) }
    var isShowChooseThemeDialog by remember { mutableStateOf(false) }
    var isShowInputFloatingWindowIconAlphaDialog by remember { mutableStateOf(false) }
    var isShowInputFloatingWindowScreenAlphaDialog by remember { mutableStateOf(false) }
    var isShowInputFloatingWindowIconSizeDialog by remember { mutableStateOf(false) }
    var isShowChooseCpackBranchDialog by remember { mutableStateOf(false) }
    var isShowChooseTagClickDialog by remember { mutableStateOf(false) }
    var isShowChooseAmbiguousLineDialog by remember { mutableStateOf(false) }
    var isShowChooseLibraryHomeRecommendDialog by remember { mutableStateOf(false) }
    var isShowInputSyntaxHighlightMaxLengthDialog by remember { mutableStateOf(false) }
    val isEnableUpdateNotifications = settings.isEnableUpdateNotifications ?: true
    val themeId = settings.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM"
    val cpackBranch = settings.cpackBranch ?: "release-experiment"
    val isCheckingBySelection = settings.isCheckingBySelection ?: true
    val isHideWindowWhenCopying = settings.isHideWindowWhenCopying ?: false
    val isSavingWhenPausing = settings.isSavingWhenPausing ?: true
    val isCrowded = settings.isCrowded ?: false
    val isShowErrorReason = settings.isShowErrorReason ?: true
    val isSyntaxHighlight = settings.isSyntaxHighlight ?: true
    val floatingWindowIconAlpha = settings.floatingWindowAlpha ?: 1.0f
    val floatingWindowScreenAlpha = settings.floatingWindowScreenAlpha ?: 1.0f
    val floatingWindowIconSize = settings.floatingWindowSize ?: 40
    val isFloatingWindowFontAlphaSync = settings.isFloatingWindowFontAlphaSync ?: true
    val isHideMetadataPreview = settings.isHideMetadataPreview ?: false
    val isEnableMcdHighlight = settings.isEnableMcdHighlight ?: true
    val isEnableLoongFlowImportMiniIcon = settings.isEnableLoongFlowImportMiniIcon ?: true
    val isPublicLibraryHomeRecommend = settings.publicLibraryHomeRecommend ?: true
    val syntaxHighlightMaxLength = settings.syntaxHighlightMaxLength ?: 4000
    val tagClickBehavior = settings.tagClickBehavior ?: "search"
    val ambiguousLineDefault = settings.ambiguousLineDefault ?: "comment"
    RootViewWithHeaderAndCopyright(stringResource(Res.string.layout_settings_title)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (error != null) {
                Collection { NameAndAction("设置保存或读取失败，点击重试", error, onClick = onRetry) }
            }
            CollectionName(stringResource(Res.string.layout_settings_application_update))
            Collection {
                SettingsItem(
                    name = stringResource(Res.string.layout_settings_is_enable_update_notification),
                    description =
                        stringResource(
                            Res.string.layout_settings_is_enable_update_notification_description
                        ),
                    checked = isEnableUpdateNotifications,
                    onCheckedChange = {
                        run {
                            onChange { current -> current.copy(isEnableUpdateNotifications = it) }
                        }
                    },
                )
            }
            CollectionName(stringResource(Res.string.layout_settings_theme_settings))
            Collection {
                NameAndAction(
                    name = stringResource(Res.string.layout_settings_choose_background),
                    description =
                        stringResource(Res.string.layout_settings_choose_background_description),
                ) {
                    chooseBackground()
                }
                Divider()
                NameAndAction(
                    name = stringResource(Res.string.layout_settings_restore_background),
                    description =
                        stringResource(Res.string.layout_settings_restore_background_description),
                ) {
                    isShowResumeBackgroundDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(Res.string.layout_settings_choose_theme),
                    description =
                        stringResource(Res.string.layout_settings_choose_theme_description),
                ) {
                    isShowChooseThemeDialog = true
                }
                if (floatingWindowSettings) {
                    Divider()
                    NameAndAction(
                        name =
                            stringResource(Res.string.layout_settings_floating_window_icon_alpha),
                        description =
                            stringResource(
                                Res.string.layout_settings_floating_window_icon_alpha_description
                            ),
                    ) {
                        isShowInputFloatingWindowIconAlphaDialog = true
                    }
                    Divider()
                    NameAndAction(
                        name =
                            stringResource(Res.string.layout_settings_floating_window_screen_alpha),
                        description =
                            stringResource(
                                Res.string.layout_settings_floating_window_screen_alpha_description
                            ),
                    ) {
                        isShowInputFloatingWindowScreenAlphaDialog = true
                    }
                    Divider()
                    SettingsItem(
                        name = "悬浮窗字体是否跟随透明",
                        description = "开启后字体随窗口一同透明；关闭后仅背景透明，字体保持清晰",
                        checked = isFloatingWindowFontAlphaSync,
                        onCheckedChange = {
                            run {
                                onChange { current ->
                                    current.copy(isFloatingWindowFontAlphaSync = it)
                                }
                            }
                        },
                    )
                    Divider()
                    NameAndAction(
                        name = stringResource(Res.string.layout_settings_floating_window_icon_size),
                        description =
                            stringResource(
                                Res.string.layout_settings_floating_window_icon_size_description
                            ),
                    ) {
                        isShowInputFloatingWindowIconSizeDialog = true
                    }
                }
            }
            CollectionName(stringResource(Res.string.layout_settings_completion_settings))
            Collection {
                val currentCpackBranchTranslation =
                    remember(cpackBranch, cpackBranchesWithTranslate) {
                        for (pair in cpackBranchesWithTranslate) {
                            if (cpackBranch == pair.second) {
                                return@remember pair.first
                            }
                        }
                        return@remember cpackBranch
                    }
                NameAndAction(
                    name = stringResource(Res.string.layout_settings_choose_cpack),
                    description =
                        stringResource(
                            Res.string.layout_settings_current_cpack,
                            currentCpackBranchTranslation
                                ?: stringResource(Res.string.layout_settings_unknown_branch),
                        ),
                ) {
                    isShowChooseCpackBranchDialog = true
                }
                Divider()
                SettingsItem(
                    name = stringResource(Res.string.layout_setting_checking_by_selection),
                    description =
                        stringResource(Res.string.layout_setting_checking_by_selection_description),
                    checked = isCheckingBySelection,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isCheckingBySelection = it) } }
                    },
                )
                if (floatingWindowSettings) {
                    Divider()
                    SettingsItem(
                        name =
                            stringResource(Res.string.layout_setting_is_hide_window_when_copying),
                        description =
                            stringResource(
                                Res.string.layout_setting_is_hide_window_when_copying_description
                            ),
                        checked = isHideWindowWhenCopying,
                        onCheckedChange = {
                            run {
                                onChange { current -> current.copy(isHideWindowWhenCopying = it) }
                            }
                        },
                    )
                }
                Divider()
                SettingsItem(
                    name = stringResource(Res.string.layout_setting_is_saving_when_pausing),
                    description =
                        stringResource(
                            Res.string.layout_setting_is_saving_when_pausing_description
                        ),
                    checked = isSavingWhenPausing,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isSavingWhenPausing = it) } }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(Res.string.layout_setting_is_crowed),
                    description = stringResource(Res.string.layout_setting_is_crowed_description),
                    checked = isCrowded,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isCrowded = it) } }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(Res.string.layout_setting_is_show_error_reason),
                    description =
                        stringResource(Res.string.layout_setting_is_show_error_reason_description),
                    checked = isShowErrorReason,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isShowErrorReason = it) } }
                    },
                )
                Divider()
                SettingsItem(
                    name = stringResource(Res.string.layout_setting_is_syntax_highlight),
                    description =
                        stringResource(Res.string.layout_setting_is_syntax_highlight_description),
                    checked = isSyntaxHighlight,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isSyntaxHighlight = it) } }
                    },
                )
                Divider()
                NameAndAction(
                    name = "高亮自动关闭阈值",
                    description = "当前限制: ${syntaxHighlightMaxLength ?: 4000} 字符 (为防卡死)",
                ) {
                    isShowInputSyntaxHighlightMaxLengthDialog = true
                }
            }
            CollectionName("命令库设置")
            Collection {
                NameAndAction(
                    name = "命令库默认主页流",
                    description = "当前: ${if (isPublicLibraryHomeRecommend) "猜你喜欢" else "按时间最新发布"}",
                ) {
                    isShowChooseLibraryHomeRecommendDialog = true
                }
                Divider()
                NameAndAction(
                    name = "Tag 点击行为",
                    description = "当前: ${if (tagClickBehavior == "search") "搜索该 Tag" else "进入详情页"}",
                ) {
                    isShowChooseTagClickDialog = true
                }
                Divider()
                NameAndAction(
                    name = "无法推断行的默认处理",
                    description = "当前: ${if (ambiguousLineDefault == "comment") "当作注释" else "当作指令"}",
                ) {
                    isShowChooseAmbiguousLineDialog = true
                }
                Divider()
                SettingsItem(
                    name = "隐藏正文元数据预览",
                    description = "隐藏 MCD 可视化中 @name、@version 等元信息区",
                    checked = isHideMetadataPreview,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isHideMetadataPreview = it) } }
                    },
                )
                Divider()
                SettingsItem(
                    name = "[实验性] 命令块语法高亮",
                    description = "支持命令库在 v2 渲染时为命令块附加语法高亮",
                    checked = isEnableMcdHighlight,
                    onCheckedChange = {
                        run { onChange { current -> current.copy(isEnableMcdHighlight = it) } }
                    },
                )
                if (loongFlowSettings) {
                    Divider()
                    SettingsItem(
                        name = "是否启用小图标模式",
                        description = "开启后游龙开始导入会自动收起为命令类型小图标；关闭后保留原大窗口流程",
                        checked = isEnableLoongFlowImportMiniIcon,
                        onCheckedChange = {
                            run {
                                onChange { current ->
                                    current.copy(isEnableLoongFlowImportMiniIcon = it)
                                }
                            }
                        },
                    )
                }
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
        SelectionDialog(
            title = "选择主题",
            initialValue = themeId,
            onDismissRequest = { isShowChooseThemeDialog = false },
            data = data,
            onChoose = { run { onChange { current -> current.copy(themeId = it) } } },
        )
    }
    if (isShowResumeBackgroundDialog) {
        IsConfirmDialog(
            onDismissRequest = { isShowResumeBackgroundDialog = false },
            content = "是否恢复背景？",
            onConfirm = { restoreBackground() },
        )
    }
    if (isShowInputFloatingWindowIconSizeDialog) {
        val textFieldState = rememberTextFieldState(initialText = floatingWindowIconSize.toString())
        InputStringDialog(
            onDismissRequest = { isShowInputFloatingWindowIconSizeDialog = false },
            title = "请输入悬浮窗图标大小",
            textFieldState = textFieldState,
            onConfirm = {
                try {
                    var integer = textFieldState.text.toString().toInt()
                    if (integer < 10) {
                        integer = 10
                    } else if (integer > 100) {
                        integer = 100
                    }
                    run { onChange { current -> current.copy(floatingWindowSize = integer) } }
                } catch (_: NumberFormatException) {}
            },
        )
    }
    if (isShowInputFloatingWindowIconAlphaDialog) {
        val textFieldState =
            rememberTextFieldState(initialText = (floatingWindowIconAlpha * 100).toInt().toString())
        InputStringDialog(
            onDismissRequest = { isShowInputFloatingWindowIconAlphaDialog = false },
            title = "请输入图标透明度",
            textFieldState = textFieldState,
            onConfirm = {
                try {
                    var integer = textFieldState.text.toString().toInt()
                    if (integer < 10) {
                        integer = 10
                    } else if (integer > 100) {
                        integer = 100
                    }
                    run {
                        onChange { current -> current.copy(floatingWindowAlpha = integer / 100f) }
                    }
                } catch (_: NumberFormatException) {}
            },
        )
    }
    if (isShowInputFloatingWindowScreenAlphaDialog) {
        val textFieldState =
            rememberTextFieldState(
                initialText = (floatingWindowScreenAlpha * 100).toInt().toString()
            )
        InputStringDialog(
            onDismissRequest = { isShowInputFloatingWindowScreenAlphaDialog = false },
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
                    run {
                        onChange { current ->
                            current.copy(floatingWindowScreenAlpha = integer / 100f)
                        }
                    }
                } catch (_: NumberFormatException) {}
            },
        )
    }
    if (isShowChooseCpackBranchDialog) {
        SelectionDialog(
            title = "选择命令分支",
            initialValue = cpackBranch,
            onDismissRequest = { isShowChooseCpackBranchDialog = false },
            data = cpackBranchesWithTranslate,
            onChoose = { run { onChange { current -> current.copy(cpackBranch = it) } } },
        )
    }
    if (isShowChooseTagClickDialog) {
        SelectionDialog(
            title = "选择 Tag 点击行为",
            initialValue = tagClickBehavior,
            onDismissRequest = { isShowChooseTagClickDialog = false },
            data = arrayOf("搜索该 Tag" to "search", "进入详情页" to "detail"),
            onChoose = { run { onChange { current -> current.copy(tagClickBehavior = it) } } },
        )
    }
    if (isShowChooseAmbiguousLineDialog) {
        SelectionDialog(
            title = "选择模糊行处理方式",
            initialValue = ambiguousLineDefault,
            onDismissRequest = { isShowChooseAmbiguousLineDialog = false },
            data = arrayOf("当作注释" to "comment", "当作指令" to "command"),
            onChoose = { run { onChange { current -> current.copy(ambiguousLineDefault = it) } } },
        )
    }
    if (isShowChooseLibraryHomeRecommendDialog) {
        SelectionDialog(
            title = "选择云端库首页推荐方式",
            initialValue = if (isPublicLibraryHomeRecommend) "true" else "false",
            onDismissRequest = { isShowChooseLibraryHomeRecommendDialog = false },
            data = arrayOf("猜你喜欢" to "true", "按时间最新发布" to "false"),
            onChoose = {
                run {
                    onChange { current -> current.copy(publicLibraryHomeRecommend = it == "true") }
                }
            },
        )
    }
    if (isShowInputSyntaxHighlightMaxLengthDialog) {
        val textFieldState =
            rememberTextFieldState(initialText = syntaxHighlightMaxLength.toString())
        InputStringDialog(
            onDismissRequest = { isShowInputSyntaxHighlightMaxLengthDialog = false },
            title = "请输入高亮自动关闭阈值 (0-100000)",
            textFieldState = textFieldState,
            onConfirm = {
                try {
                    var integer = textFieldState.text.toString().toInt()
                    if (integer < 0) {
                        integer = 0
                    } else if (integer > 100000) {
                        integer = 100000
                    }
                    run { onChange { current -> current.copy(syntaxHighlightMaxLength = integer) } }
                } catch (_: NumberFormatException) {}
            },
        )
    }
}
