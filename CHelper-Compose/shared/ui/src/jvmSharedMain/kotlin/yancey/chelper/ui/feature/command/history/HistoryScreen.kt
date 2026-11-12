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

package yancey.chelper.ui.feature.command.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import yancey.chelper.ui.feature.command.HistoryContent
import yancey.chelper.ui.platform.ClientToast as Toaster
import yancey.chelper.ui.platform.LocalClientServices as LocalContext

@Composable
fun HistoryScreen(history: List<String>?) {
    val scope = rememberCoroutineScope()
    val clipboard = yancey.chelper.ui.platform.LocalClientServices.current.platform
    HistoryContent(history) { text ->
        scope.launch {
            clipboard.copyText(text)
            Toaster.show("已复制")
        }
    }
}

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val copyHistoryDataStore = remember { context.history }
    val history by copyHistoryDataStore.history().collectAsStateWithLifecycle(initialValue = null)
    HistoryScreen(history)
}
