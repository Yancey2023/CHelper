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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.CustomDialogProperties
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.widget.Text

/*
 * 条件块结构化编辑器。对应网页版 getConditionalEditorContent。
 * IF 段三类型：selector / score / rawjson；THEN 段为 rawtext 数组。
 * selector 和 score 的计分对象都能拉起选择器编辑器联动填值。
 */
@Composable
fun ConditionalEditorDialog(
    initialConditionJson: String,
    initialThenJson: String,
    items: List<NamedEntry>,
    slots: List<NamedEntry>,
    onApply: (conditionJson: String, thenJson: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { ConditionEditorState.fromJson(initialConditionJson) }
    var type by remember { mutableStateOf(state.type) }
    var selector by remember { mutableStateOf(state.selector) }
    var scoreName by remember { mutableStateOf(state.scoreName) }
    var scoreObjective by remember { mutableStateOf(state.scoreObjective) }
    var scoreValue by remember { mutableStateOf(state.scoreValue) }
    var rawJson by remember {
        mutableStateOf(state.rawJson.ifBlank { initialConditionJson })
    }
    var thenJson by remember { mutableStateOf(initialThenJson) }

    // 选择器子编辑器：targetField 决定回填到哪个输入框
    var selectorEditorTarget by remember { mutableStateOf<String?>(null) }

    fun composeConditionJson(): String {
        val editor = ConditionEditorState(
            type = type,
            selector = selector,
            scoreName = scoreName,
            scoreObjective = scoreObjective,
            scoreValue = scoreValue,
            rawJson = rawJson,
        )
        return editor.toConditionJson()
    }

    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            backgroundNoTranslate = true,
            cornerSize = 22.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "编辑条件块",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // IF 段
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CHelperTheme.colors.background)
                            .padding(12.dp)
                    ) {
                        RawtextSectionLabel("IF（条件）")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RawtextChipButton(
                                text = "选择器",
                                color = CHelperTheme.colors.mainColor,
                                selected = type == ConditionType.Selector,
                                modifier = Modifier.weight(1f),
                                onClick = { type = ConditionType.Selector }
                            )
                            RawtextChipButton(
                                text = "计分板",
                                color = CHelperTheme.colors.mainColor,
                                selected = type == ConditionType.Score,
                                modifier = Modifier.weight(1f),
                                onClick = { type = ConditionType.Score }
                            )
                            RawtextChipButton(
                                text = "RawJSON",
                                color = CHelperTheme.colors.mainColor,
                                selected = type == ConditionType.RawJson,
                                modifier = Modifier.weight(1f),
                                onClick = { type = ConditionType.RawJson }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        when (type) {
                            ConditionType.Selector -> RawtextLabeledField(
                                label = "选择器",
                                value = selector,
                                onValueChange = { selector = it },
                                hint = "@p[tag=vip]",
                                trailing = {
                                    RawtextSmallIconButton(
                                        text = "编辑",
                                        color = CHelperTheme.colors.mainColor,
                                        onClick = { selectorEditorTarget = "selector" }
                                    )
                                }
                            )

                            ConditionType.Score -> {
                                RawtextLabeledField(
                                    label = "计分对象",
                                    value = scoreName,
                                    onValueChange = { scoreName = it },
                                    hint = "@p, 玩家名...",
                                    trailing = {
                                        RawtextSmallIconButton(
                                            text = "编辑",
                                            color = CHelperTheme.colors.mainColor,
                                            onClick = { selectorEditorTarget = "scoreName" }
                                        )
                                    }
                                )
                                RawtextLabeledField(
                                    label = "计分项",
                                    value = scoreObjective,
                                    onValueChange = { scoreObjective = it },
                                    hint = "money, score..."
                                )
                                RawtextLabeledField(
                                    label = "分数 (支持 10 / 5.. / ..15 / 10..12 / !10)",
                                    value = scoreValue,
                                    onValueChange = { scoreValue = it },
                                    hint = "10..12"
                                )
                            }

                            ConditionType.RawJson -> {
                                RawtextLabeledField(
                                    label = "RawJSON 条件",
                                    value = rawJson,
                                    onValueChange = { rawJson = it },
                                    hint = "{\"selector\":\"@a[tag=admin]\"}",
                                    minHeight = 96.dp
                                )
                                Text(
                                    text = "填一个合法 JSON 对象作为条件；非法时生成区会兜底。",
                                    style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // THEN 段
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CHelperTheme.colors.background)
                            .padding(12.dp)
                    ) {
                        RawtextSectionLabel("THEN（结果）")
                        Spacer(Modifier.height(8.dp))
                        RawtextLabeledField(
                            label = "Rawtext JSON 数组",
                            value = thenJson,
                            onValueChange = { thenJson = it },
                            hint = "[{\"text\":\"You are a VIP!\"}]",
                            minHeight = 110.dp
                        )
                        Text(
                            text = "条件成立时显示的 rawtext 数组。",
                            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawtextFilledButton(
                        text = "取消",
                        color = CHelperTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    RawtextFilledButton(
                        text = "保存",
                        color = CHelperTheme.colors.mainColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onApply(composeConditionJson(), thenJson) }
                    )
                }
            }
        }
    }

    selectorEditorTarget?.let { target ->
        val initial = if (target == "selector") selector else scoreName
        SelectorEditorDialog(
            initialSelector = initial.ifBlank { "@p" },
            items = items,
            slots = slots,
            onApply = { result ->
                if (target == "selector") selector = result else scoreName = result
                selectorEditorTarget = null
            },
            onDismiss = { selectorEditorTarget = null }
        )
    }
}
