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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.R
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.Surface
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text

/** 调试面板 + 实时预览 */
@Composable
fun RawtextDebugScreen(viewModel: RawtextViewModel, targets: RawtextDebugTargets) {
    viewModel.revision
    val dbg = viewModel.debug
    val preview = remember(viewModel.revision) { RawtextPreviewEngine.render(viewModel.elements, dbg) }
    val onTouch: () -> Unit = { viewModel.touch(); viewModel.persistDebug() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(0.6f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            item {
                RawtextSectionLabel(stringResource(R.string.layout_rawtext_debug_title))
                RawtextHint(stringResource(R.string.layout_rawtext_debug_hint))
            }
            if (targets.condScores.isNotEmpty()) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_scores)) {
                        RawtextHint(stringResource(R.string.layout_rawtext_debug_scores_hint))
                        targets.condScores.sorted().forEach { o -> RawtextScoreRow(o, dbg, onTouch, range = targets.scoreBounds[o] ?: targets.globalScoreRange ?: 0..100) }
                    }
                }
            }
            if (targets.tags.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_tags)) { RawtextToggleGroup(targets.tags.sorted(), dbg.tags, onTouch) } }
            if (targets.items.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_items)) { RawtextToggleGroup(targets.items.sorted(), dbg.items, onTouch) } }
            if (targets.types.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_types)) { RawtextToggleGroup(targets.types.sorted(), dbg.types, onTouch) } }
            if (targets.families.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_families)) { RawtextToggleGroup(targets.families.sorted(), dbg.families, onTouch) } }
            if (targets.names.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_names)) { RawtextToggleGroup(targets.names.sorted(), dbg.names, onTouch) } }
            if (targets.counts.isNotEmpty()) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_counts)) { RawtextToggleGroup(targets.counts.sorted(), dbg.counts, onTouch, prefix = "c=") } }
            if (targets.boolFlags.isNotEmpty()) {
                item {
                    RawtextGroupCard("自定义开关") {
                        targets.boolFlags.sorted().forEach { k ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = k,
                                    modifier = Modifier.weight(1f),
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = CHelperTheme.colors.textMain),
                                )
                                Switch(
                                    checked = dbg.boolFlags.contains(k),
                                    onCheckedChange = { on ->
                                        if (on) dbg.boolFlags.add(k) else dbg.boolFlags.remove(k)
                                        onTouch()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (targets.gms.isNotEmpty()) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_gamemode)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("" to "未设置", "survival" to "生存", "creative" to "创造", "adventure" to "冒险", "spectator" to "旁观").forEach { (v, l) ->
                                RawtextChip(l, dbg.gamemode == v, onClick = { dbg.gamemode = v; onTouch() })
                            }
                        }
                    }
                }
            }
            if (targets.hasDist) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_distance)) { RawtextNumRow("距离", dbg.distance, { dbg.distance = it; onTouch() }) } }
            if (targets.hasLevel) item { RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_level)) { RawtextNumRow("等级", dbg.level, { dbg.level = it; onTouch() }) } }
            if (targets.hasPos) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_pos)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RawtextNumRow("X", dbg.posX, { dbg.posX = it; onTouch() })
                            RawtextNumRow("Y", dbg.posY, { dbg.posY = it; onTouch() })
                            RawtextNumRow("Z", dbg.posZ, { dbg.posZ = it; onTouch() })
                        }
                    }
                }
            }
            if (targets.hasRot) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_rotation)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RawtextNumRow("俯仰 rx", dbg.pitch, { dbg.pitch = it; onTouch() }, range = -90..90)
                            RawtextNumRow("偏航 ry", dbg.yaw, { dbg.yaw = it; onTouch() }, range = -180..180)
                        }
                    }
                }
            }
            if (targets.displayScores.isNotEmpty()) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_display_scores)) {
                        RawtextHint(stringResource(R.string.layout_rawtext_debug_display_scores_hint))
                        targets.displayScores.sorted().forEach { o -> RawtextScoreRow(o, dbg, onTouch, range = 0..32767) }
                    }
                }
            }
            if (targets.displaySelectors.isNotEmpty()) {
                item {
                    RawtextGroupCard(stringResource(R.string.layout_rawtext_debug_display_selectors)) {
                        RawtextHint(stringResource(R.string.layout_rawtext_debug_display_selectors_hint))
                        targets.displaySelectors.sorted().forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = !dbg.hiddenSelectors.contains(s),
                                    onCheckedChange = { on ->
                                        if (on) dbg.hiddenSelectors.remove(s) else dbg.hiddenSelectors.add(s)
                                        onTouch()
                                    },
                                )
                                Text(s, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }
        RawtextPreviewPanel(viewModel, preview, Modifier.weight(0.4f))
    }
}

/** 调试面板的小节分组卡片 */
@Composable
private fun RawtextGroupCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        clipCornerSize = 10.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 8.dp,
    ) {
        Column {
            RawtextSectionLabel(title)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** 简易拖动条（Canvas + 拖动手势） */
@Composable
private fun RawtextSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbColor = CHelperTheme.colors.mainColor
        val trackColor = CHelperTheme.colors.mainColor.copy(alpha = 0.3f)
        fun update(x: Float) {
            val f = (x / widthPx).coerceIn(0f, 1f)
            onValueChange(range.start + f * (range.endInclusive - range.start))
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(range) {
                detectDragGestures(
                    onDragStart = { update(it.x) },
                    onDrag = { change, _ -> change.consume(); update(change.position.x) },
                )
            },
        ) {
            val y = size.height / 2f
            drawLine(trackColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx())
            val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            drawCircle(thumbColor, radius = 8.dp.toPx(), center = Offset(size.width * f, y))
        }
    }
}

/** 计分板行：单值 / 数组两种模式 */
@Composable
private fun RawtextScoreRow(o: String, dbg: RawtextDebugState, onTouch: () -> Unit, range: IntRange = 0..100) {
    val isArray = dbg.arrayScores.contains(o)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RawtextChip("单值", !isArray, onClick = { dbg.arrayScores.remove(o); onTouch() })
            Spacer(Modifier.width(4.dp))
            RawtextChip("数组", isArray, onClick = { dbg.arrayScores.add(o); onTouch() })
        }
        Spacer(Modifier.height(4.dp))
        if (isArray) {
            val expr = dbg.arrayScoreText[o] ?: ""
            val err = RawtextScoreArray.validate(expr)
            RawtextTextField(expr, { dbg.arrayScoreText[o] = it; onTouch() }, Modifier.fillMaxWidth(), placeholder = "如 1,3,8 / 10..100 / 1.. / ..-1")
            if (err != null) {
                Text(err, style = TextStyle(color = CHelperTheme.colors.textErrorReason, fontSize = 12.sp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RawtextTextField(dbg.scores[o] ?: "0", { dbg.scores[o] = it; onTouch() }, Modifier.width(80.dp))
                Spacer(Modifier.width(6.dp))
                RawtextSlider(
                    value = (dbg.scores[o]?.toFloatOrNull() ?: 0f).coerceIn(range.first.toFloat(), range.last.toFloat()),
                    range = range.first.toFloat()..range.last.toFloat(),
                    onValueChange = { dbg.scores[o] = it.toInt().toString(); onTouch() },
                    modifier = Modifier.weight(1f).height(32.dp).padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** 数值行（可选滑块） */
@Composable
private fun RawtextNumRow(label: String, value: String, onSet: (String) -> Unit, range: IntRange? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(fontSize = 13.sp))
        Spacer(Modifier.width(6.dp))
        RawtextTextField(value, onSet, Modifier.width(80.dp))
        if (range != null) {
            Spacer(Modifier.width(8.dp))
            RawtextSlider(
                value = (value.toFloatOrNull() ?: 0f).coerceIn(range.first.toFloat(), range.last.toFloat()),
                range = range.first.toFloat()..range.last.toFloat(),
                onValueChange = { onSet(it.toInt().toString()) },
                modifier = Modifier.weight(1f).height(32.dp).padding(horizontal = 8.dp),
            )
        }
    }
}

/** 开关组（可选文本前缀，如数量的 c=） */
@Composable
private fun RawtextToggleGroup(values: List<String>, set: MutableList<String>, onChange: () -> Unit, prefix: String = "") {
    if (values.isEmpty()) return
    values.forEach { v ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = set.contains(v),
                onCheckedChange = { on -> if (on) set.add(v) else set.remove(v); onChange() },
            )
            Text(prefix + v, style = TextStyle(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
    }
}
