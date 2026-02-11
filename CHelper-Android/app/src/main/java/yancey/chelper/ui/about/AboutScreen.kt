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

package yancey.chelper.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import yancey.chelper.BuildConfig
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.Collection
import yancey.chelper.ui.common.layout.NameAndAsset
import yancey.chelper.ui.common.layout.NameAndLink
import yancey.chelper.ui.common.layout.NameAndValue
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Divider

@Composable
fun AboutScreen(navController: NavHostController) {
    RootViewWithHeaderAndCopyright(stringResource(R.string.layout_about_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(15.dp))
            Collection {
                NameAndValue(
                    stringResource(R.string.layout_about_current_version),
                    BuildConfig.VERSION_NAME
                )
                Divider()
                NameAndValue(stringResource(R.string.layout_about_author), "Yancey")
                Divider()
                NameAndValue(stringResource(R.string.layout_about_qq_personal), "1709185482")
                Divider()
                NameAndValue(stringResource(R.string.layout_about_qq_group), "766625597")
                Divider()
                NameAndLink(
                    stringResource(R.string.layout_about_bilibili),
                    "https://space.bilibili.com/470179011".toUri()
                )
                Divider()
                NameAndLink(
                    stringResource(R.string.layout_about_source_code),
                    "https://github.com/Yancey2023/CHelper".toUri()
                )
            }
            Spacer(Modifier.height(15.dp))
            Collection {
                NameAndAsset(
                    navController,
                    stringResource(R.string.layout_about_release_note),
                    "about/release_note.txt"
                )
                Divider()
                NameAndLink(
                    stringResource(R.string.layout_about_latest_release_note),
                    "https://www.yanceymc.cn/chelper_doc/chelper-release-notes".toUri()
                )
                Divider()
                NameAndLink(
                    stringResource(R.string.layout_about_issue),
                    "https://www.yanceymc.cn/gitea/Yancey/CHelper/issues".toUri()
                )
                Divider()
                NameAndLink(
                    stringResource(R.string.layout_about_donate),
                    "https://www.yanceymc.cn/chelper_doc/donate".toUri()
                )
                Divider()
                NameAndAsset(
                    navController,
                    stringResource(R.string.layout_about_privacy_policy),
                    "about/privacy_policy.txt"
                )
                Divider()
                NameAndAsset(
                    navController,
                    stringResource(R.string.layout_about_open_source_terms),
                    "about/open_source_terms.txt"
                )
            }
        }
    }
}

@Preview
@Composable
fun AboutScreenLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        AboutScreen(rememberNavController())
    }
}

@Preview
@Composable
fun AboutScreenDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        AboutScreen(rememberNavController())
    }
}
