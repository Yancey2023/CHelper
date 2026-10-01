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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 原始 JSON 文本的导入 / 导出（对应 titleraw 的 rawtext 结构）。
 * 使用 kotlinx.serialization 的 JsonElement 而非 org.json，与项目其余 JSON 处理保持一致。
 */
object RawtextJson {

    private val parser = Json { ignoreUnknownKeys = true }
    private val prettyPrinter = Json { prettyPrint = true; prettyPrintIndent = "  " }
    private val compactPrinter = Json.Default

    // ---------- 导入 ----------

    /** 展平导入：嵌套 {"rawtext":[...]} 自动展开 */
    private fun toElements(node: JsonObject): List<RawtextElement> {
        val raw = node["rawtext"] as? JsonArray
        if (raw != null) {
            val out = mutableListOf<RawtextElement>()
            raw.forEach { c -> if (c is JsonObject) out.addAll(toElements(c)) }
            return out
        }
        return listOf(toElement(node))
    }

    private fun toElement(node: JsonObject): RawtextElement {
        node["text"]?.let { text ->
            return RawtextElement.Text(
                when (text) {
                    is JsonNull -> ""
                    is JsonPrimitive -> text.content
                    else -> ""
                }
            )
        }
        (node["translate"] as? JsonPrimitive)?.let { translate ->
            val key = translate.content
            val withRaw = (node["with"] as? JsonObject)?.get("rawtext") as? JsonArray
            val shape = parseConditionalShape(key, withRaw)
            if (shape != null) {
                buildCondition(shape)?.let { return it }
            }
            if (withRaw != null && Regex("%%[1-9]").containsMatchIn(key)) {
                val c = RawtextElement.Condition()
                c.mode = RawtextConditionMode.Sequence
                c.hasBlank = false
                c.seqKey = key
                withRaw.forEach { o -> if (o is JsonObject) c.seqSlots.addAll(toElements(o)) }
                return c
            }
            val t = RawtextElement.Translate()
            t.key = key
            val withArr = node["with"] as? JsonArray
            if (withArr != null) {
                t.withMode = RawtextWithMode.Array
                withArr.forEach { t.args.add(it.valueString()) }
            } else if (withRaw != null) {
                t.withMode = RawtextWithMode.Rawtext
                withRaw.forEach { o -> if (o is JsonObject) t.raw.addAll(toElements(o)) }
            }
            return t
        }
        (node["selector"] as? JsonPrimitive)?.let { selector ->
            return RawtextElement.Selector(selector.content)
        }
        (node["score"] as? JsonObject)?.let { score ->
            if (score.containsKey("name")) {
                val name = (score["name"] as? JsonPrimitive)?.content ?: ""
                val objective = (score["objective"] as? JsonPrimitive)?.content ?: ""
                return RawtextElement.Score(name, objective)
            }
        }
        return RawtextElement.Text("")
    }

    /** 单个 %%N 条件形状：M=N-1，[选择器×M]+[选项×M(+1)] */
    private data class CondShape(
        val selectors: List<String>,
        val options: List<JsonObject>,
        val hasFallback: Boolean,
    )

    private fun parseConditionalShape(key: String, withRaw: JsonArray?): CondShape? {
        val m = Regex("^%%([2-9])$").find(key) ?: return null
        if (withRaw == null) return null
        val mNum = m.groupValues[1].toInt()
        val count = mNum - 1
        if (count < 1) return null
        val len = withRaw.size
        if (len != 2 * count && len != 2 * count + 1) return null
        val sels = mutableListOf<String>()
        for (i in 0 until count) {
            val o = withRaw[i] as? JsonObject ?: return null
            val s = (o["selector"] as? JsonPrimitive)?.content
            if (s.isNullOrEmpty()) return null
            sels.add(s)
        }
        val opts = mutableListOf<JsonObject>()
        for (i in count until len) {
            val o = withRaw[i] as? JsonObject ?: return null
            opts.add(o)
        }
        return CondShape(sels, opts, len == 2 * count + 1)
    }

    private fun optToBranch(opt: JsonObject): RawtextBranch? {
        opt["text"]?.let { text ->
            val t = when (text) {
                is JsonNull -> ""
                is JsonPrimitive -> text.content
                else -> ""
            }
            return RawtextBranch(items = mutableListOf(RawtextElement.Text(t)))
        }
        (opt["rawtext"] as? JsonArray)?.let { raw ->
            val items = mutableListOf<RawtextElement>()
            raw.forEach { o -> if (o is JsonObject) items.addAll(toElements(o)) }
            return RawtextBranch(items = items)
        }
        val single = toElement(opt)
        return RawtextBranch(items = mutableListOf(single))
    }

    private fun buildCondition(shape: CondShape): RawtextElement.Condition? {
        val e = RawtextElement.Condition()
        e.branches.clear() // 清除默认构造的两个空分支
        e.mode = RawtextConditionMode.Branch
        e.hasBlank = shape.hasFallback
        var objective = ""
        var target = ""
        for (i in shape.selectors.indices) {
            val sel = RawtextSelectorParser.parse(shape.selectors[i])
            if (i == 0) {
                objective = sel.args.firstOrNull { it.key == "scores" }?.objective ?: ""
                target = sel.variable
            }
            val b = optToBranch(shape.options[i]) ?: return null
            e.branches.add(RawtextBranch(shape.selectors[i], sel, b.items))
        }
        if (!shape.hasFallback) {
            e.objective = objective
            e.target = target.ifEmpty { "@s" }
            return e
        }
        val fb = shape.options.last()
        if (fb.containsKey("text")) {
            val t = fb["text"]
            val text = when (t) {
                is JsonNull -> ""
                is JsonPrimitive -> t.content
                else -> ""
            }
            e.branches.add(RawtextBranch(items = mutableListOf(RawtextElement.Text(text))))
        } else {
            val b = optToBranch(fb) ?: return null
            e.branches.add(RawtextBranch(items = b.items))
        }
        e.objective = objective
        e.target = target.ifEmpty { "@s" }
        return e
    }

    /** 提取命令文本中的 JSON 对象：优先定位 {"rawtext" 起始，避免选择器里 scores={...} 等花括号干扰 */
    private fun extractJsonObject(s: String): String? {
        var i = s.indexOf("{\"rawtext\"")
        if (i < 0) i = s.indexOf('{')
        if (i < 0) return null
        var depth = 0
        for (j in i until s.length) {
            when (s[j]) {
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return s.substring(i, j + 1)
                }
            }
        }
        return null
    }

    fun importText(text: String): List<RawtextElement> {
        val t = text.trim()
        if (t.startsWith("[")) {
            return try {
                val arr = parser.parseToJsonElement(t) as? JsonArray
                    ?: throw IllegalArgumentException("JSON 解析失败")
                val out = mutableListOf<RawtextElement>()
                arr.forEach { o -> if (o is JsonObject) out.addAll(toElements(o)) }
                out
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("JSON 解析失败")
            }
        }
        var root: JsonObject? = runCatching { parser.parseToJsonElement(t) as? JsonObject }.getOrNull()
        if (root == null) {
            val obj = extractJsonObject(t)
            if (obj != null) root = runCatching { parser.parseToJsonElement(obj) as? JsonObject }.getOrNull()
        }
        val jsonRoot = root ?: throw IllegalArgumentException("JSON 解析失败")
        val arr = jsonRoot["rawtext"] as? JsonArray
        if (arr != null) {
            val out = mutableListOf<RawtextElement>()
            arr.forEach { o -> if (o is JsonObject) out.addAll(toElements(o)) }
            return out
        }
        // 顶层对象本身作为一个元素
        return toElements(jsonRoot)
    }

    // ---------- 导出 ----------

    fun branchOutput(b: RawtextBranch): JsonObject {
        val items = b.items
        if (items.isNotEmpty()) {
            if (items.size == 1) return toJson(items[0])
            return JsonObject(mapOf("rawtext" to JsonArray(items.map { toJson(it) as JsonElement })))
        }
        return JsonObject(mapOf("text" to JsonPrimitive("")))
    }

    private fun scoresSel(b: RawtextBranch, e: RawtextElement.Condition): JsonObject {
        val raw = b.selectorRaw
        val value = if (!raw.isNullOrBlank()) raw.trim()
        else RawtextSelectorParser.string(b.sel ?: RawtextParsedSelector(e.target.ifEmpty { "@s" }))
        return JsonObject(mapOf("selector" to JsonPrimitive(value)))
    }

    private fun conditionToElement(e: RawtextElement.Condition): JsonObject {
        val rawBranches = e.branches
        val hasBlank = e.hasBlank && rawBranches.isNotEmpty()
        val branches = rawBranches.filterIndexed { i, b ->
            (hasBlank && i == rawBranches.size - 1) ||
                (b.sel?.variable != null) ||
                b.items.isNotEmpty()
        }
        val entries = if (hasBlank) branches.dropLast(1) else branches
        val fallback = if (hasBlank) branches.lastOrNull() else null

        fun build(list: List<RawtextBranch>, fb: RawtextBranch?): JsonObject {
            val arr = mutableListOf<JsonElement>()
            if (list.size <= 8) {
                list.forEach { arr.add(scoresSel(it, e)) }
                list.forEach { arr.add(branchOutput(it)) }
                if (fb != null) arr.add(branchOutput(fb))
                return JsonObject(
                    mapOf(
                        "translate" to JsonPrimitive("%%" + (list.size + 1)),
                        "with" to JsonObject(mapOf("rawtext" to JsonArray(arr))),
                    )
                )
            }
            val first = list.take(8)
            val rest = list.drop(8)
            first.forEach { arr.add(scoresSel(it, e)) }
            first.forEach { arr.add(branchOutput(it)) }
            arr.add(build(rest, fb))
            return JsonObject(
                mapOf(
                    "translate" to JsonPrimitive("%%9"),
                    "with" to JsonObject(mapOf("rawtext" to JsonArray(arr))),
                )
            )
        }
        return build(entries, fallback)
    }

    private fun sequenceToElement(e: RawtextElement.Condition): JsonObject {
        val slots = e.seqSlots
        val key = e.seqKey.trim()
        val translate = if (key.isNotEmpty() && Regex("%%[1-9]").containsMatchIn(key)) key
        else slots.indices.joinToString("") { "%%" + (it + 1) }
        return JsonObject(
            mapOf(
                "translate" to JsonPrimitive(translate),
                "with" to JsonObject(mapOf("rawtext" to JsonArray(slots.map { toJson(it) as JsonElement }))),
            )
        )
    }

    private fun toJson(el: RawtextElement): JsonObject {
        return when (el) {
            is RawtextElement.Text -> JsonObject(mapOf("text" to JsonPrimitive(el.text)))

            is RawtextElement.Translate -> {
                when {
                    el.withMode == RawtextWithMode.Array && el.args.isNotEmpty() -> JsonObject(
                        mapOf(
                            "translate" to JsonPrimitive(el.key),
                            "with" to JsonArray(el.args.map { JsonPrimitive(it) }),
                        )
                    )

                    el.withMode == RawtextWithMode.Rawtext && el.raw.isNotEmpty() -> JsonObject(
                        mapOf(
                            "translate" to JsonPrimitive(el.key),
                            "with" to JsonObject(mapOf("rawtext" to JsonArray(el.raw.map { toJson(it) as JsonElement }))),
                        )
                    )

                    else -> JsonObject(mapOf("translate" to JsonPrimitive(el.key)))
                }
            }

            is RawtextElement.Selector ->
                JsonObject(mapOf("selector" to JsonPrimitive(el.rawSelector.trim().ifEmpty { "@s" })))

            is RawtextElement.Score -> JsonObject(
                mapOf(
                    "score" to JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(el.name),
                            "objective" to JsonPrimitive(el.objective),
                        )
                    )
                )
            )

            is RawtextElement.Condition ->
                if (el.mode == RawtextConditionMode.Sequence) sequenceToElement(el) else conditionToElement(el)
        }
    }

    private fun buildJSON(elements: List<RawtextElement>): JsonObject =
        JsonObject(mapOf("rawtext" to JsonArray(elements.map { toJson(it) as JsonElement })))

    fun pretty(elements: List<RawtextElement>): String = prettyPrinter.encodeToString(buildJSON(elements))

    /** 紧凑单行 JSON（无换行/缩进），供复制粘贴进 /titleraw 使用 */
    fun compact(elements: List<RawtextElement>): String = compactPrinter.encodeToString(buildJSON(elements))

    private fun JsonElement.valueString(): String = when (this) {
        is JsonNull -> "null"
        is JsonPrimitive -> content
        else -> toString()
    }
}
