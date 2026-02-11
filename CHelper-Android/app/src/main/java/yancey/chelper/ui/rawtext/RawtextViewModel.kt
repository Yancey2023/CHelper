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

package yancey.chelper.ui.rawtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ColorFormat(var format: String, var color: Color, var isLight: Boolean)

class RawtextViewModel : ViewModel() {
    var isPreview by mutableStateOf(false)
    var text by mutableStateOf(TextFieldValue())

    companion object {
        val colorFormats = listOf(
            ColorFormat("§0", Color(0xFF000000), false),
            ColorFormat("§1", Color(0xFF0000AA), false),
            ColorFormat("§2", Color(0xFF00AA00), true),
            ColorFormat("§3", Color(0xFF00AAAA), true),
            ColorFormat("§4", Color(0xFFAA0000), false),
            ColorFormat("§5", Color(0xFFAA00AA), false),
            ColorFormat("§6", Color(0xFFFFAA00), true),
            ColorFormat("§7", Color(0xFFAAAAAA), true),
            ColorFormat("§8", Color(0xFF555555), false),
            ColorFormat("§9", Color(0xFF5555FF), false),
            ColorFormat("§a", Color(0xFF55FF55), true),
            ColorFormat("§b", Color(0xFF55FFFF), true),
            ColorFormat("§c", Color(0xFFFF5555), true),
            ColorFormat("§d", Color(0xFFFF55FF), true),
            ColorFormat("§e", Color(0xFFFFFF55), true),
            ColorFormat("§f", Color(0xFFFFFFFF), true),
            ColorFormat("§g", Color(0xFFDDD605), true),
            ColorFormat("§h", Color(0xFFE3D4D1), true),
            ColorFormat("§i", Color(0xFFCECACA), true),
            ColorFormat("§j", Color(0xFF443A3B), false),
            ColorFormat("§m", Color(0xFF971607), false),
            ColorFormat("§n", Color(0xFFB4684D), true),
            ColorFormat("§p", Color(0xFFDEB12D), true),
            ColorFormat("§q", Color(0xFF47A036), true),
            ColorFormat("§s", Color(0xFF2CBAA8), true),
            ColorFormat("§t", Color(0xFF21497B), false),
            ColorFormat("§u", Color(0xFF9A5CC6), true),
        )
    }

    fun getRawJson(): String {
        // 获取文本
        val annotatedString = text.annotatedString
        // 获取颜色
        val colors = Array(annotatedString.length) { Color.White }
        for (range in annotatedString.spanStyles) {
            colors.fill(range.item.color, range.start, range.end)
        }
        // 输出Json
        val result = JsonObject()
        val rawText = JsonArray()
        val text = JsonObject()
        text.addProperty("text", buildString {
            if (colors.size > 0) {
                var indexStart = 0
                for (i in colors.indices) {
                    if (colors[i] == colors[indexStart]) {
                        continue
                    }
                    append(colorFormats.find { it.color == colors[indexStart] }?.format)
                    append(annotatedString.text.substring(indexStart, i))
                    indexStart = i
                }
                append(colorFormats.find { it.color == colors[indexStart] }?.format)
                append(annotatedString.text.substring(indexStart, colors.size))
            }
        })
        rawText.add(text)
        result.add("rawtext", rawText)
        return result.toString()
    }
}
