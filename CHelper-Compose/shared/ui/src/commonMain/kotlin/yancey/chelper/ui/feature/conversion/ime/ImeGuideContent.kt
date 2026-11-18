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

package yancey.chelper.ui.feature.conversion.ime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.layout.Surface
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun ImeGuideContent(onOpenSettings: () -> Unit, onChooseInputMethod: () -> Unit) {
    RootViewWithHeaderAndCopyright(stringResource(Res.string.layout_old2new_title)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(15.dp))
            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
                Text(text = stringResource(Res.string.layout_old2new_ime_guide_guide))
            }
            Spacer(modifier = Modifier.height(15.dp))
            Button(stringResource(Res.string.layout_old2new_ime_guide_step1)) { onOpenSettings() }
            Button(stringResource(Res.string.layout_old2new_ime_guide_step2)) {
                onChooseInputMethod()
            }
        }
    }
}
