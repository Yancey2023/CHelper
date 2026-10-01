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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/** 调试面板计分板「自定义数组模式」解析（与拖动条单值模式互斥）：
 *  - 1,3,8        → {1, 3, 8}
 *  - 1..          → [1, +∞)
 *  - ..100        → (-∞, 100]
 *  - 10..100      → [10, 100]
 *  - 1,10..,..-1  → {1} ∪ [10, +∞) ∪ (-∞, -1]
 */
object RawtextScoreArray {

    /** 闭区间 [lo, hi]；null 表示该侧无界（±∞） */
    data class RawtextScoreInterval(val lo: Double?, val hi: Double?)

    /** 解析逗号分隔的数值/区间表达式；空项与无法解析的项会被忽略（返回空列表 = 未激活任何值） */
    fun parse(expr: String): List<RawtextScoreInterval> {
        val out = mutableListOf<RawtextScoreInterval>()
        for (raw in expr.split(',')) {
            val tok = raw.trim()
            if (tok.isEmpty()) continue
            if (tok.contains("..")) {
                val parts = tok.split("..", limit = 2)
                val a = parts[0].trim()
                val b = parts[1].trim()
                if (a.isEmpty() && b.isEmpty()) continue // ".." 无意义
                val lo = if (a.isEmpty()) null else a.toDoubleOrNull() ?: continue
                val hi = if (b.isEmpty()) null else b.toDoubleOrNull() ?: continue
                out.add(RawtextScoreInterval(lo, hi))
            } else {
                val n = tok.toDoubleOrNull() ?: continue
                out.add(RawtextScoreInterval(n, n))
            }
        }
        return out
    }

    /** 判断选择器区间 [olo, ohi] 是否与激活集合有交集 */
    fun intersects(intervals: List<RawtextScoreInterval>, olo: Double?, ohi: Double?): Boolean =
        intervals.any { (lo, hi) -> overlap(lo, hi, olo, ohi) }

    /** 校验表达式；返回 null 表示合法，否则返回错误说明（空字符串视为合法 = 未激活） */
    fun validate(expr: String): String? {
        for (raw in expr.split(',')) {
            val tok = raw.trim()
            if (tok.isEmpty()) continue
            if (tok.contains("..")) {
                val parts = tok.split("..", limit = 2)
                val a = parts[0].trim()
                val b = parts[1].trim()
                if (a.isEmpty() && b.isEmpty()) return "区间不能为空：$tok"
                if (a.isNotEmpty() && a.toDoubleOrNull() == null) return "无法解析：$tok"
                if (b.isNotEmpty() && b.toDoubleOrNull() == null) return "无法解析：$tok"
            } else {
                if (tok.toDoubleOrNull() == null) return "无法解析：$tok"
            }
        }
        return null
    }

    /** 取首个可读激活值：取首个区间的下界（如 1,3,8→1、10..100→10、1..→1）；
     *  仅 ..100 这类无下界时返回 null，由调用方回退到表达式原文。 */
    fun firstActiveValue(expr: String): String? {
        val first = parse(expr).firstOrNull() ?: return null
        val n = first.lo ?: return null
        val l = n.toLong()
        return if (n == l.toDouble()) l.toString() else n.toString()
    }

    private fun overlap(aLo: Double?, aHi: Double?, bLo: Double?, bHi: Double?): Boolean {
        if (aHi != null && bLo != null && aHi < bLo) return false
        if (bHi != null && aLo != null && bHi < aLo) return false
        return true
    }
}

/** 调试状态：模拟玩家条件（使用 Compose 快照状态，使输入/点击能即时刷新 UI） */
class RawtextDebugState {
    val scores = mutableStateMapOf<String, String>()
    val tags = mutableStateListOf<String>()
    val items = mutableStateListOf<String>()
    val types = mutableStateListOf<String>()
    val families = mutableStateListOf<String>()
    val names = mutableStateListOf<String>()
    val counts = mutableStateListOf<String>()

    /** 自定义布尔参数开关（选择器数据化参数值为 true/false 时在此启停） */
    val boolFlags = mutableStateListOf<String>()
    val hiddenSelectors = mutableStateListOf<String>()
    var distance: String by mutableStateOf("0")
    var level: String by mutableStateOf("0")
    var posX: String by mutableStateOf("0")
    var posY: String by mutableStateOf("64")
    var posZ: String by mutableStateOf("0")
    var pitch: String by mutableStateOf("0")
    var yaw: String by mutableStateOf("0")
    var gamemode: String by mutableStateOf("")

    /** 计分板「自定义数组模式」：处于数组模式的目标计分板集合 */
    val arrayScores = mutableStateListOf<String>()

    /** 数组模式表达式原文（objective -> "1,3,8 / 10..100 / 1.. / ..-1"） */
    val arrayScoreText = mutableStateMapOf<String, String>()

    /** 从持久化快照恢复全部字段 */
    fun apply(snapshot: RawtextDebugSnapshot) {
        scores.clear(); scores.putAll(snapshot.scores)
        tags.clear(); tags.addAll(snapshot.tags)
        items.clear(); items.addAll(snapshot.items)
        types.clear(); types.addAll(snapshot.types)
        families.clear(); families.addAll(snapshot.families)
        names.clear(); names.addAll(snapshot.names)
        counts.clear(); counts.addAll(snapshot.counts)
        boolFlags.clear(); boolFlags.addAll(snapshot.boolFlags)
        hiddenSelectors.clear(); hiddenSelectors.addAll(snapshot.hiddenSelectors)
        distance = snapshot.distance
        level = snapshot.level
        posX = snapshot.posX
        posY = snapshot.posY
        posZ = snapshot.posZ
        pitch = snapshot.pitch
        yaw = snapshot.yaw
        gamemode = snapshot.gamemode
        arrayScores.clear(); arrayScores.addAll(snapshot.arrayScores)
        arrayScoreText.clear(); arrayScoreText.putAll(snapshot.arrayScoreText)
    }
}

/** 调试状态的持久化快照（不含 Compose 包装，可直接 kotlinx 序列化） */
@Serializable
data class RawtextDebugSnapshot(
    val scores: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val items: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val families: Set<String> = emptySet(),
    val names: Set<String> = emptySet(),
    val counts: Set<String> = emptySet(),
    val boolFlags: Set<String> = emptySet(),
    val hiddenSelectors: Set<String> = emptySet(),
    val distance: String = "0",
    val level: String = "0",
    val posX: String = "0",
    val posY: String = "64",
    val posZ: String = "0",
    val pitch: String = "0",
    val yaw: String = "0",
    val gamemode: String = "",
    val arrayScores: Set<String> = emptySet(),
    val arrayScoreText: Map<String, String> = emptyMap(),
) {
    fun toState(): RawtextDebugState {
        val s = RawtextDebugState()
        s.scores.putAll(scores)
        s.tags.addAll(tags)
        s.items.addAll(items)
        s.types.addAll(types)
        s.families.addAll(families)
        s.names.addAll(names)
        s.counts.addAll(counts)
        s.boolFlags.addAll(boolFlags)
        s.hiddenSelectors.addAll(hiddenSelectors)
        s.distance = distance
        s.level = level
        s.posX = posX
        s.posY = posY
        s.posZ = posZ
        s.pitch = pitch
        s.yaw = yaw
        s.gamemode = gamemode
        s.arrayScores.addAll(arrayScores)
        s.arrayScoreText.putAll(arrayScoreText)
        return s
    }

    companion object {
        fun from(state: RawtextDebugState): RawtextDebugSnapshot = RawtextDebugSnapshot(
            scores = state.scores.toMap(),
            tags = state.tags.toSet(),
            items = state.items.toSet(),
            types = state.types.toSet(),
            families = state.families.toSet(),
            names = state.names.toSet(),
            counts = state.counts.toSet(),
            boolFlags = state.boolFlags.toSet(),
            hiddenSelectors = state.hiddenSelectors.toSet(),
            distance = state.distance,
            level = state.level,
            posX = state.posX,
            posY = state.posY,
            posZ = state.posZ,
            pitch = state.pitch,
            yaw = state.yaw,
            gamemode = state.gamemode,
            arrayScores = state.arrayScores.toSet(),
            arrayScoreText = state.arrayScoreText.toMap(),
        )
    }
}

/** 收集到的选择器条件目标 */
data class RawtextDebugTargets(
    val condScores: Set<String> = emptySet(),
    val displayScores: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val items: Set<String> = emptySet(),
    val gms: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val families: Set<String> = emptySet(),
    val names: Set<String> = emptySet(),
    val counts: Set<String> = emptySet(),
    val boolFlags: Set<String> = emptySet(),
    val hasDist: Boolean = false,
    val hasLevel: Boolean = false,
    val hasPos: Boolean = false,
    val hasRot: Boolean = false,
    val displaySelectors: Set<String> = emptySet(),
    /** 各计分板的滑块范围（选择器分数的最小值-1 .. 最大值+1） */
    val scoreBounds: Map<String, IntRange> = emptyMap(),
    val globalScoreRange: IntRange? = null,
)

object RawtextDebugEngine {

    private fun scoreMatches(value: String?, rangeStr: String, negate: Boolean): Boolean {
        if (value.isNullOrEmpty()) return false
        val s = rangeStr.trim()
        val v = value.toDoubleOrNull() ?: return false
        val matched = if (s.contains("..")) {
            val parts = s.split("..", limit = 2)
            val a = parts[0].trim()
            val b = parts[1].trim()
            when {
                a.isEmpty() && b.isEmpty() -> true
                a.isEmpty() -> v <= (b.toDoubleOrNull() ?: return false)
                b.isEmpty() -> v >= (a.toDoubleOrNull() ?: return false)
                else -> v >= (a.toDoubleOrNull() ?: return false) && v <= (b.toDoubleOrNull() ?: return false)
            }
        } else {
            v == (s.toDoubleOrNull() ?: return false)
        }
        return if (negate) !matched else matched
    }

    /** 选择器 scores 区间串 → 闭区间 (lo, hi)；null 表示该侧无界 */
    private fun scoreRangeInterval(s: String): Pair<Double?, Double?> {
        val t = s.trim()
        if (!t.contains("..")) {
            val n = t.toDoubleOrNull()
            return n to n
        }
        val parts = t.split("..", limit = 2)
        val a = parts[0].trim()
        val b = parts[1].trim()
        return (a.toDoubleOrNull()) to (b.toDoubleOrNull())
    }

    fun selectorMatches(sel: RawtextParsedSelector, dbg: RawtextDebugState): Boolean {
        val args = sel.args
        if (args.isEmpty()) return true
        val pm = mutableMapOf<String, String>()
        args.forEach { pm[it.key] = it.value }
        // 位置
        val posKeys = listOf("x", "y", "z", "dx", "dy", "dz")
        if (posKeys.any { pm.containsKey(it) }) {
            if (posKeys.filter { pm.containsKey(it) }.all { !pm[it]!!.contains("~") }) {
                fun inAxis(c: String, d: String): Boolean {
                    val o = (pm[c]?.toDoubleOrNull()) ?: 0.0
                    val sz = (pm[d]?.toDoubleOrNull()) ?: 0.0
                    val p = when (c) {
                        "x" -> dbg.posX.toDoubleOrNull() ?: 0.0
                        "y" -> dbg.posY.toDoubleOrNull() ?: 64.0
                        else -> dbg.posZ.toDoubleOrNull() ?: 0.0
                    }
                    return p >= o && p <= o + sz
                }
                if (!inAxis("x", "dx") || !inAxis("y", "dy") || !inAxis("z", "dz")) return false
            }
        }
        // 视角
        pm["rx"]?.let { if ((dbg.pitch.toDoubleOrNull() ?: 0.0) > (it.toDoubleOrNull() ?: 0.0)) return false }
        pm["rxm"]?.let { if ((dbg.pitch.toDoubleOrNull() ?: 0.0) < (it.toDoubleOrNull() ?: 0.0)) return false }
        pm["ry"]?.let { if ((dbg.yaw.toDoubleOrNull() ?: 0.0) > (it.toDoubleOrNull() ?: 0.0)) return false }
        pm["rym"]?.let { if ((dbg.yaw.toDoubleOrNull() ?: 0.0) < (it.toDoubleOrNull() ?: 0.0)) return false }
        // 逐项
        for (a in args) {
            when (a.key) {
                "scores" -> {
                    val matched = if (dbg.arrayScores.contains(a.objective)) {
                        val intervals = RawtextScoreArray.parse(dbg.arrayScoreText[a.objective] ?: "")
                        val (lo, hi) = scoreRangeInterval(a.range)
                        RawtextScoreArray.intersects(intervals, lo, hi)
                    } else {
                        scoreMatches(dbg.scores[a.objective], a.range, false)
                    }
                    if (matched == a.negate) return false
                }

                "tag" -> {
                    val has = dbg.tags.contains(a.value)
                    if (if (a.negate) has else !has) return false
                }

                "m" -> {
                    if (dbg.gamemode.isEmpty()) return false
                    val equal = dbg.gamemode == a.value
                    if (if (a.negate) equal else !equal) return false
                }

                "hasitem" -> {
                    val ok = a.items.any { it.item.isNotEmpty() && dbg.items.contains(it.item) }
                    if (!ok) return false
                }

                "type" -> {
                    val has = dbg.types.contains(a.value)
                    if (if (a.negate) has else !has) return false
                }

                "family" -> {
                    val has = dbg.families.contains(a.value)
                    if (if (a.negate) has else !has) return false
                }

                "name" -> {
                    val has = dbg.names.contains(a.value)
                    if (if (a.negate) has else !has) return false
                }

                "c" -> {
                    if (!dbg.counts.contains(a.value)) return false
                }

                "r" -> {
                    val d = dbg.distance.toDoubleOrNull() ?: return false
                    if (d > (a.value.toDoubleOrNull() ?: 0.0)) return false
                }

                "rm" -> {
                    val d = dbg.distance.toDoubleOrNull() ?: return false
                    if (d < (a.value.toDoubleOrNull() ?: 0.0)) return false
                }

                "l" -> {
                    val lv = dbg.level.toDoubleOrNull() ?: return false
                    if (lv > (a.value.toDoubleOrNull() ?: 0.0)) return false
                }

                "lm" -> {
                    val lv = dbg.level.toDoubleOrNull() ?: return false
                    if (lv < (a.value.toDoubleOrNull() ?: 0.0)) return false
                }

                // 自定义布尔参数：按调试开关（boolFlags 命中=开）参与命中
                else -> {
                    val b = a.value.trim()
                    if (b != "true" && b != "false") continue
                    val on = dbg.boolFlags.contains(a.key)
                    val matched = if (b == "true") on else !on
                    if (if (a.negate) matched else !matched) return false
                }
            }
        }
        return true
    }

    private class Acc {
        val condScores = mutableSetOf<String>()
        val displayScores = mutableSetOf<String>()
        val tags = mutableSetOf<String>()
        val items = mutableSetOf<String>()
        val gms = mutableSetOf<String>()
        val types = mutableSetOf<String>()
        val families = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        val counts = mutableSetOf<String>()
        val boolFlags = mutableSetOf<String>()
        var hasDist = false
        var hasLevel = false
        var hasPos = false
        var hasRot = false
        val displaySelectors = mutableSetOf<String>()
        val scoreBounds = mutableMapOf<String, MutableList<Int>>()
    }

    private fun collectSelArgs(sel: RawtextParsedSelector, acc: Acc) {
        for (a in sel.args) {
            when (a.key) {
                "scores" -> {
                    if (a.objective.isNotEmpty()) {
                        acc.condScores.add(a.objective)
                        Regex("-?\\d+").findAll(a.range).forEach {
                            acc.scoreBounds.getOrPut(a.objective) { mutableListOf() }.add(it.value.toInt())
                        }
                    }
                }

                "tag" -> if (a.value.isNotEmpty()) acc.tags.add(a.value)
                "m" -> if (a.value.isNotEmpty()) acc.gms.add(a.value)
                "hasitem" -> a.items.forEach { if (it.item.isNotEmpty()) acc.items.add(it.item) }
                "type" -> if (a.value.isNotEmpty()) acc.types.add(a.value)
                "family" -> if (a.value.isNotEmpty()) acc.families.add(a.value)
                "name" -> if (a.value.isNotEmpty()) acc.names.add(a.value)
                "c" -> if (a.value.isNotBlank()) acc.counts.add(a.value)
                "r", "rm" -> acc.hasDist = true
                "l", "lm" -> acc.hasLevel = true
                "x", "y", "z", "dx", "dy", "dz" -> acc.hasPos = true
                "rx", "rxm", "ry", "rym" -> acc.hasRot = true
                // 未知参数值为 true/false → 视为自定义布尔参数开关
                else -> {
                    val v = a.value.trim()
                    if (v == "true" || v == "false") acc.boolFlags.add(a.key)
                }
            }
        }
    }

    fun collect(elements: List<RawtextElement>): RawtextDebugTargets {
        val acc = Acc()
        fun walk(arr: List<RawtextElement>) {
            for (e in arr) {
                when (e) {
                    is RawtextElement.Selector -> {
                        val sel = RawtextSelectorParser.parse(e.rawSelector)
                        if (sel.args.isEmpty()) acc.displaySelectors.add(e.rawSelector.trim())
                        else collectSelArgs(sel, acc)
                    }

                    is RawtextElement.Score -> if (e.objective.isNotEmpty()) acc.displayScores.add(e.objective)
                    is RawtextElement.Translate -> if (e.withMode == RawtextWithMode.Rawtext) walk(e.raw)
                    is RawtextElement.Condition -> {
                        if (e.mode == RawtextConditionMode.Sequence) {
                            walk(e.seqSlots)
                            continue
                        }
                        if (e.objective.isNotEmpty()) acc.condScores.add(e.objective)
                        for (b in e.branches) {
                            collectSelArgs(b.sel ?: RawtextParsedSelector(e.target.ifEmpty { "@s" }), acc)
                            walk(b.items)
                        }
                    }

                    else -> {}
                }
            }
        }
        walk(elements)
        acc.condScores.forEach { acc.displayScores.remove(it) }
        val boundsMap = acc.scoreBounds.mapValues { (_, v) ->
            val mn = v.minOrNull() ?: 0
            val mx = v.maxOrNull() ?: 0
            (mn - 1)..(mx + 1)
        }
        val allNums = acc.scoreBounds.values.flatten()
        val globalRange = if (allNums.isEmpty()) null
        else (allNums.minOrNull()!! - 1)..(allNums.maxOrNull()!! + 1)
        return RawtextDebugTargets(
            condScores = acc.condScores.toSet(),
            displayScores = acc.displayScores.toSet(),
            tags = acc.tags.toSet(),
            items = acc.items.toSet(),
            gms = acc.gms.toSet(),
            types = acc.types.toSet(),
            families = acc.families.toSet(),
            names = acc.names.toSet(),
            counts = acc.counts.toSet(),
            boolFlags = acc.boolFlags.toSet(),
            hasDist = acc.hasDist,
            hasLevel = acc.hasLevel,
            hasPos = acc.hasPos,
            hasRot = acc.hasRot,
            displaySelectors = acc.displaySelectors.toSet(),
            scoreBounds = boundsMap,
            globalScoreRange = globalRange,
        )
    }
}
