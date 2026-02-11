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

package yancey.chelper.ui.enumeration

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hjq.toast.Toaster
import redempt.crunch.data.Pair
import redempt.crunch.exceptions.ExpressionCompilationException
import yancey.chelper.android.common.util.MonitorUtil

class DataVariable {
    var name by mutableStateOf(TextFieldState())
    var start by mutableStateOf(TextFieldState())
    var interval by mutableStateOf(TextFieldState())
}

class EnumerationViewModel : ViewModel() {
    var expression by mutableStateOf(TextFieldState())
    var times by mutableStateOf(TextFieldState())
    var variableList = mutableStateListOf<DataVariable>()
    var output by mutableStateOf("")
    var isShowPreviewDialog by mutableStateOf(false)

    fun run() {
        val input = expression.text.toString()
        val timesInt: Int
        try {
            timesInt = times.text.toString().toInt()
        } catch (_: NumberFormatException) {
            Toaster.show("运行次数不是整数")
            return
        }
        val pairs: List<Pair<String, CustomDoubleSupplier>>
        try {
            pairs =
                variableList.map { dataVariable ->
                    Pair(
                        dataVariable.name.text.toString(),
                        CustomDoubleSupplier(
                            dataVariable.start.text.toString().toDouble(),
                            dataVariable.interval.text.toString().toDouble()
                        )
                    )
                }.toList()
        } catch (_: NumberFormatException) {
            Toaster.show("变量数据的获取出错")
            return
        }
        try {
            output = EnumerationUtil.run(input, pairs, timesInt)
            isShowPreviewDialog = true
        } catch (_: NumberFormatException) {
            Toaster.show("运行时出错：文字转数字时失败")
            return
        } catch (_: ExpressionCompilationException) {
            Toaster.show("运行时出错：表达式结构有问题")
            return
        } catch (throwable: Throwable) {
            Toaster.show("运行时出错")
            MonitorUtil.generateCustomLog(throwable, "ExpressionException")
            return
        }
    }
}
