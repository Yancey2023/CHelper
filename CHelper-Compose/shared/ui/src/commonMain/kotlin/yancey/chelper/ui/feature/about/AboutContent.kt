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

package yancey.chelper.ui.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.layout.Collection
import yancey.chelper.ui.designsystem.layout.NameAndAction
import yancey.chelper.ui.designsystem.layout.NameAndValue
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Divider

@Composable
fun AboutContent(version: String, openUrl: (String) -> Unit, openAsset: (String, String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    RootViewWithHeaderAndCopyright(stringResource(Res.string.layout_about_title)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(15.dp))
            Collection {
                NameAndValue(stringResource(Res.string.layout_about_current_version), version)
                Divider()
                NameAndValue(stringResource(Res.string.layout_about_author), "Yancey")
                Divider()
                NameAndValue(stringResource(Res.string.layout_about_qq_personal), "1709185482")
                Divider()
                NameAndValue(stringResource(Res.string.layout_about_qq_group), "766625597")
                Divider()
                AboutLink(
                    stringResource(Res.string.layout_about_bilibili),
                    "https://space.bilibili.com/470179011",
                    openUrl,
                )
                Divider()
                AboutLink(
                    stringResource(Res.string.layout_about_source_code),
                    "https://github.com/Yancey2023/CHelper",
                    openUrl,
                )
            }
            Spacer(Modifier.height(15.dp))
            Collection {
                AboutAsset(
                    stringResource(Res.string.layout_about_release_note),
                    "about/release_note.txt",
                    openAsset,
                )
                Divider()
                AboutLink(
                    stringResource(Res.string.layout_about_latest_release_note),
                    "https://www.yanceymc.cn/chelper_doc/chelper-release-notes",
                    openUrl,
                )
                Divider()
                AboutLink(
                    stringResource(Res.string.layout_about_issue),
                    "https://www.yanceymc.cn/gitea/Yancey/CHelper/issues",
                    openUrl,
                )
                Divider()
                AboutLink(
                    stringResource(Res.string.layout_about_donate),
                    "https://www.yanceymc.cn/chelper_doc/donate",
                    openUrl,
                )
                Divider()
                AboutAsset(
                    stringResource(Res.string.layout_about_privacy_policy),
                    "about/privacy_policy.txt",
                    openAsset,
                )
                Divider()
                AboutAsset(
                    stringResource(Res.string.layout_about_open_source_terms),
                    "about/open_source_terms.txt",
                    openAsset,
                )
            }
        }
    }
}

@Composable
private fun AboutLink(name: String, url: String, open: (String) -> Unit) {
    NameAndAction(name, iconId = Res.drawable.external_link) { open(url) }
}

@Composable
private fun AboutAsset(name: String, path: String, open: (String, String) -> Unit) {
    NameAndAction(name) { open(name, path) }
}
