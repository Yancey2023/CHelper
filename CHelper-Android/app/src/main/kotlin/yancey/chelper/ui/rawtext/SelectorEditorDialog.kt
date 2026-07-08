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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.CustomDialogProperties
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.widget.Text

/*
 * 选择器可视化编辑器。对应网页版 getSelectorModalContent。
 * 双模式：
 *   - 高级模式：拆成 base / 基本 / 坐标距离 / 旋转 / 维度 / tag / 玩家数据 + hasitem/scores 子编辑器
 *   - 手动模式：一个完整选择器字符串输入框
 * 两模式切换时互相同步（解析 / 拼装）。
 * onApply 回传最终选择器字符串。
 */
@Composable
fun SelectorEditorDialog(
    initialSelector: String,
    items: List<NamedEntry>,
    slots: List<NamedEntry>,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember { SelectorParser.parse(initialSelector) }
    var advancedMode by remember {
        mutableStateOf(!initialSelector.matches(Regex("^@[prsaen]$")) || initialSelector.contains("["))
    }
    var manualText by remember { mutableStateOf(initialSelector) }
    var base by remember { mutableStateOf(parsed.base) }
    val fields = remember {
        mutableStateMapOf<String, String>().apply {
            SelectorParser.SIMPLE_FIELDS.forEach { put(it, parsed.params[it] ?: "") }
        }
    }
    val hasitems = remember {
        mutableStateListOf<HasitemCondition>().apply {
            addAll(HasitemCondition.parseValue(parsed.params["hasitem"] ?: ""))
        }
    }
    val scores = remember {
        mutableStateListOf<ScoreCondition>().apply {
            addAll(ScoreCondition.parseValue(parsed.params["scores"] ?: ""))
        }
    }

    var familyPickerVisible by remember { mutableStateOf(false) }
    var itemPickerIndex by remember { mutableStateOf<Int?>(null) }
    var slotPickerIndex by remember { mutableStateOf<Int?>(null) }

    fun buildFromAdvanced(): String {
        val params = linkedMapOf<String, String>()
        SelectorParser.SIMPLE_FIELDS.forEach { key ->
            fields[key]?.takeIf { it.isNotBlank() }?.let { params[key] = it }
        }
        HasitemCondition.buildValue(hasitems).takeIf { it.isNotBlank() }?.let { params["hasitem"] = it }
        ScoreCondition.buildValue(scores).takeIf { it.isNotBlank() }?.let { params["scores"] = it }
        return SelectorParser.build(base, params)
    }

    fun syncToManual() {
        manualText = buildFromAdvanced()
    }

    fun syncFromManual() {
        val p = SelectorParser.parse(manualText)
        base = p.base
        SelectorParser.SIMPLE_FIELDS.forEach { fields[it] = p.params[it] ?: "" }
        hasitems.clear()
        hasitems.addAll(HasitemCondition.parseValue(p.params["hasitem"] ?: ""))
        scores.clear()
        scores.addAll(ScoreCondition.parseValue(p.params["scores"] ?: ""))
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
                    text = "选择器编辑器",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RawtextChipButton(
                        text = "高级模式",
                        color = CHelperTheme.colors.mainColor,
                        selected = advancedMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!advancedMode) {
                            syncFromManual()
                            advancedMode = true
                        }
                    }
                    RawtextChipButton(
                        text = "手动模式",
                        color = CHelperTheme.colors.mainColor,
                        selected = !advancedMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (advancedMode) {
                            syncToManual()
                            advancedMode = false
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (advancedMode) {
                        AdvancedSelectorForm(
                            base = base,
                            onBaseChange = { base = it },
                            fields = fields,
                            hasitems = hasitems,
                            scores = scores,
                            onOpenFamilyPicker = { familyPickerVisible = true },
                            onOpenItemPicker = { itemPickerIndex = it },
                            onOpenSlotPicker = { slotPickerIndex = it },
                        )
                    } else {
                        RawtextLabeledField(
                            label = "完整选择器",
                            value = manualText,
                            onValueChange = { manualText = it },
                            hint = "@a[tag=vip,r=10]",
                            minHeight = 110.dp
                        )
                        Text(
                            text = "直接输入完整选择器字符串，切回高级模式会自动拆解。",
                            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary)
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
                        onClick = {
                            val result = if (advancedMode) buildFromAdvanced() else manualText
                            onApply(result)
                        }
                    )
                }
            }
        }
    }

    if (familyPickerVisible) {
        RawtextSearchDialog(
            title = "选择族类型",
            entries = RawtextDataRepository.familyTypes,
            onPick = {
                fields["family"] = it.id
                familyPickerVisible = false
            },
            onDismiss = { familyPickerVisible = false }
        )
    }

    itemPickerIndex?.let { index ->
        RawtextSearchDialog(
            title = "物品查询",
            entries = items,
            onPick = { entry ->
                hasitems.getOrNull(index)?.let {
                    hasitems[index] = it.copy(item = entry.id)
                }
                itemPickerIndex = null
            },
            onDismiss = { itemPickerIndex = null }
        )
    }

    slotPickerIndex?.let { index ->
        RawtextSearchDialog(
            title = "槽位查询",
            entries = slots,
            onPick = { entry ->
                hasitems.getOrNull(index)?.let {
                    hasitems[index] = it.copy(location = entry.id)
                }
                slotPickerIndex = null
            },
            onDismiss = { slotPickerIndex = null }
        )
    }
}

@Composable
private fun AdvancedSelectorForm(
    base: String,
    onBaseChange: (String) -> Unit,
    fields: MutableMap<String, String>,
    hasitems: MutableList<HasitemCondition>,
    scores: MutableList<ScoreCondition>,
    onOpenFamilyPicker: () -> Unit,
    onOpenItemPicker: (Int) -> Unit,
    onOpenSlotPicker: (Int) -> Unit,
) {
    val baseLabels = mapOf(
        "p" to "@p 最近玩家",
        "r" to "@r 随机玩家",
        "a" to "@a 所有玩家",
        "e" to "@e 所有实体",
        "s" to "@s 执行者",
        "n" to "@n 最近实体",
    )

    SelectorGroup("基本") {
        RawtextSectionLabel("目标")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectorParser.BASE_OPTIONS.take(3).forEach { option ->
                RawtextChipButton(
                    text = "@$option",
                    color = CHelperTheme.colors.mainColor,
                    selected = base == option,
                    modifier = Modifier.weight(1f),
                    onClick = { onBaseChange(option) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectorParser.BASE_OPTIONS.drop(3).forEach { option ->
                RawtextChipButton(
                    text = "@$option",
                    color = CHelperTheme.colors.mainColor,
                    selected = base == option,
                    modifier = Modifier.weight(1f),
                    onClick = { onBaseChange(option) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = baseLabels[base] ?: "",
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
        )
        Spacer(Modifier.height(10.dp))
        SelectorField(fields, "type", "实体类型 (type)", "minecraft:player")
        SelectorField(fields, "name", "名称 (name)", "Steve")
        SelectorField(fields, "c", "数量 (c)", "正数=最近, 负数=最远")
        RawtextLabeledField(
            label = "族 (family)",
            value = fields["family"] ?: "",
            onValueChange = { fields["family"] = it },
            hint = "monster",
            trailing = {
                RawtextSmallIconButton(text = "查", color = CHelperTheme.colors.mainColor, onClick = onOpenFamilyPicker)
            }
        )
    }

    SelectorGroup("坐标与距离") {
        SelectorField(fields, "x", "X 坐标 (x)", "~, 10, ~-5")
        SelectorField(fields, "y", "Y 坐标 (y)", "~, 64, ~10")
        SelectorField(fields, "z", "Z 坐标 (z)", "~, 100, ~-5")
        SelectorField(fields, "r", "最大半径 (r)", "10")
        SelectorField(fields, "rm", "最小半径 (rm)", "1")
    }

    SelectorGroup("旋转角度") {
        SelectorField(fields, "rx", "最大垂直旋转 (rx)", "90")
        SelectorField(fields, "rxm", "最小垂直旋转 (rxm)", "-90")
        SelectorField(fields, "ry", "最大水平旋转 (ry)", "180")
        SelectorField(fields, "rym", "最小水平旋转 (rym)", "-180")
    }

    SelectorGroup("维度选择 (dx/dy/dz)") {
        SelectorField(fields, "dx", "X 维度 (dx)", "10.5")
        SelectorField(fields, "dy", "Y 维度 (dy)", "-5")
        SelectorField(fields, "dz", "Z 维度 (dz)", "20")
        Text(
            text = "定义一个长方体区域，可为负数和小数。未指定 x/y/z 时以执行位置为原点。",
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
        )
    }

    SelectorGroup("标签 (tag)") {
        RawtextLabeledField(
            label = "标签",
            value = fields["tag"] ?: "",
            onValueChange = { fields["tag"] = it },
            hint = "vip 或 !member"
        )
        Text(
            text = "多个标签写多组 tag，这里只支持单值；复杂用法可切手动模式。",
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary)
        )
    }

    SelectorGroup("玩家数据") {
        RawtextSectionLabel("游戏模式 (m)")
        Spacer(Modifier.height(6.dp))
        val modes = listOf("" to "任何", "s" to "生存", "c" to "创造", "a" to "冒险", "d" to "默认")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEach { (value, label) ->
                RawtextChipButton(
                    text = label,
                    color = CHelperTheme.colors.mainColor,
                    selected = (fields["m"] ?: "") == value,
                    modifier = Modifier.weight(1f),
                    onClick = { fields["m"] = value }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SelectorField(fields, "lm", "最小等级 (lm)", "10")
        SelectorField(fields, "l", "最大等级 (l)", "50")
    }

    SelectorGroup("物品栏 (hasitem)") {
        hasitems.forEachIndexed { index, condition ->
            HasitemConditionItem(
                index = index,
                condition = condition,
                onChange = { hasitems[index] = it },
                onRemove = { hasitems.removeAt(index) },
                onOpenItemPicker = { onOpenItemPicker(index) },
                onOpenSlotPicker = { onOpenSlotPicker(index) },
            )
            Spacer(Modifier.height(8.dp))
        }
        RawtextFilledButton(
            text = "+ 添加物品条件",
            color = Color(0xFF43A047),
            modifier = Modifier.fillMaxWidth(),
            onClick = { hasitems.add(HasitemCondition()) }
        )
    }

    SelectorGroup("计分板 (scores)") {
        scores.forEachIndexed { index, condition ->
            ScoreConditionItem(
                index = index,
                condition = condition,
                onChange = { scores[index] = it },
                onRemove = { scores.removeAt(index) },
            )
            Spacer(Modifier.height(8.dp))
        }
        RawtextFilledButton(
            text = "+ 添加计分条件",
            color = Color(0xFF43A047),
            modifier = Modifier.fillMaxWidth(),
            onClick = { scores.add(ScoreCondition()) }
        )
    }
}

@Composable
private fun SelectorGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CHelperTheme.colors.background)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CHelperTheme.colors.textMain
            )
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SelectorField(
    fields: MutableMap<String, String>,
    key: String,
    label: String,
    hint: String,
    hidden: Boolean = false,
) {
    if (hidden) return
    RawtextLabeledField(
        label = label,
        value = fields[key] ?: "",
        onValueChange = { fields[key] = it },
        hint = hint
    )
}

@Composable
private fun HasitemConditionItem(
    index: Int,
    condition: HasitemCondition,
    onChange: (HasitemCondition) -> Unit,
    onRemove: () -> Unit,
    onOpenItemPicker: () -> Unit,
    onOpenSlotPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "条件 #${index + 1}",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.textSecondary)
            )
            RawtextSmallIconButton(text = "删除", color = Color(0xFFD84315), onClick = onRemove)
        }
        Spacer(Modifier.height(8.dp))
        RawtextLabeledField(
            label = "物品 ID (item)",
            value = condition.item,
            onValueChange = { onChange(condition.copy(item = it)) },
            hint = "minecraft:apple",
            trailing = {
                RawtextSmallIconButton(text = "查", color = CHelperTheme.colors.mainColor, onClick = onOpenItemPicker)
            }
        )
        RawtextLabeledField(
            label = "数据值 (data)",
            value = condition.data,
            onValueChange = { onChange(condition.copy(data = it)) },
            hint = "0-32767"
        )
        RawtextLabeledField(
            label = "数量 (quantity)",
            value = condition.quantity,
            onValueChange = { onChange(condition.copy(quantity = it)) },
            hint = "1.., 1-10, !0"
        )
        RawtextLabeledField(
            label = "物品栏 (location)",
            value = condition.location,
            onValueChange = { onChange(condition.copy(location = it)) },
            hint = "slot.hotbar",
            trailing = {
                RawtextSmallIconButton(text = "查", color = CHelperTheme.colors.mainColor, onClick = onOpenSlotPicker)
            }
        )
        RawtextLabeledField(
            label = "槽位 (slot)",
            value = condition.slot,
            onValueChange = { onChange(condition.copy(slot = it)) },
            hint = "0..8"
        )
    }
}

@Composable
private fun ScoreConditionItem(
    index: Int,
    condition: ScoreCondition,
    onChange: (ScoreCondition) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "条件 #${index + 1}",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.textSecondary)
            )
            RawtextSmallIconButton(text = "删除", color = Color(0xFFD84315), onClick = onRemove)
        }
        Spacer(Modifier.height(8.dp))
        RawtextLabeledField(
            label = "计分项 (objective)",
            value = condition.objective,
            onValueChange = { onChange(condition.copy(objective = it)) },
            hint = "money, kills..."
        )
        RawtextLabeledField(
            label = "分数 (value)",
            value = condition.value,
            onValueChange = { onChange(condition.copy(value = it)) },
            hint = "10, 5.., ..15, 10..12, !10"
        )
    }
}
