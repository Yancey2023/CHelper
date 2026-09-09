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

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.R
import yancey.chelper.core.MainPackProvider
import yancey.chelper.data.SettingsDataStore
import yancey.chelper.ui.PackManagerScreenKey
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.InputStringDialog
import yancey.chelper.ui.common.dialog.IsConfirmDialog
import yancey.chelper.ui.common.dialog.SelectionDialog
import yancey.chelper.ui.common.layout.Collection
import yancey.chelper.ui.common.layout.CollectionName
import yancey.chelper.ui.common.layout.NameAndAction
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.SettingsItem
import yancey.chelper.ui.common.widget.Divider

@Composable
fun SettingsScreen(
    navController: NavHostController? = null,
    chooseBackground: () -> Unit,
    restoreBackground: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
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
    val isEnableUpdateNotifications by settingsDataStore.isEnableUpdateNotifications()
        .collectAsState(initial = null)
    val themeId by settingsDataStore.themeId()
        .collectAsState(initial = "MODE_NIGHT_FOLLOW_SYSTEM")
    val cpackBranch by settingsDataStore.cpackBranch()
        .collectAsState(initial = null)
    val isCheckingBySelection by settingsDataStore.isCheckingBySelection()
        .collectAsState(initial = null)
    val isHideWindowWhenCopying by settingsDataStore.isHideWindowWhenCopying()
        .collectAsState(initial = null)
    val isSavingWhenPausing by settingsDataStore.isSavingWhenPausing()
        .collectAsState(initial = null)
    val isCrowded by settingsDataStore.isCrowded()
        .collectAsState(initial = null)
    val isShowErrorReason by settingsDataStore.isShowErrorReason()
        .collectAsState(initial = null)
    val isSyntaxHighlight by settingsDataStore.isSyntaxHighlight()
        .collectAsState(initial = null)
    val floatingWindowIconAlpha by settingsDataStore.floatingWindowIconAlpha()
        .collectAsState(initial = null)
    val floatingWindowScreenAlpha by settingsDataStore.floatingWindowScreenAlpha()
        .collectAsState(initial = null)
    val floatingWindowIconSize by settingsDataStore.floatingWindowIconSize()
        .collectAsState(initial = null)
    val isFloatingWindowFontAlphaSync by settingsDataStore.isFloatingWindowFontAlphaSync()
        .collectAsState(initial = null)
    var tagClickBehavior by remember { mutableStateOf("search") }
    var ambiguousLineDefault by remember { mutableStateOf("comment") }
    val isHideMetadataPreview by settingsDataStore.isHideMetadataPreview()
        .collectAsState(initial = false)
    val isEnableMcdHighlight by settingsDataStore.isEnableMcdHighlight()
        .collectAsState(initial = true)
    val isEnableLoongFlowImportMiniIcon by settingsDataStore.isEnableLoongFlowImportMiniIcon()
        .collectAsState(initial = true)
    val isPublicLibraryHomeRecommend by settingsDataStore.isPublicLibraryHomeRecommend()
        .collectAsState(initial = true)
    val syntaxHighlightMaxLength by settingsDataStore.syntaxHighlightMaxLength()
        .collectAsState(initial = null)
    // DataStore flow -> 本地变量同步（首次加载时拿到持久化值）
    val tagClickBehaviorFlow by settingsDataStore.tagClickBehavior()
        .collectAsState(initial = "search")
    val ambiguousLineDefaultFlow by settingsDataStore.ambiguousLineDefault()
        .collectAsState(initial = "comment")
    SideEffect {
        if (tagClickBehavior != tagClickBehaviorFlow) {
            tagClickBehavior = tagClickBehaviorFlow
        }
        if (ambiguousLineDefault != ambiguousLineDefaultFlow) {
            ambiguousLineDefault = ambiguousLineDefaultFlow
        }
    }
    // 可选的版本/分支列表：由主包（数据包）manifest 的 segments 动态提供，
    // 数据包添加新段后这里自动出现，不在此硬编码。
    var segmentChoices by remember {
        mutableStateOf(emptyArray<Pair<String, String>>())
    }
    LaunchedEffect(context) {
        val pack = withContext(Dispatchers.IO) { MainPackProvider.get(context) }
        segmentChoices = pack?.segments.orEmpty().map { segment ->
            val label = if (segment.version.isBlank()) {
                segment.name.ifBlank { segment.id }
            } else {
                "${segment.name.ifBlank { segment.id }}（${segment.version}）"
            }
            label to segment.id
        }.toTypedArray()
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
                    name = stringResource(R.string.layout_settings_floating_window_icon_alpha),
                    description = stringResource(R.string.layout_settings_floating_window_icon_alpha_description),
                ) {
                    isShowInputFloatingWindowIconAlphaDialog = true
                }
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_screen_alpha),
                    description = stringResource(R.string.layout_settings_floating_window_screen_alpha_description),
                ) {
                    isShowInputFloatingWindowScreenAlphaDialog = true
                }
                Divider()
                SettingsItem(
                    name = "悬浮窗字体是否跟随透明",
                    description = "开启后字体随窗口一同透明；关闭后仅背景透明，字体保持清晰",
                    checked = isFloatingWindowFontAlphaSync,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsFloatingWindowFontAlphaSync(it)
                        }
                    },
                )
                Divider()
                NameAndAction(
                    name = stringResource(R.string.layout_settings_floating_window_icon_size),
                    description = stringResource(R.string.layout_settings_floating_window_icon_size_description),
                ) {
                    isShowInputFloatingWindowIconSizeDialog = true
                }
            }
            CollectionName("资源包")
            Collection {
                NameAndAction(
                    name = "资源包管理",
                    description = "内置主资源包（可切换命令分支）+ 拓展包导入/启停/删除/排序，列表靠上优先",
                ) {
                    navController?.navigate(PackManagerScreenKey)
                }
            }
            CollectionName(stringResource(R.string.layout_settings_completion_settings))
            Collection {
                val currentCpackBranchTranslation =
                    remember(cpackBranch, segmentChoices) {
                        val normalized = normalizeSegmentId(cpackBranch)
                        for (pair in segmentChoices) {
                            if (pair.second == normalized) {
                                return@remember pair.first
                            }
                        }
                        return@remember normalized
                    }
                NameAndAction(
                    name = stringResource(R.string.layout_settings_choose_cpack),
                    description = stringResource(
                        R.string.layout_settings_current_cpack,
                        currentCpackBranchTranslation
                            ?: stringResource(R.string.layout_settings_unknown_branch)
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
                        coroutineScope.launch {
                            settingsDataStore.setIsHideMetadataPreview(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = "[实验性] 命令块语法高亮",
                    description = "支持命令库在 v2 渲染时为命令块附加语法高亮",
                    checked = isEnableMcdHighlight,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsEnableMcdHighlight(it)
                        }
                    },
                )
                Divider()
                SettingsItem(
                    name = "是否启用小图标模式",
                    description = "开启后游龙开始导入会自动收起为命令类型小图标；关闭后保留原大窗口流程",
                    checked = isEnableLoongFlowImportMiniIcon,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsDataStore.setIsEnableLoongFlowImportMiniIcon(it)
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
        SelectionDialog(
            title = "选择主题",
            initialValue = themeId,
            onDismissRequest = { isShowChooseThemeDialog = false },
            data = data,
            onChoose = {
                coroutineScope.launch {
                    settingsDataStore.setThemeId(it)
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
    if (isShowInputFloatingWindowIconSizeDialog && floatingWindowIconSize != null) {
        val textFieldState = rememberTextFieldState(
            initialText = floatingWindowIconSize!!.toString()
        )
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
                    coroutineScope.launch {
                        settingsDataStore.setFloatingWindowIconSize(integer)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (isShowInputFloatingWindowIconAlphaDialog && floatingWindowIconAlpha != null) {
        val textFieldState = rememberTextFieldState(
            initialText = (floatingWindowIconAlpha!! * 100).toInt().toString()
        )
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
                    coroutineScope.launch {
                        settingsDataStore.setFloatingWindowIconAlpha(integer / 100f)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (isShowInputFloatingWindowScreenAlphaDialog && floatingWindowScreenAlpha != null) {
        val textFieldState = rememberTextFieldState(
            initialText = (floatingWindowScreenAlpha!! * 100).toInt().toString()
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
                    coroutineScope.launch {
                        settingsDataStore.setFloatingWindowScreenAlpha(integer / 100f)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
    }
    if (isShowChooseCpackBranchDialog) {
        SelectionDialog(
            title = "选择命令分支",
            initialValue = normalizeSegmentId(cpackBranch),
            onDismissRequest = { isShowChooseCpackBranchDialog = false },
            data = segmentChoices,
            onChoose = {
                coroutineScope.launch {
                    settingsDataStore.setCpackBranch(it)
                }
            })
    }
    if (isShowChooseTagClickDialog) {
        SelectionDialog(
            title = "选择 Tag 点击行为",
            initialValue = tagClickBehavior,
            onDismissRequest = { isShowChooseTagClickDialog = false },
            data = arrayOf(
                "搜索该 Tag" to "search",
                "进入详情页" to "detail"
            ),
            onChoose = {
                tagClickBehavior = it
                coroutineScope.launch {
                    settingsDataStore.setTagClickBehavior(it)
                }
            })
    }
    if (isShowChooseAmbiguousLineDialog) {
        SelectionDialog(
            title = "选择模糊行处理方式",
            initialValue = ambiguousLineDefault,
            onDismissRequest = { isShowChooseAmbiguousLineDialog = false },
            data = arrayOf(
                "当作注释" to "comment",
                "当作指令" to "command"
            ),
            onChoose = {
                ambiguousLineDefault = it
                coroutineScope.launch {
                    settingsDataStore.setAmbiguousLineDefault(it)
                }
            })
    }
    if (isShowChooseLibraryHomeRecommendDialog) {
        SelectionDialog(
            title = "选择云端库首页推荐方式",
            initialValue = if (isPublicLibraryHomeRecommend) "true" else "false",
            onDismissRequest = { isShowChooseLibraryHomeRecommendDialog = false },
            data = arrayOf(
                "猜你喜欢" to "true",
                "按时间最新发布" to "false"
            ),
            onChoose = {
                Log.d("CPL_Tab", "Settings: user chose isRecommend=$it")
                coroutineScope.launch {
                    settingsDataStore.setPublicLibraryHomeRecommend(it == "true")
                    Log.d(
                        "CPL_Tab",
                        "Settings: saved to DataStore isRecommend=${it == "true"}"
                    )
                }
            })
    }
    if (isShowInputSyntaxHighlightMaxLengthDialog && syntaxHighlightMaxLength != null) {
        val textFieldState = rememberTextFieldState(
            initialText = syntaxHighlightMaxLength!!.toString()
        )
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
                    coroutineScope.launch {
                        settingsDataStore.setSyntaxHighlightMaxLength(integer)
                    }
                } catch (_: NumberFormatException) {
                }
            }
        )
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
        )
    }
}

/**
 * 把旧设置值（如 "beta-vanilla"）归一化成主包段 id 形式（"beta/vanilla"）。
 * 数据包由 manifest 的 segments 提供段列表，id 一律为 "版本类型/分支"。
 */
private fun normalizeSegmentId(branch: String?): String? = branch?.replace('-', '/')
