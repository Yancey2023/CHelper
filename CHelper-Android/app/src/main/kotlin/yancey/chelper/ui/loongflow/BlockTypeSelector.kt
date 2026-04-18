/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 命令方块属性配置组件，用于导出模式中逐条录入时配置方块的类型、条件模式、红石控制和延迟刻。
 */

package yancey.chelper.ui.loongflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.library.mcd.BlockType

/**
 * 方块属性配置面板。
 * 四组控件纵向排列：方块类型、条件模式、红石控制、延迟刻。
 * 颜色与 MCDRenderer 的方块色值保持一致，给用户直觉映射。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockTypeSelector(
    selectedType: BlockType,
    onTypeChange: (BlockType) -> Unit,
    conditional: Boolean,
    onConditionalChange: (Boolean) -> Unit,
    needsRedstone: Boolean,
    onNeedsRedstoneChange: (Boolean) -> Unit,
    tickDelay: Int,
    onTickDelayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 方块类型 ──
        PropertyRow(label = "方块类型") {
            BlockType.entries.forEach { type ->
                val isSelected = type == selectedType
                val color = if (CHelperTheme.theme == CHelperTheme.Theme.Dark) {
                    type.darkColor
                } else {
                    type.lightColor
                }
                SelectableChip(
                    text = type.label,
                    isSelected = isSelected,
                    color = color,
                    onClick = { onTypeChange(type) }
                )
                Spacer(Modifier.width(6.dp))
            }
        }

        // ── 条件模式 ──
        PropertyRow(label = "条件模式") {
            SelectableChip(
                text = "无条件",
                isSelected = !conditional,
                color = CHelperTheme.colors.mainColor,
                onClick = { onConditionalChange(false) }
            )
            Spacer(Modifier.width(6.dp))
            SelectableChip(
                text = "条件",
                isSelected = conditional,
                color = Color(0xFFE65100),
                onClick = { onConditionalChange(true) }
            )
        }

        // ── 红石控制 ──
        PropertyRow(label = "红石控制") {
            SelectableChip(
                text = "保持开启",
                isSelected = !needsRedstone,
                color = Color(0xFF2E7D32),
                onClick = { onNeedsRedstoneChange(false) }
            )
            Spacer(Modifier.width(6.dp))
            SelectableChip(
                text = "需要红石",
                isSelected = needsRedstone,
                color = Color(0xFFB71C1C),
                onClick = { onNeedsRedstoneChange(true) }
            )
        }

        // ── 延迟刻 ──
        PropertyRow(label = "延迟刻") {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CHelperTheme.colors.backgroundComponent)
                    .border(
                        1.dp,
                        CHelperTheme.colors.textSecondary.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = if (tickDelay == 0) "" else tickDelay.toString(),
                    onValueChange = { input ->
                        val parsed = input.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                        onTickDelayChange(parsed)
                    },
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = CHelperTheme.colors.textMain,
                        fontWeight = FontWeight.Medium
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (tickDelay == 0) {
                                Text(
                                    text = "0",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = CHelperTheme.colors.textSecondary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PropertyRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            style = TextStyle(
                fontSize = 12.sp,
                color = CHelperTheme.colors.textSecondary
            )
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

/**
 * 可选中的 Chip 按钮。
 * 选中时背景填充对应颜色 15% alpha + 实色文字，
 * 未选中时仅边框 + 暗淡文字。
 */
@Composable
private fun SelectableChip(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (isSelected) color.copy(alpha = 0.4f) else CHelperTheme.colors.textSecondary.copy(alpha = 0.2f)
    val textColor = if (isSelected) color else CHelperTheme.colors.textSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        )
    }
}
