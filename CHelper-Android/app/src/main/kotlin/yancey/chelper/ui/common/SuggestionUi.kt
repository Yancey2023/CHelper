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

package yancey.chelper.ui.common

import yancey.chelper.core.Suggestion

/**
 * 补全候选的公共展示文案：命令页（拥挤单行/双行）与任何引擎候选消费方共用，
 * 徽标文字与"来源"格式只在此定义一份。
 */
object SuggestionUi {

    /** 来源包名（null = 内置，不显示徽标） */
    fun Suggestion.badgeText(): String? = packName?.takeIf { it.isNotBlank() }

    /** 「来自 XX」徽标文案（null = 内置） */
    fun Suggestion.sourceLabel(): String? = badgeText()?.let { "来自 $it" }

    /** 单行形态：`名字 - 描述 · 来自 XX` */
    fun Suggestion.oneLineText(): String {
        val sb = StringBuilder(name ?: "")
        description?.takeIf { it.isNotEmpty() }?.let { sb.append(" - ").append(it) }
        sourceLabel()?.let { sb.append(" · ").append(it) }
        return sb.toString()
    }
}
