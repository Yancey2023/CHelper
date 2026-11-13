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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.ui.platform.LocalClientServices as LocalContext

@Composable
fun SettingsScreen(chooseBackground: () -> Unit, restoreBackground: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { context.settings }
    val model = viewModel { SettingsViewModel(repository) }
    SideEffect { context.session.register(context.settings, model::awaitSaved) }
    val settings by model.state.collectAsStateWithLifecycle()
    var packs by remember { mutableStateOf(emptyArray<Pair<String, String>>()) }
    LaunchedEffect(context) {
        packs = withContext(Dispatchers.IO) { resourcePackOptions(context.packFilenames) }
    }
    SettingsContent(
        settings,
        packs,
        chooseBackground,
        restoreBackground,
        model::update,
        floatingWindowSettings = context.platform.floatingWindow != null,
        loongFlowSettings = context.platform.supportsLoongFlow,
    )
}
