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

package yancey.chelper.ui.old2new

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Button

@Composable
fun Old2NewIMEScreen(undo: () -> Unit) {
    Button(
        text = stringResource(R.string.layout_old2new_ime_undo),
        modifier = Modifier
            .background(CHelperTheme.colors.background)
            .padding(15.dp),
        onClick = undo
    )
}

@Preview
@Composable
fun Old2NewIMELightThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Light, backgroundBitmap = null) {
        Old2NewIMEScreen {}
    }
}

@Preview
@Composable
fun Old2NewIMEDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        Old2NewIMEScreen {}
    }
}