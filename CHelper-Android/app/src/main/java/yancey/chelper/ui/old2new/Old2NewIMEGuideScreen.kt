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

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.layout.Surface
import yancey.chelper.ui.common.widget.Button
import yancey.chelper.ui.common.widget.Text

@Composable
fun Old2NewIMEGuideScreen() {
    val context = LocalContext.current
    RootViewWithHeaderAndCopyright(stringResource(R.string.layout_old2new_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(15.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
            ) {
                Text(text = stringResource(R.string.layout_old2new_ime_guide_guide))
            }
            Spacer(modifier = Modifier.height(15.dp))
            Button(stringResource(R.string.layout_old2new_ime_guide_step1)) {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            Button(stringResource(R.string.layout_old2new_ime_guide_step2)) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
    }
}

@Preview
@Composable
fun Old2NewIMEGuideLightThemePreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        Old2NewIMEGuideScreen()
    }
}

@Preview
@Composable
fun Old2NewIMEGuideDarkThemePreview() {
    CHelperTheme(theme = CHelperTheme.Theme.Dark, backgroundBitmap = null) {
        Old2NewIMEGuideScreen()
    }
}