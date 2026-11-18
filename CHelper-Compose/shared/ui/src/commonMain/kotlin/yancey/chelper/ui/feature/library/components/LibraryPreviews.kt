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

package yancey.chelper.ui.feature.library.components

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import yancey.chelper.domain.library.parseMCDStructure
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.library.mcd.MCDRowsContent
import yancey.chelper.ui.feature.library.mcd.flattenParsedMCD

@Preview
@Composable
fun LibraryDetailLightPreview() = LibraryDetailPreview(CHelperTheme.Theme.Light)

@Preview @Composable fun LibraryDetailDarkPreview() = LibraryDetailPreview(CHelperTheme.Theme.Dark)

@Composable
private fun LibraryDetailPreview(theme: CHelperTheme.Theme) {
    val source = "@name=示例命令库\n@version=1.0.0\n# 欢迎消息\nsay Hello\ntp @s 0 64 0"
    CHelperTheme(theme, null) {
        LibraryDetailContent(
            title = "示例命令库",
            resolvedSource = source,
            ambiguousDefault = "command",
            hideMetadata = false,
            onCopy = {},
            onMessage = {},
        ) { text, defaultType, metadata ->
            MCDRowsContent(
                rows = flattenParsedMCD(parseMCDStructure(text, defaultType), metadata),
                onCopy = {},
            )
        }
    }
}

@Preview
@Composable
fun LibraryVersionFieldsLightPreview() = LibraryVersionFieldsPreview(CHelperTheme.Theme.Light)

@Preview
@Composable
fun LibraryVersionFieldsDarkPreview() = LibraryVersionFieldsPreview(CHelperTheme.Theme.Dark)

@Composable
private fun LibraryVersionFieldsPreview(theme: CHelperTheme.Theme) {
    CHelperTheme(theme, null) {
        LibraryVersionFields(
            name = rememberTextFieldState("示例命令库"),
            description = rememberTextFieldState("常用命令与实用工具"),
            version = rememberTextFieldState("1.0.0"),
            tags = rememberTextFieldState("工具,示例"),
        )
    }
}
