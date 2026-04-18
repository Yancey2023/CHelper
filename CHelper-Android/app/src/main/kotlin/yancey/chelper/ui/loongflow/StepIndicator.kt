/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 水平步骤指示条组件，用于在向导流程中可视化当前进度。
 */

package yancey.chelper.ui.loongflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text

/**
 * 水平步骤指示器。
 * 活跃步骤显示实心圆点 + 主色标签，未完成步骤显示空心外观 + 浅色标签。
 * 步骤间以短横线连接。
 *
 * 例: ● 选择命令 ─── ○ 逐条复制
 *
 * @param steps 步骤标签列表
 * @param currentStep 当前活跃步骤的索引 (0-indexed)
 */
@Composable
fun StepIndicator(
    steps: List<String>,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val activeColor = CHelperTheme.colors.mainColor
    val inactiveColor = CHelperTheme.colors.textSecondary.copy(alpha = 0.35f)
    val completedColor = CHelperTheme.colors.mainColor.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        steps.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep
            val dotColor = when {
                isActive -> activeColor
                isCompleted -> completedColor
                else -> inactiveColor
            }
            val textColor = when {
                isActive -> activeColor
                isCompleted -> completedColor
                else -> inactiveColor
            }

            // 圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // 标签
            Text(
                text = label,
                modifier = Modifier.padding(start = 4.dp),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            )

            // 步骤间连接线（最后一步不加）
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(1.dp)
                        .weight(1f)
                        .background(
                            if (index < currentStep) completedColor else inactiveColor
                        )
                )
            }
        }
    }
}
