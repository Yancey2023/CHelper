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

/*
 * 选择器字符串解析与拼装。
 * 对应网页版 ui/index.ts 里的 parseSelectorString / buildSelectorString。
 * 关键难点是 hasitem / scores 这两个参数的值本身带有 {} 和 []，
 * 用普通逗号切分会把它们撕碎，所以这里用一个状态机按括号深度切分参数。
 */
object SelectorParser {

    // 高级模式里逐个暴露的简单字段（值不含括号）
    val SIMPLE_FIELDS = listOf(
        "type", "name", "c", "family",
        "x", "y", "z", "r", "rm",
        "rx", "rxm", "ry", "rym",
        "dx", "dy", "dz",
        "tag",
        "m", "lm", "l"
    )

    val BASE_OPTIONS = listOf("p", "r", "a", "e", "s", "n")

    fun parse(selector: String): ParsedSelector {
        val trimmed = selector.trim()
        val baseMatch = Regex("^@([prsaen])").find(trimmed)
        val base = baseMatch?.groupValues?.get(1) ?: "p"
        val params = linkedMapOf<String, String>()

        val bracketStart = trimmed.indexOf('[')
        if (bracketStart >= 0 && trimmed.endsWith("]")) {
            val inner = trimmed.substring(bracketStart + 1, trimmed.length - 1)
            for ((key, value) in splitParams(inner)) {
                params[key] = value
            }
        }
        return ParsedSelector(base, params)
    }

    fun build(base: String, params: Map<String, String>): String {
        val parts = params.entries
            .filter { it.value.isNotBlank() }
            .map { "${it.key}=${it.value}" }
        return if (parts.isEmpty()) "@$base" else "@$base[${parts.joinToString(",")}]"
    }

    /**
     * 按括号深度切分 key=value，兼容 hasitem={...} / hasitem=[{...},{...}] / scores={...}
     */
    private fun splitParams(inner: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val buffer = StringBuilder()
        var depth = 0
        for (ch in inner) {
            when (ch) {
                '{', '[' -> {
                    depth++
                    buffer.append(ch)
                }

                '}', ']' -> {
                    depth--
                    buffer.append(ch)
                }

                ',' -> {
                    if (depth == 0) {
                        addPair(result, buffer.toString())
                        buffer.clear()
                    } else {
                        buffer.append(ch)
                    }
                }

                else -> buffer.append(ch)
            }
        }
        if (buffer.isNotBlank()) addPair(result, buffer.toString())
        return result
    }

    private fun addPair(result: MutableList<Pair<String, String>>, raw: String) {
        val eq = raw.indexOf('=')
        if (eq <= 0) return
        val key = raw.substring(0, eq).trim()
        val value = raw.substring(eq + 1).trim()
        if (key.isNotEmpty()) result.add(key to value)
    }
}

data class ParsedSelector(
    val base: String,
    val params: Map<String, String>
)

/**
 * hasitem 单条件。对应网页版 hasitem-condition-item 模板的字段。
 */
data class HasitemCondition(
    var item: String = "",
    var data: String = "",
    var quantity: String = "",
    var location: String = "",
    var slot: String = "",
) {
    fun isEmpty(): Boolean =
        item.isBlank() && data.isBlank() && quantity.isBlank() && location.isBlank() && slot.isBlank()

    fun toClause(): String {
        val parts = mutableListOf<String>()
        if (item.isNotBlank()) parts.add("item=$item")
        if (data.isNotBlank()) parts.add("data=$data")
        if (quantity.isNotBlank()) parts.add("quantity=$quantity")
        if (location.isNotBlank()) parts.add("location=$location")
        if (slot.isNotBlank()) parts.add("slot=$slot")
        return "{${parts.joinToString(",")}}"
    }

    companion object {
        fun parseValue(value: String): List<HasitemCondition> {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return emptyList()
            val objects = when {
                trimmed.startsWith("[") && trimmed.endsWith("]") ->
                    splitObjects(trimmed.substring(1, trimmed.length - 1))

                trimmed.startsWith("{") && trimmed.endsWith("}") ->
                    listOf(trimmed.substring(1, trimmed.length - 1))

                else -> listOf(trimmed)
            }
            return objects.mapNotNull { parseSingle(it) }.filter { !it.isEmpty() }
        }

        private fun splitObjects(inner: String): List<String> {
            val result = mutableListOf<String>()
            val buffer = StringBuilder()
            var depth = 0
            for (ch in inner) {
                when (ch) {
                    '{' -> {
                        if (depth > 0) buffer.append(ch)
                        depth++
                    }

                    '}' -> {
                        depth--
                        if (depth > 0) buffer.append(ch) else {
                            result.add(buffer.toString())
                            buffer.clear()
                        }
                    }

                    else -> if (depth > 0) buffer.append(ch)
                }
            }
            return result
        }

        private fun parseSingle(raw: String): HasitemCondition? {
            val cleaned = raw.trim().removePrefix("{").removeSuffix("}")
            if (cleaned.isBlank()) return null
            val condition = HasitemCondition()
            for (pair in cleaned.split(",")) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val key = pair.substring(0, eq).trim()
                val v = pair.substring(eq + 1).trim()
                when (key) {
                    "item" -> condition.item = v
                    "data" -> condition.data = v
                    "quantity" -> condition.quantity = v
                    "location" -> condition.location = v
                    "slot" -> condition.slot = v
                }
            }
            return condition
        }

        fun buildValue(conditions: List<HasitemCondition>): String {
            val valid = conditions.filter { !it.isEmpty() }
            return when {
                valid.isEmpty() -> ""
                valid.size == 1 -> valid.first().toClause()
                else -> "[${valid.joinToString(",") { it.toClause() }}]"
            }
        }
    }
}

/**
 * scores 单条件：记分项 + 值（支持 10..12 / !10 / 5.. 这类写法）。
 */
data class ScoreCondition(
    var objective: String = "",
    var value: String = "",
) {
    fun isEmpty(): Boolean = objective.isBlank() && value.isBlank()

    companion object {
        fun parseValue(value: String): List<ScoreCondition> {
            val trimmed = value.trim().removePrefix("{").removeSuffix("}")
            if (trimmed.isBlank()) return emptyList()
            return trimmed.split(",").mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val objective = pair.substring(0, eq).trim()
                val v = pair.substring(eq + 1).trim()
                if (objective.isEmpty()) null else ScoreCondition(objective, v)
            }
        }

        fun buildValue(conditions: List<ScoreCondition>): String {
            val valid = conditions.filter { it.objective.isNotBlank() }
            if (valid.isEmpty()) return ""
            return "{${valid.joinToString(",") { "${it.objective}=${it.value}" }}}"
        }
    }
}
