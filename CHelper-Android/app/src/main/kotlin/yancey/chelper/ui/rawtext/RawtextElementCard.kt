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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.Surface
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text

/** 可添加的元素类型（顶层「添加元素」与嵌套共用） */
enum class RawtextAddKind(val label: String, val desc: String) {
    Text("文本", "直接显示一段文本（支持 § 颜色）"),
    Translate("翻译", "把翻译识别符显示成对应语言文本"),
    Selector("选择器", "显示被选中玩家的名字"),
    Score("计分板", "显示玩家在某计分板上的分数"),
    Condition("条件菜单", "多个选项按选择器命中情况显示"),
    Sequence("顺序拼接", "把槽位按 %%1%%2… 排列显示"),
}

fun createElement(kind: RawtextAddKind): RawtextElement = when (kind) {
    RawtextAddKind.Text -> RawtextElement.Text("")
    RawtextAddKind.Translate -> RawtextElement.Translate().apply { key = "" }
    RawtextAddKind.Selector -> RawtextElement.Selector("")
    RawtextAddKind.Score -> RawtextElement.Score("", "")
    RawtextAddKind.Condition -> RawtextElement.Condition()
    RawtextAddKind.Sequence -> RawtextElement.Condition().apply { mode = RawtextConditionMode.Sequence }
}

/** 各元素类型的专属颜色（与原编辑器一致） */
private fun kindColor(el: RawtextElement): Color = when (el) {
    is RawtextElement.Text -> Color(0xFF7CE87C)
    is RawtextElement.Translate -> Color(0xFF5FD6F5)
    is RawtextElement.Selector -> Color(0xFFFFB84D)
    is RawtextElement.Score -> Color(0xFFC792EA)
    is RawtextElement.Condition -> if (el.mode == RawtextConditionMode.Sequence) Color(0xFF64B5F6) else Color(0xFFF06292)
}

/** 层级连接线颜色（随深度循环：同一层级统一、不同层级区分） */
private fun connectorColor(depth: Int): Color = when (depth % 3) {
    0 -> Color(0xFF5FD6F5)
    1 -> Color(0xFFFFB84D)
    else -> Color(0xFFC792EA)
}

/** 各元素类型对应的图标 */
private fun kindIconRes(el: RawtextElement): Int = when (el) {
    is RawtextElement.Text -> R.drawable.ic_rawtext_text
    is RawtextElement.Translate -> R.drawable.ic_rawtext_translate
    is RawtextElement.Selector -> R.drawable.ic_rawtext_selector
    is RawtextElement.Score -> R.drawable.ic_rawtext_score
    is RawtextElement.Condition -> if (el.mode == RawtextConditionMode.Sequence) R.drawable.ic_rawtext_sequence else R.drawable.ic_rawtext_condition
}

private fun kindName(el: RawtextElement): String = when (el) {
    is RawtextElement.Text -> "文本"
    is RawtextElement.Translate -> "翻译"
    is RawtextElement.Selector -> "选择器"
    is RawtextElement.Score -> "计分板"
    is RawtextElement.Condition -> if (el.mode == RawtextConditionMode.Sequence) "顺序拼接" else "条件菜单"
}

private fun elSummary(el: RawtextElement): String = when (el) {
    is RawtextElement.Text -> if (el.text.isBlank()) "显示空白文本" else "显示：" + el.text
    is RawtextElement.Translate -> "翻译「" + el.key + "」" + if (el.withMode == RawtextWithMode.Array) "，参数 ×" + el.args.size else "，嵌套 ×" + el.raw.size
    is RawtextElement.Selector -> "目标：" + el.rawSelector
    is RawtextElement.Score -> "显示「" + el.name + "」的「" + el.objective + "」分数"
    is RawtextElement.Condition -> if (el.mode == RawtextConditionMode.Sequence) {
        "按 " + (if (el.seqKey.isNotBlank()) el.seqKey else "%%1%%2…") + " 排列（" + el.seqSlots.size + " 槽位）"
    } else {
        val n = el.branches.size - (if (el.hasBlank) 1 else 0)
        n.toString() + " 选项" + (if (el.hasBlank) " + 否则" else "")
    }
}.replace("\n", "\\n")

/** 元素帮助说明（问号按钮弹出） */
private fun elHelp(el: RawtextElement): String = when (el) {
    is RawtextElement.Text -> "§ 开头是颜色/样式代码：§e 黄、§l 粗、§o 斜、§k 乱码、§r 重置。点下方色块/按钮插入末尾。"
    is RawtextElement.Translate -> "翻译识别符显示成对应语言文本，如 item.diamond.name → 钻石。"
    is RawtextElement.Selector -> "显示被选中玩家的名字：@p 最近、@a 全部、@s[tag=会员] 带标签。"
    is RawtextElement.Score -> "显示某玩家在某计分板上的分数，如「金币：5」。"
    is RawtextElement.Condition -> if (el.mode == RawtextConditionMode.Sequence) {
        "拼接 %%1%%2 按槽位顺序显示。"
    } else {
        "条件菜单 %%N：过滤掉未命中的选择器后顺位显示第 N 个；超过 8 项导出会自动嵌套 %%9。"
    }
}

private fun elHasContent(el: RawtextElement): Boolean = when (el) {
    is RawtextElement.Text -> el.text.isNotBlank()
    is RawtextElement.Translate -> el.key.isNotBlank() || el.args.isNotEmpty() || el.raw.isNotEmpty()
    is RawtextElement.Selector -> el.rawSelector.isNotBlank()
    is RawtextElement.Score -> el.name.isNotBlank() || el.objective.isNotBlank()
    is RawtextElement.Condition -> el.seqKey.isNotBlank() || el.seqSlots.isNotEmpty() ||
        el.branches.any { b -> b.items.isNotEmpty() || !b.selectorRaw.isNullOrBlank() }
}

private fun branchHasContent(b: RawtextBranch): Boolean =
    b.items.isNotEmpty() || !b.selectorRaw.isNullOrBlank()

/** 行内删除图标（红色 ✕，用于参数/分支/选项） */
@Composable
private fun RawtextRemoveIcon(onClick: () -> Unit) {
    Text(
        "✕",
        style = TextStyle(fontSize = 14.sp, color = Color(0xFFFF7A7A)),
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    )
}

/** 元素卡片：左侧连接线 + 彩色图标 + 类型 + 摘要；嵌套时缩进并显示连接线 */
@Composable
fun RawtextElementCard(
    el: RawtextElement,
    targets: RawtextDebugTargets,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    showMove: Boolean = true,
    depth: Int = 0,
) {
    val nested = depth > 0
    var expanded by remember { mutableStateOf(el.uiExpanded) }
    val confirm = LocalRawtextConfirm.current
    val help = LocalRawtextHelp.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (nested) 1.dp else 3.dp).height(IntrinsicSize.Max),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(connectorColor(depth)))
        Spacer(Modifier.width(4.dp))
        Surface(
            modifier = Modifier.weight(1f),
            clipCornerSize = if (nested) 8.dp else 12.dp,
            horizontalPadding = if (nested) 8.dp else 12.dp,
            verticalPadding = if (nested) 4.dp else 8.dp,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(if (nested) 24.dp else 32.dp).clip(CircleShape).background(kindColor(el).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(kindIconRes(el)),
                            contentDescription = null,
                            modifier = Modifier.size(if (nested) 14.dp else 18.dp),
                            colorFilter = ColorFilter.tint(kindColor(el)),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(kindName(el), style = TextStyle(fontSize = if (nested) 14.sp else 15.sp, fontWeight = FontWeight.Bold, color = kindColor(el)))
                            Icon(
                                R.drawable.help_circle,
                                Modifier.clickable { help?.invoke(kindName(el) + " · 帮助", elHelp(el)) }.padding(start = 4.dp).size(if (nested) 14.dp else 16.dp),
                                "帮助",
                            )
                        }
                        Text(
                            summaryText(elSummary(el)),
                            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showMove) {
                        Icon(R.drawable.chevron_up, Modifier.clickable { onMove(-1) }.padding(4.dp).size(20.dp), "上移")
                        Icon(R.drawable.chevron_down, Modifier.clickable { onMove(1) }.padding(4.dp).size(20.dp), "下移")
                    }
                    Icon(
                        R.drawable.x,
                        Modifier.clickable {
                            if (elHasContent(el)) confirm?.invoke(RawtextConfirmRequest("确认删除", "确定删除该元素吗？删除后无法撤销。", onRemove)) else onRemove()
                        }.padding(4.dp).size(20.dp),
                        "删除",
                    )
                    Text(
                        if (expanded) "收起" else "展开",
                        style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.mainColor),
                        modifier = Modifier.clickable {
                            expanded = !expanded
                            el.uiExpanded = expanded
                        }.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(CHelperTheme.colors.line))
                    Spacer(Modifier.height(6.dp))
                    when (el) {
                        is RawtextElement.Text -> RawtextTextEditor(el, onChanged)
                        is RawtextElement.Translate -> RawtextTranslateEditor(el, targets, onChanged)
                        is RawtextElement.Selector -> RawtextSelectorEditor(el, targets, onChanged)
                        is RawtextElement.Score -> RawtextScoreEditor(el, targets, onChanged)
                        is RawtextElement.Condition -> RawtextConditionEditor(el, targets, onChanged, depth)
                    }
                }
            }
        }
    }
}

/** 文本元素：文本框 + § 样式快捷按钮 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawtextTextEditor(el: RawtextElement.Text, onChanged: () -> Unit) {
    var ta by remember { mutableStateOf(el.text) }
    var editing by remember { mutableStateOf(false) }
    BasicTextField(
        value = if (editing) ta.replace("\n", "\\n") else ta,
        onValueChange = {
            val v = if (editing) it.replace("\\n", "\n") else it
            ta = v
            el.text = v
            onChanged()
        },
        singleLine = editing,
        maxLines = if (editing) 1 else 5,
        minLines = 1,
        textStyle = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
        modifier = Modifier.fillMaxWidth().onFocusChanged { editing = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(CHelperTheme.colors.background).padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (ta.isEmpty()) {
                    Text("要显示的文本（\\n 代表换行）", style = TextStyle(color = CHelperTheme.colors.textHint, fontSize = 14.sp))
                }
                inner()
            }
        },
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (CLASSIC_COLORS + EXTENDED_COLORS).toList().sortedBy { it.first }.forEach { (c, color) ->
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .clickable { ta += "§" + c; el.text = ta; onChanged() },
                contentAlignment = Alignment.Center,
            ) {
                Text("§" + c, style = TextStyle(fontSize = 11.sp))
            }
        }
        listOf("l" to "加粗", "o" to "斜体", "k" to "乱码", "r" to "重置").forEach { (c, n) ->
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CHelperTheme.colors.backgroundComponent)
                    .clickable { ta += "§" + c; el.text = ta; onChanged() },
                contentAlignment = Alignment.Center,
            ) {
                Text("§" + c, style = TextStyle(fontSize = 11.sp))
            }
        }
        RawtextSmallButton("↲换行", onClick = { ta += "\n"; el.text = ta; onChanged() })
    }
}

/** 选择器元素 */
@Composable
private fun RawtextSelectorEditor(el: RawtextElement.Selector, targets: RawtextDebugTargets, onChanged: () -> Unit) {
    RawtextSelectorField(el.rawSelector, { el.rawSelector = it; onChanged() }, targets, "玩家 / 目标选择器")
}

/** 翻译元素 */
@Composable
private fun RawtextTranslateEditor(el: RawtextElement.Translate, targets: RawtextDebugTargets, onChanged: () -> Unit) {
    RawtextKeyField(el.key, { el.key = it; onChanged() }, "翻译识别符（自动补全）")
    Spacer(Modifier.height(4.dp))
    val confirm = LocalRawtextConfirm.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RawtextChip(
            "with = 嵌套元素",
            el.withMode == RawtextWithMode.Rawtext,
            onClick = {
                if (el.withMode != RawtextWithMode.Rawtext && el.args.isNotEmpty()) {
                    confirm?.invoke(RawtextConfirmRequest("确认切换", "切换后当前的文本参数会被清空，确定切换吗？") {
                        el.withMode = RawtextWithMode.Rawtext
                        onChanged()
                    })
                } else {
                    el.withMode = RawtextWithMode.Rawtext
                    onChanged()
                }
            },
        )
        RawtextChip(
            "with = 文本参数",
            el.withMode == RawtextWithMode.Array,
            onClick = {
                if (el.withMode != RawtextWithMode.Array && el.raw.isNotEmpty()) {
                    confirm?.invoke(RawtextConfirmRequest("确认切换", "切换后当前的嵌套元素会被清空，确定切换吗？") {
                        el.withMode = RawtextWithMode.Array
                        onChanged()
                    })
                } else {
                    el.withMode = RawtextWithMode.Array
                    onChanged()
                }
            },
        )
    }
    Spacer(Modifier.height(4.dp))
    if (el.withMode == RawtextWithMode.Array) {
        RawtextHint("参数按顺序填入翻译文本的 %1\$s、%2\$s…")
        el.args.indices.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("参数 " + (i + 1), style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
                Spacer(Modifier.weight(1f))
                RawtextRemoveIcon(onClick = {
                    if (el.args[i].isNotBlank()) {
                        confirm?.invoke(RawtextConfirmRequest("确认删除", "确定删除该参数吗？删除后无法撤销。") {
                            el.args.removeAt(i); onChanged()
                        })
                    } else {
                        el.args.removeAt(i); onChanged()
                    }
                })
            }
            RawtextTextField(el.args[i], { el.args[i] = it; onChanged() }, Modifier.fillMaxWidth())
        }
        RawtextSmallButton("+ 参数", onClick = { el.args.add(""); onChanged() })
    } else {
        RawtextHint("嵌套元素按顺序拼在翻译文本后显示。")
        RawtextNestedElements(el.raw, targets, onChanged, depth = 1)
    }
}

/** 计分板元素 */
@Composable
private fun RawtextScoreEditor(el: RawtextElement.Score, targets: RawtextDebugTargets, onChanged: () -> Unit) {
    RawtextSelectorField(el.name, { el.name = it; onChanged() }, targets, "玩家名 / 目标选择器（name）")
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RawtextSmallButton("自己 @s", onClick = { el.name = "@s"; onChanged() })
        RawtextSmallButton("所有玩家 *", onClick = { el.name = "*"; onChanged() })
    }
    Spacer(Modifier.height(4.dp))
    RawtextObjectiveField(el.objective, { el.objective = it; onChanged() }, targets, "计分板名称（objective）")
}

/** 条件元素（分支 / 拼接） */
@Composable
private fun RawtextConditionEditor(el: RawtextElement.Condition, targets: RawtextDebugTargets, onChanged: () -> Unit, depth: Int) {
    if (el.mode == RawtextConditionMode.Sequence) {
        RawtextHint("拼接顺序（如 %%1%%2，可混排文字）")
        RawtextTextField(el.seqKey, { el.seqKey = it; onChanged() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        RawtextHint("槽位内容（%%1、%%2… 按顺序拼接，可重复引用）：")
        RawtextNestedElements(el.seqSlots, targets, onChanged, depth = depth + 1)
    } else {
        val confirm = LocalRawtextConfirm.current
        val blankIdx = if (el.hasBlank) el.branches.lastIndex else -1
        RawtextSectionLabel("选择器（命中条件）")
        Spacer(Modifier.height(4.dp))
        el.branches.forEachIndexed { i, b ->
            if (i != blankIdx) {
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(CHelperTheme.colors.backgroundComponentNoTranslate).border(1.dp, CHelperTheme.colors.mainColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CHelperTheme.colors.mainColor), modifier = Modifier.width(20.dp))
                        Box(Modifier.weight(1f)) {
                            RawtextSelectorField(
                                b.selectorRaw ?: (b.sel?.let { RawtextSelectorParser.string(it) } ?: ""),
                                { b.selectorRaw = it; b.sel = RawtextSelectorParser.parse(it); onChanged() },
                                targets,
                                "条件 ${i + 1} 的选择器（命中保留，未命中过滤）",
                            )
                        }
                        RawtextRemoveIcon(onClick = {
                            if (branchHasContent(b)) {
                                confirm?.invoke(RawtextConfirmRequest("确认删除", "确定删除该选项吗？删除后无法撤销。") {
                                    el.branches.removeAt(i); onChanged()
                                })
                            } else {
                                el.branches.removeAt(i); onChanged()
                            }
                        })
                    }
                }
            }
        }
        RawtextSmallButton("+ 添加选项", onClick = {
            val idx = if (el.hasBlank) el.branches.size - 1 else el.branches.size
            el.branches.add(idx, RawtextBranch())
            onChanged()
        })
        Spacer(Modifier.height(6.dp))
        RawtextSectionLabel("显示内容（过滤掉未命中的选择器后顺位显示第 N 个）")
        Spacer(Modifier.height(4.dp))
        el.branches.forEachIndexed { i, b ->
            RawtextConditionContentRow(el, i, b, targets, onChanged, depth + 1)
        }
        if (el.hasBlank) {
            RawtextSmallButton("✕ 否则", onClick = { el.hasBlank = false; el.branches.removeAt(el.branches.lastIndex); onChanged() })
        } else {
            RawtextSmallButton("+ 否则", onClick = { el.hasBlank = true; el.branches.add(RawtextBranch()); onChanged() })
        }
    }
}

/** 条件菜单：内容区的一行（编号 + 元素组） */
@Composable
private fun RawtextConditionContentRow(el: RawtextElement.Condition, i: Int, b: RawtextBranch, targets: RawtextDebugTargets, onChanged: () -> Unit, depth: Int) {
    val isBlank = el.hasBlank && i == el.branches.lastIndex
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(CHelperTheme.colors.backgroundComponentNoTranslate).border(1.dp, CHelperTheme.colors.mainColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Column {
            Text(
                if (isBlank) "否则：无任何选择器命中时显示" else "选项 ${i + 1}",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isBlank) CHelperTheme.colors.mainColor else kindColor(el)),
            )
            Spacer(Modifier.height(4.dp))
            RawtextNestedElements(b.items, targets, onChanged, depth = depth + 1, showAdd = true, addInline = true)
        }
    }
}

/** 嵌套元素列表（translate.with / 分支元素组 / 拼接槽位）：层级缩进 + 连接线 */
@Composable
fun RawtextNestedElements(
    list: MutableList<RawtextElement>,
    targets: RawtextDebugTargets,
    onChanged: () -> Unit,
    depth: Int = 0,
    showAdd: Boolean = true,
    addInline: Boolean = false,
) {
    Column {
        list.forEachIndexed { i, e ->
            val card: @Composable () -> Unit = {
                RawtextElementCard(
                    el = e,
                    targets = targets,
                    onChanged = onChanged,
                    onRemove = { list.removeAt(i); onChanged() },
                    onMove = { d -> val j = i + d; if (j in list.indices) { val t = list[i]; list[i] = list[j]; list[j] = t; onChanged() } },
                    depth = depth,
                )
            }
            if (addInline && showAdd && i == list.lastIndex) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { card() }
                    Spacer(Modifier.width(4.dp))
                    RawtextNestedAddButton(onAdd = { el -> list.add(el); onChanged() })
                }
            } else {
                card()
            }
        }
        if (showAdd && (!addInline || list.isEmpty())) {
            Spacer(Modifier.height(4.dp))
            RawtextNestedAddButton(onAdd = { el -> list.add(el); onChanged() })
        }
    }
}

/** 「添加嵌套元素」按钮：下方展开选择要嵌套的元素类型 */
@Composable
fun RawtextNestedAddButton(onAdd: (RawtextElement) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        RawtextSmallButton("+ 嵌套元素", onClick = { open = !open })
        if (open) {
            Surface(Modifier.fillMaxWidth(), clipCornerSize = 8.dp, horizontalPadding = 0.dp, verticalPadding = 4.dp) {
                Column {
                    RawtextAddKind.entries.forEach { kind ->
                        Column(
                            Modifier.fillMaxWidth().clickable { open = false; onAdd(createElement(kind)) }.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(kind.label, style = TextStyle(fontSize = 14.sp))
                            Text(kind.desc, style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary))
                        }
                    }
                }
            }
        }
    }
}
