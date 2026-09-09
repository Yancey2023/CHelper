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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** translate 的 with 参数形态：文本参数数组 / 嵌套 rawtext 元素 */
enum class RawtextWithMode {
    Array,
    Rawtext
}

/** 条件元素形态：多分支菜单 / 顺序拼接 */
enum class RawtextConditionMode {
    Branch,
    Sequence
}

/**
 * 原始 JSON 文本（titleraw）的元素树。
 * 与旧版「扁平段流」不同，这里用可嵌套的树结构，天然支持 translate 嵌套、
 * 条件分支与顺序拼接，且可直接与 JSON 结构一一对应。
 */
@Serializable
sealed class RawtextElement {
    /** UI 折叠状态（切换页面后保留，不参与 JSON 导入导出与草稿持久化） */
    @Transient
    var uiExpanded: Boolean = false

    @Serializable
    @SerialName("text")
    data class Text(var text: String) : RawtextElement()

    @Serializable
    @SerialName("translate")
    data class Translate(
        var key: String,
        var withMode: RawtextWithMode,
        var args: MutableList<String>,
        var raw: MutableList<RawtextElement>,
    ) : RawtextElement() {
        constructor() : this("translation.key", RawtextWithMode.Rawtext, mutableListOf(), mutableListOf())
    }

    @Serializable
    @SerialName("selector")
    data class Selector(var rawSelector: String) : RawtextElement()

    @Serializable
    @SerialName("score")
    data class Score(var name: String, var objective: String) : RawtextElement()

    @Serializable
    @SerialName("condition")
    data class Condition(
        var mode: RawtextConditionMode,
        var hasBlank: Boolean,
        var branches: MutableList<RawtextBranch>,
        var seqKey: String,
        var seqSlots: MutableList<RawtextElement>,
        var objective: String = "",
        var target: String = "@s",
    ) : RawtextElement() {
        constructor() : this(
            RawtextConditionMode.Branch,
            true,
            mutableListOf(RawtextBranch(), RawtextBranch()),
            "",
            mutableListOf()
        )
    }
}

/** 条件分支：选择器（可空 = 空白分支）+ 内容（元素组，纯文本以单个文字元素保留） */
@Serializable
data class RawtextBranch(
    var selectorRaw: String? = null,
    var sel: RawtextParsedSelector? = null,
    var items: MutableList<RawtextElement> = mutableListOf(),
)
