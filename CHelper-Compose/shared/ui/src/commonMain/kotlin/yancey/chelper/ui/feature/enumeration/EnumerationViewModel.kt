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

package yancey.chelper.ui.feature.enumeration

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import yancey.chelper.domain.enumeration.EnumerationRepository
import yancey.chelper.domain.enumeration.EnumerationVariable

class DataVariable {
    var name by mutableStateOf(TextFieldState())
    var start by mutableStateOf(TextFieldState())
    var interval by mutableStateOf(TextFieldState())
}

class EnumerationViewModel(private val repository: EnumerationRepository) : ViewModel() {
    var expression by mutableStateOf(TextFieldState())
    var times by mutableStateOf(TextFieldState())
    val variableList = mutableStateListOf<DataVariable>()
    var output by mutableStateOf("")
    var isShowPreviewDialog by mutableStateOf(false)
    var failure by mutableStateOf<String?>(null)
        private set

    var running by mutableStateOf(false)
        private set

    fun dismissFailure() {
        failure = null
    }

    fun run() {
        if (running) return
        val count = times.text.toString().toIntOrNull()
        if (count == null || count !in 0..100000) {
            failure = "运行次数需要是 0 到 100000 的整数"
            return
        }
        val variables =
            try {
                variableList.map {
                    EnumerationVariable(
                        it.name.text.toString(),
                        it.start.text.toString().toDouble(),
                        it.interval.text.toString().toDouble(),
                    )
                }
            } catch (_: NumberFormatException) {
                failure = "变量数据的获取出错"
                return
            }
        val command = expression.text.toString()
        running = true
        viewModelScope.launch {
            try {
                output = repository.evaluate(command, variables, count)
                ensureActive()
                isShowPreviewDialog = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "表达式执行失败"
            } finally {
                running = false
            }
        }
    }
}
