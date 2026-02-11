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

import redempt.crunch.CompiledExpression
import redempt.crunch.Crunch
import redempt.crunch.data.Pair
import redempt.crunch.functional.EvaluationEnvironment
import java.util.function.DoubleSupplier

interface Param {
    fun evaluate(): String
}

class StringParam(val value: String) : Param {
    override fun evaluate(): String {
        return value
    }
}

class ExpressionParam(
    private val expression: CompiledExpression,
    private val isFloat: Boolean
) : Param {
    override fun evaluate(): String {
        val a = expression.evaluate()
        return if (isFloat) a.toFloat().toString() else a.toInt().toString()
    }
}

class CustomDoubleSupplier(private var current: Double, private val interval: Double) :
    DoubleSupplier {
    override fun getAsDouble(): Double {
        return current
    }

    fun nextTime() {
        current += interval
    }
}


/**
 * 穷举表达式的工具类
 */
object EnumerationUtil {

    /**
     * 使用${}作为表达式
     *
     * ${a*(b+c)+10,int},输出整数
     *
     * ${sin(x)+pi,float},输出小数
     *
     * @param command 带有表达式的命令
     * @param pairs   表达式变量
     * @param times   次数
     * @return 经过处理的字符串
     */
    fun run(
        command: String,
        pairs: List<Pair<String, CustomDoubleSupplier>>,
        times: Int
    ): String {
        // 新建一个运行环境,把变量名传入
        val env = EvaluationEnvironment().apply {
            pairs.forEach {
                addLazyVariable(it.getFirst(), it.getSecond())
            }
        }
        // 解析命令中的表达式
        val params = mutableListOf<Param>()
        var start = 0
        while (true) {
            val a = command.indexOf("\${", start)
            if (a == -1) {
                break
            }
            val b = command.indexOf('}', a)
            if (b == -1) {
                break
            }
            val content = command.substring(a + 2, b).trim()
            val expression: String
            val isFloat: Boolean
            val c = content.lastIndexOf(',')
            if (c == -1) {
                expression = content
                isFloat = true
            } else {
                expression = content.substring(0, c)
                isFloat = content.substring(c + 1).trim() != "int"
            }
            params.add(StringParam(command.substring(start, a)))
            params.add(ExpressionParam(Crunch.compileExpression(expression, env), isFloat))
            start = b + 1
        }
        if (start != command.length) {
            params.add(StringParam(command.substring(start)))
        }
        // 开始运行
        return buildString {
            var isFirst = true
            repeat(times) {
                if (isFirst) {
                    isFirst = false
                } else {
                    append('\n')
                }
                for (param in params) {
                    append(param.evaluate())
                }
                for (pair in pairs) {
                    pair.getSecond().nextTime()
                }
            }
        }
    }
}
