/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * 条件块的结构化模型，对应网页版 getConditionalEditorContent / applyEdit。
 * 条件分三型：selector / score / rawjson。
 * score 的分数值支持 10 / 10..12 / 5.. / ..15 / !10 五种写法，
 * 与游戏内 score min/max/value/not 字段互转。
 */
enum class ConditionType { Selector, Score, RawJson }

data class ConditionEditorState(
    var type: ConditionType = ConditionType.Selector,
    var selector: String = "@p",
    var scoreName: String = "@p",
    var scoreObjective: String = "money",
    var scoreValue: String = "",
    var rawJson: String = "",
) {
    companion object {
        private val parser = Json { ignoreUnknownKeys = true }

        fun fromJson(conditionJson: String): ConditionEditorState {
            val state = ConditionEditorState()
            val obj = runCatching { parser.parseToJsonElement(conditionJson).jsonObject }.getOrNull()
            if (obj == null) {
                state.type = ConditionType.RawJson
                state.rawJson = conditionJson
                return state
            }
            when {
                obj.containsKey("score") -> {
                    state.type = ConditionType.Score
                    val score = obj["score"]?.jsonObject
                    state.scoreName = score?.get("name")?.jsonPrimitive?.contentOrNull ?: "@p"
                    state.scoreObjective = score?.get("objective")?.jsonPrimitive?.contentOrNull ?: "money"
                    state.scoreValue = formatScoreValue(score)
                }

                obj.containsKey("selector") -> {
                    state.type = ConditionType.Selector
                    state.selector = obj["selector"]?.jsonPrimitive?.contentOrNull ?: "@p"
                }

                else -> {
                    state.type = ConditionType.RawJson
                    state.rawJson = conditionJson
                }
            }
            return state
        }

        private fun formatScoreValue(score: JsonObject?): String {
            if (score == null) return ""
            val min = score["min"]?.jsonPrimitive?.intOrNull
            val max = score["max"]?.jsonPrimitive?.intOrNull
            val value = score["value"]?.jsonPrimitive?.intOrNull
            val not = score["not"]?.jsonPrimitive?.intOrNull
            return when {
                min != null && max != null -> "$min..$max"
                min != null -> "$min.."
                max != null -> "..$max"
                value != null -> "$value"
                not != null -> "!$not"
                else -> ""
            }
        }
    }

    /** 拼回条件 JSON 字符串。rawjson 非法时原样返回，交给上层兜底。 */
    fun toConditionJson(): String = when (type) {
        ConditionType.Selector -> buildJsonObject { put("selector", selector) }.toString()

        ConditionType.Score -> buildJsonObject {
            putJsonObject("score") {
                if (scoreName.isNotBlank()) put("name", scoreName)
                if (scoreObjective.isNotBlank()) put("objective", scoreObjective)
                applyScoreValue(scoreValue)
            }
        }.toString()

        ConditionType.RawJson -> rawJson.ifBlank { "{}" }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.applyScoreValue(raw: String) {
        val v = raw.trim()
        if (v.isEmpty()) return
        when {
            v.contains("..") -> {
                val parts = v.split("..")
                parts.getOrNull(0)?.trim()?.toIntOrNull()?.let { put("min", it) }
                parts.getOrNull(1)?.trim()?.toIntOrNull()?.let { put("max", it) }
            }

            v.startsWith("!") -> v.substring(1).trim().toIntOrNull()?.let { put("not", it) }
            else -> v.toIntOrNull()?.let { put("value", it) }
        }
    }
}
