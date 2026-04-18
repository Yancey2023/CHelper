/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 导入向导 UI：从命令库 MCD 数据逐条复制命令到剪贴板。
 * 分两步：选择命令 → 逐条复制。
 */

package yancey.chelper.ui.loongflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text

@Composable
fun ImportWizard(
    viewModel: LoongFlowViewModel,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 步骤指示条
        StepIndicator(
            steps = listOf("选择命令", "逐条复制"),
            currentStep = viewModel.importStep
        )

        // 内容区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            when (viewModel.importStep) {
                0 -> ImportStepSelect(viewModel)
                1 -> ImportStepCopy(viewModel)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 底部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (viewModel.importStep) {
                0 -> {
                    // 收起为气泡
                    ActionButton(
                        text = "收起",
                        color = CHelperTheme.colors.textSecondary,
                        onClick = onMinimize
                    )
                    ActionButton(
                        text = "开始导入 (${viewModel.selectedIndices.size}) ▸",
                        color = CHelperTheme.colors.mainColor,
                        onClick = { viewModel.startImportCopy() }
                    )
                }
                1 -> {
                    ActionButton(
                        text = "◂ 上一条",
                        color = CHelperTheme.colors.textSecondary,
                        onClick = { viewModel.prevCommand(context) },
                        enabled = viewModel.currentCopyIndex > 0
                    )

                    if (viewModel.isImportComplete) {
                        ActionButton(
                            text = "✓ 完成",
                            color = CHelperTheme.colors.mainColor,
                            onClick = onDismiss
                        )
                    } else {
                        ActionButton(
                            text = "下一条 ▸",
                            color = CHelperTheme.colors.mainColor,
                            onClick = { viewModel.nextCommand(context) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 0: 命令列表勾选界面
 */
@Composable
private fun ImportStepSelect(viewModel: LoongFlowViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 全选/反选操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选 ${viewModel.selectedIndices.size}/${viewModel.allCommands.size} 条",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
            Row {
                Text(
                    text = "全选",
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.selectAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.mainColor,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "反选",
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.deselectAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = CHelperTheme.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // 命令列表
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            itemsIndexed(viewModel.allCommands) { index, ctx ->
                val isSelected = index in viewModel.selectedIndices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleSelection(index) }
                        .padding(10.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 勾选指示
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) CHelperTheme.colors.mainColor
                                else Color.Transparent
                            )
                            .border(
                                1.5.dp,
                                if (isSelected) CHelperTheme.colors.mainColor
                                else CHelperTheme.colors.textSecondary.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                text = "✓",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // 序号
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.width(24.dp),
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = CHelperTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    // 命令文本
                    Text(
                        text = ctx.command,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) CHelperTheme.colors.textMain
                            else CHelperTheme.colors.textSecondary
                        ),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Step 1: 逐条复制界面
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportStepCopy(viewModel: LoongFlowViewModel) {
    val context = LocalContext.current
    val cmds = viewModel.selectedCommands
    val index = viewModel.currentCopyIndex

    // 首次进入自动复制第一条
    LaunchedEffect(Unit) {
        viewModel.copyCurrentCommand(context)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (viewModel.isImportComplete) {
            // 完成状态
            Text(
                text = "✓",
                style = TextStyle(
                    fontSize = 48.sp,
                    color = CHelperTheme.colors.mainColor,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "全部导入完成",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = CHelperTheme.colors.mainColor,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "共 ${cmds.size} 条命令",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
        } else if (index < cmds.size) {
            // 进度
            Text(
                text = "${index + 1} / ${cmds.size}",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = CHelperTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(6.dp))

            // 进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CHelperTheme.colors.textSecondary.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (index + 1).toFloat() / cmds.size)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CHelperTheme.colors.mainColor)
                )
            }
            Spacer(Modifier.height(16.dp))

            val ctx = cmds[index]
            
            // 元信息 Chips
            FlowRow(
                modifier = Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 所属链
                ctx.chainName?.takeIf { it.isNotEmpty() }?.let { chainName ->
                    Text(
                        text = "链: $chainName",
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = CHelperTheme.colors.mainColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // 方块类型
                val typeName = ctx.blockTypeName
                val baseColor = ctx.blockData?.type?.let { if (CHelperTheme.theme == CHelperTheme.Theme.Dark) it.darkColor else it.lightColor } ?: CHelperTheme.colors.textSecondary
                Text(
                    text = typeName,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(baseColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = baseColor,
                        fontWeight = FontWeight.Medium
                    )
                )

                // 完整解析的 MCDv2 标签卡片化呈现
                ctx.blockData?.let { bd ->
                    if (bd.type != yancey.chelper.ui.library.mcd.BlockType.CHAT) {
                        // 1. 条件制约
                        if (bd.conditional) {
                            Text(
                                text = "条件制约",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE65100).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = Color(0xFFE65100), // 橙色警告色
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // 2. 动力来源
                        if (bd.needsRedstone) {
                            Text(
                                text = "红石控制",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFD32F2F).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = Color(0xFFD32F2F), // 红色警示色
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        } else if (bd.alwaysActive) {
                            Text(
                                text = "始终活动",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF388E3C).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = Color(0xFF388E3C), // 绿色畅通色
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // 3. 延迟 Tick
                        if (bd.tickDelay > 0) {
                            Text(
                                text = "延迟 ${bd.tickDelay} 刻",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1976D2).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = Color(0xFF1976D2), // 蓝色信息色
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // 当前命令预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CHelperTheme.colors.backgroundComponent)
                    .padding(14.dp)
            ) {
                Text(
                    text = ctx.command,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CHelperTheme.colors.textMain
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已自动复制到剪贴板",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = CHelperTheme.colors.textSecondary
                )
            )
        }
    }
}

/**
 * 底部操作按钮
 */
@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val effectiveColor = if (enabled) color else color.copy(alpha = 0.3f)
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = effectiveColor
        )
    )
}
