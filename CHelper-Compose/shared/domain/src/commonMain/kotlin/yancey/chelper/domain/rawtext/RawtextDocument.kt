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

package yancey.chelper.domain.rawtext

import kotlinx.serialization.Serializable

enum class RawtextSegmentType {
    Text,
    LineBreak,
    Score,
    Selector,
    Translate,
    Conditional,
}

@Serializable
data class RawtextSegmentSnapshot(
    val type: String,
    val text: String = "",
    val scoreName: String = "@p",
    val scoreObjective: String = "score",
    val selector: String = "@p",
    val translateKey: String = "key.example",
    val translateWithJson: String = "[]",
    val conditionJson: String = "{}",
    val thenJson: String = "[]",
) {
    val kind
        get() =
            runCatching { RawtextSegmentType.valueOf(type) }.getOrDefault(RawtextSegmentType.Text)
}

@Serializable
data class RawtextMockSnapshot(
    val p: String = "Steve",
    val r: String = "Alex",
    val a: String = "Steve, Alex, ...",
    val s: String = "执行者",
    val score: String = "100",
    val translate: String = "翻译文本",
    val condition: String = "条件内容",
)

data class RawtextDocumentSnapshot(
    val segments: List<RawtextSegmentSnapshot>,
    val mock: RawtextMockSnapshot,
)
