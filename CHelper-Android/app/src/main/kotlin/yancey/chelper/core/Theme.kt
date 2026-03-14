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


package yancey.chelper.core

/**
 * 语法高亮主题
 */
class Theme {
    var colorBoolean: Int = 0
    var colorFloat: Int = 0
    var colorInteger: Int = 0
    var colorSymbol: Int = 0
    var colorId: Int = 0
    var colorTargetSelector: Int = 0
    var colorCommand: Int = 0
    var colorBrackets1: Int = 0
    var colorBrackets2: Int = 0
    var colorBrackets3: Int = 0
    var colorString: Int = 0
    var colorNull: Int = 0
    var colorRange: Int = 0
    var colorLiteral: Int = 0

    fun getColorByToken(token: Int, normalColor: Int): Int {
        return when (token) {
            1 -> colorBoolean
            2 -> colorFloat
            3 -> colorInteger
            4 -> colorSymbol
            5 -> colorId
            6 -> colorTargetSelector
            7 -> colorCommand
            8 -> colorBrackets1
            9 -> colorBrackets2
            10 -> colorBrackets3
            11 -> colorString
            12 -> colorNull
            13 -> colorRange
            14 -> colorLiteral
            else -> normalColor
        }
    }

    companion object {
        var THEME_DAY: Theme = Theme()
        var THEME_NIGHT: Theme = Theme()

        init {
            val COLOR_PURPLE = -0x60df59
            val COLOR_ORANGE = -0x26a5ad
            val COLOR_LIGHT_BLUE = -0xf05f38
            val COLOR_BLUE = -0xba8e1f
            val COLOR_LIGHT_GREEN = -0xb0529d
            val COLOR_GREEN = -0xf83ea0
            val COLOR_LIGHT_YELLOW = -0x2b53f3
            val COLOR_YELLOW = -0x7c93f6

            THEME_DAY.colorBoolean = COLOR_LIGHT_GREEN
            THEME_DAY.colorFloat = COLOR_LIGHT_GREEN
            THEME_DAY.colorInteger = COLOR_LIGHT_GREEN
            THEME_DAY.colorSymbol = COLOR_LIGHT_GREEN
            THEME_DAY.colorId = COLOR_LIGHT_YELLOW
            THEME_DAY.colorTargetSelector = COLOR_GREEN
            THEME_DAY.colorCommand = COLOR_PURPLE
            THEME_DAY.colorBrackets1 = COLOR_YELLOW
            THEME_DAY.colorBrackets2 = COLOR_PURPLE
            THEME_DAY.colorBrackets3 = COLOR_BLUE
            THEME_DAY.colorString = COLOR_ORANGE
            THEME_DAY.colorNull = COLOR_LIGHT_BLUE
            THEME_DAY.colorRange = COLOR_LIGHT_BLUE
            THEME_DAY.colorLiteral = COLOR_LIGHT_BLUE
        }

        init {
            val COLOR_PURPLE = -0x3a7940
            val COLOR_ORANGE = -0x316e88
            val COLOR_LIGHT_BLUE = -0x632302
            val COLOR_BLUE = -0xe86001
            val COLOR_LIGHT_GREEN = -0x4a3158
            val COLOR_GREEN = -0xb13650
            val COLOR_LIGHT_YELLOW = -0x232356
            val COLOR_YELLOW = -0x2900

            THEME_NIGHT.colorBoolean = COLOR_LIGHT_GREEN
            THEME_NIGHT.colorFloat = COLOR_LIGHT_GREEN
            THEME_NIGHT.colorInteger = COLOR_LIGHT_GREEN
            THEME_NIGHT.colorSymbol = COLOR_LIGHT_GREEN
            THEME_NIGHT.colorId = COLOR_LIGHT_YELLOW
            THEME_NIGHT.colorTargetSelector = COLOR_GREEN
            THEME_NIGHT.colorCommand = COLOR_PURPLE
            THEME_NIGHT.colorBrackets1 = COLOR_YELLOW
            THEME_NIGHT.colorBrackets2 = COLOR_PURPLE
            THEME_NIGHT.colorBrackets3 = COLOR_BLUE
            THEME_NIGHT.colorString = COLOR_ORANGE
            THEME_NIGHT.colorNull = COLOR_LIGHT_BLUE
            THEME_NIGHT.colorRange = COLOR_LIGHT_BLUE
            THEME_NIGHT.colorLiteral = COLOR_LIGHT_BLUE
        }
    }
}
