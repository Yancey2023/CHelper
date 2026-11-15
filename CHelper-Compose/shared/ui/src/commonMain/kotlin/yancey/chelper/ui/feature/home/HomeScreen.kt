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

package yancey.chelper.ui.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.data.policy.PolicyGrantManager
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.dialog.PolicyGrantDialog
import yancey.chelper.ui.navigation.AboutScreenKey
import yancey.chelper.ui.navigation.CompletionScreenKey
import yancey.chelper.ui.navigation.EnumerationScreenKey
import yancey.chelper.ui.navigation.LibraryMainScreenKey
import yancey.chelper.ui.navigation.Old2NewIMEGuideScreenKey
import yancey.chelper.ui.navigation.Old2NewScreenKey
import yancey.chelper.ui.navigation.RawtextScreenKey
import yancey.chelper.ui.navigation.SettingsScreenKey
import yancey.chelper.ui.navigation.ShowTextScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.FloatingWindowController as FloatingWindowManager
import yancey.chelper.ui.platform.LocalClientServices as LocalContext
import yancey.chelper.ui.platform.rememberOpenUrl

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = rememberHomeViewModel(),
    navController: NavHostController = rememberNavController(),
    floatingWindowManager: FloatingWindowManager? = LocalContext.current.platform.floatingWindow,
) {
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val settingsDataStore = remember(context) { context.settings }
    val isShowPublicLibrary by
        settingsDataStore.isShowPublicLibrary().collectAsStateWithLifecycle(initialValue = false)
    val floatingWindowIconSize by
        settingsDataStore.floatingWindowIconSize().collectAsStateWithLifecycle(initialValue = 40)
    val floatingWindowIconAlpha by
        settingsDataStore.floatingWindowIconAlpha().collectAsStateWithLifecycle(initialValue = 1.0f)
    val floatingWindowScreenAlpha by
        settingsDataStore
            .floatingWindowScreenAlpha()
            .collectAsStateWithLifecycle(initialValue = 1.0f)
    val isFloatingWindowFontAlphaSync by
        settingsDataStore
            .isFloatingWindowFontAlphaSync()
            .collectAsStateWithLifecycle(initialValue = true)
    val publicLibraryMinVersion by
        settingsDataStore.publicLibraryMinVersion().collectAsStateWithLifecycle(initialValue = 0)

    DisposableEffect(viewModel.pendingAnnouncementUrl) {
        viewModel.pendingAnnouncementUrl?.let { url ->
            viewModel.pendingAnnouncementUrl = null
            openUrl(url)
        }
        onDispose {}
    }

    DisposableEffect(viewModel.announcementLinkMessage) {
        viewModel.announcementLinkMessage?.let { message ->
            viewModel.announcementLinkMessage = null
            ClientMessages.show(message)
        }
        onDispose {}
    }

    HomeContent(context.platform.homeActions) { action ->
        when (action) {
            HomeAction.Completion ->
                if (viewModel.isUsingFloatingWindow(floatingWindowManager))
                    ClientMessages.show("你必须关闭悬浮窗模式才可以进入应用模式")
                else navController.navigate(CompletionScreenKey)
            HomeAction.FloatingWindow ->
                if (viewModel.isUsingFloatingWindow(floatingWindowManager))
                    viewModel.stopFloatingWindow(floatingWindowManager)
                else
                    viewModel.startFloatingWindow(
                        context,
                        false,
                        floatingWindowIconSize,
                        floatingWindowIconAlpha,
                        floatingWindowScreenAlpha,
                        isFloatingWindowFontAlphaSync,
                        floatingWindowManager,
                    )
            HomeAction.Settings -> navController.navigate(SettingsScreenKey)
            HomeAction.Old2New -> navController.navigate(Old2NewScreenKey)
            HomeAction.Old2NewIMEGuide -> navController.navigate(Old2NewIMEGuideScreenKey)
            HomeAction.Enumeration -> navController.navigate(EnumerationScreenKey)
            HomeAction.LibraryMain ->
                if (isShowPublicLibrary)
                    viewModel.checkCommandLabVersion(publicLibraryMinVersion) {
                        navController.navigate(LibraryMainScreenKey)
                    }
                else navController.navigate(LibraryMainScreenKey)
            HomeAction.LoongFlow ->
                if (!context.platform.loongFlowPermissionGranted())
                    viewModel.isShowPermissionRequestWindow = true
                else context.platform.showLoongFlowExport()
            HomeAction.Rawtext -> navController.navigate(RawtextScreenKey)
            HomeAction.About -> navController.navigate(AboutScreenKey)
        }
    }
    if (viewModel.isShowPermissionRequestWindow) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowPermissionRequestWindow = false },
            content = "需要悬浮窗权限，请进入设置进行授权",
            confirmText = "打开设置",
            onConfirm = {
                context.platform.requestLoongFlowPermission { granted ->
                    if (granted) {
                        ClientMessages.show("悬浮窗权限获取成功")
                    } else {
                        ClientMessages.show("悬浮窗权限获取失败")
                    }
                }
            },
            onCancel = { viewModel.isShowPermissionRequestWindow = false },
        )
    }
    if (viewModel.isShowXiaomiClipboardPermissionTips) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowXiaomiClipboardPermissionTips = false },
            content =
                "对于小米手机和红米手机，需要将写入剪切板权限设置为始终允许才能在悬浮窗复制文本。具体设置方式如下：设置-应用设置-权限管理-应用权限管理-CHelper-写入剪切板-始终允许。",
            cancelText = "不再提示",
            onCancel = {
                viewModel.dismissShowXiaomiClipboardPermissionTipsForever()
                viewModel.startFloatingWindow(
                    context,
                    true,
                    floatingWindowIconSize,
                    floatingWindowIconAlpha,
                    floatingWindowScreenAlpha,
                    isFloatingWindowFontAlphaSync,
                    floatingWindowManager,
                )
            },
            onConfirm = {
                viewModel.startFloatingWindow(
                    context,
                    true,
                    floatingWindowIconSize,
                    floatingWindowIconAlpha,
                    floatingWindowScreenAlpha,
                    isFloatingWindowFontAlphaSync,
                    floatingWindowManager,
                )
            },
        )
    }
    if (viewModel.isShowPolicyGrantDialog) {
        val policyPageTitle = stringResource(Res.string.layout_about_privacy_policy)
        PolicyGrantDialog(
            content =
                if (viewModel.policyGrantState == PolicyGrantManager.State.NOT_READ)
                    stringResource(Res.string.dialog_policy_grant_message_if_unread)
                else stringResource(Res.string.dialog_policy_grant_message_if_updated),
            readPolicy = {
                navController.navigate(
                    ShowTextScreenKey(policyPageTitle, "about/privacy_policy.txt")
                )
            },
            onConfirm = { viewModel.agreePolicy() },
        )
    }
    if (viewModel.isShowAnnouncementDialog) {
        IsConfirmDialog(
            onDismissRequest = {
                viewModel.isShowAnnouncementDialog = false
                viewModel.checkUpdate()
            },
            title = viewModel.announcement!!.title!!,
            content = viewModel.announcement!!.message!!,
            cancelText = if (viewModel.announcement!!.isForce!!) "取消" else "不再提醒",
            onCancel = {
                if (!viewModel.announcement!!.isForce!!) {
                    viewModel.ignoreCurrentAnnouncement()
                }
            },
            contentLinksEnabled = true,
            onContentLinkClick = viewModel::openAnnouncementLink,
        )
    }
    if (viewModel.isShowCommandLabVersionDialog) {
        IsConfirmDialog(
            onDismissRequest = { viewModel.dismissCommandLabVersionDialog() },
            title = "版本提示",
            content = "公有命令库需要最新版软件才能使用，请下载最新版软件体验完整功能。",
        )
    }
    if (viewModel.isShowUpdateNotificationsDialog) {
        val content =
            remember(viewModel.latestVersionInfo) {
                viewModel.latestVersionInfo!!.versionName +
                    "版本已发布，欢迎下载体验。本次更新内容如下：\n" +
                    viewModel.latestVersionInfo!!.changelog
            }
        IsConfirmDialog(
            onDismissRequest = { viewModel.isShowUpdateNotificationsDialog = false },
            title = "更新提醒",
            content = content,
            cancelText = "忽略此版本",
            onCancel = { viewModel.ignoreLatestVersion() },
        )
    }
}

@Composable
private fun rememberHomeViewModel(): HomeViewModel {
    val services = LocalContext.current
    return viewModel { HomeViewModel(services) }
}
