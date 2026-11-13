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

package yancey.chelper.ui.feature.conversion

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Old2NewViewModel(private val conversionScope: CoroutineScope? = null) : ViewModel() {
    val oldCommand = TextFieldState()
    var newCommand by mutableStateOf("")
        private set

    var isConverting by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var revision = 0L

    fun convert(text: String, converter: suspend (String) -> String) {
        val version = ++revision
        job?.cancel()
        isConverting = text.isNotEmpty()
        newCommand = ""
        if (text.isEmpty()) return
        job =
            (conversionScope ?: viewModelScope).launch {
                try {
                    val result = converter(text)
                    if (revision == version && oldCommand.text.toString() == text)
                        newCommand = result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (revision == version) newCommand = "转换失败：${e.message ?: "未知错误"}"
                } finally {
                    if (revision == version) isConverting = false
                }
            }
    }
}
