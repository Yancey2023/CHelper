/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * 导出向导 UI：帮助玩家从 MC 命令方块链中逐个提取命令并拼装为 MCD v2。
 * 三步流程：链配置 → 逐条录入 → 预览 & 导出。
 */

package yancey.chelper.ui.loongflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.library.mcd.BlockType

@Composable
fun ExportWizard(
    viewModel: LoongFlowViewModel,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // 步骤指示条
        StepIndicator(
            steps = listOf("链配置", "逐条录入", "导出"),
            currentStep = viewModel.exportStep
        )

        // 内容区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            when (viewModel.exportStep) {
                0 -> ExportStepConfig(viewModel)
                1 -> ExportStepRecord(viewModel)
                2 -> ExportStepPreview(viewModel)
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
            when (viewModel.exportStep) {
                0 -> {
                    ExportActionButton(
                        text = "收起",
                        color = CHelperTheme.colors.textSecondary,
                        onClick = onMinimize
                    )
                    ExportActionButton(
                        text = "开始录入 ▸",
                        color = CHelperTheme.colors.mainColor,
                        onClick = { viewModel.startRecording() }
                    )
                }
                1 -> {
                    ExportActionButton(
                        text = "◂ 返回配置",
                        color = CHelperTheme.colors.textSecondary,
                        onClick = { viewModel.exportStep = 0 }
                    )
                    ExportActionButton(
                        text = "完成录入 (${viewModel.collectedBlocks.size}) →",
                        color = CHelperTheme.colors.mainColor,
                        onClick = { viewModel.finishRecording() }
                    )
                }
                2 -> {
                    ExportActionButton(
                        text = "◂ 继续录入",
                        color = CHelperTheme.colors.textSecondary,
                        onClick = { viewModel.exportStep = 1 }
                    )
                    ExportActionButton(
                        text = "关闭",
                        color = CHelperTheme.colors.mainColor,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * Step 0: 链配置——设置链名称、作者名
 */
@Composable
private fun ExportStepConfig(viewModel: LoongFlowViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "配置命令链",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = CHelperTheme.colors.textMain
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "从 Minecraft 中逐个复制命令方块的指令，组装为 MCDv2 格式",
            style = TextStyle(
                fontSize = 12.sp,
                color = CHelperTheme.colors.textSecondary
            )
        )
        Spacer(Modifier.height(20.dp))

        // 链名称
        ConfigTextField(
            label = "链名称",
            value = viewModel.chainName,
            onValueChange = { viewModel.chainName = it },
            placeholder = "未命名命令链"
        )
        Spacer(Modifier.height(14.dp))

        // 作者名
        ConfigTextField(
            label = "作者",
            value = viewModel.authorName,
            onValueChange = { viewModel.authorName = it },
            placeholder = "可选"
        )
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                color = CHelperTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .border(
                    1.dp,
                    CHelperTheme.colors.textSecondary.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = CHelperTheme.colors.textMain
                ),
                singleLine = true,
                cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = CHelperTheme.colors.textSecondary.copy(alpha = 0.4f)
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

/**
 * Step 1: 逐条录入——核心交互界面
 */
@Composable
private fun ExportStepRecord(viewModel: LoongFlowViewModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        // 当前方块标题
        val blockLabel = if (viewModel.editingBlockIndex >= 0) {
            "编辑方块 #${viewModel.editingBlockIndex + 1}"
        } else {
            "方块 #${viewModel.collectedBlocks.size + 1}"
        }
        Text(
            text = blockLabel,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = CHelperTheme.colors.textMain
            )
        )
        Spacer(Modifier.height(8.dp))

        // 命令输入区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .border(
                    1.dp,
                    CHelperTheme.colors.textSecondary.copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            val focusRequester = remember { FocusRequester() }
            BasicTextField(
                value = viewModel.currentBlockInput,
                onValueChange = { viewModel.currentBlockInput = it },
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CHelperTheme.colors.textMain
                ),
                cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (viewModel.currentBlockInput.isEmpty()) {
                            Text(
                                text = "在 MC 中复制命令后粘贴在此处",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = CHelperTheme.colors.textSecondary.copy(alpha = 0.4f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // 粘贴按钮——尝试通过 Compose clipboard 读取，如果失败提示手动长按粘贴
        val composeClipboard = LocalClipboardManager.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f))
                    .clickable {
                        // Compose ClipboardManager 在窗口有焦点时可以工作
                        val text = try {
                            composeClipboard.getText()?.text?.trim()
                        } catch (_: Exception) { null }

                        if (!text.isNullOrEmpty()) {
                            viewModel.currentBlockInput = text
                            viewModel.toastMessage = "已粘贴"
                        } else {
                            viewModel.toastMessage = "无法读取剪贴板，请手动粘贴"
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "尝试从剪贴板粘贴",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.mainColor
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 方块属性配置
        BlockTypeSelector(
            selectedType = viewModel.currentBlockType,
            onTypeChange = { viewModel.currentBlockType = it },
            conditional = viewModel.currentConditional,
            onConditionalChange = { viewModel.currentConditional = it },
            needsRedstone = viewModel.currentNeedsRedstone,
            onNeedsRedstoneChange = { viewModel.currentNeedsRedstone = it },
            tickDelay = viewModel.currentTickDelay,
            onTickDelayChange = { viewModel.currentTickDelay = it }
        )

        Spacer(Modifier.height(12.dp))

        // 保存当前 / 删除 按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (viewModel.editingBlockIndex >= 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.deleteBlock(viewModel.editingBlockIndex) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "删除",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C)
                        )
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f))
                    .clickable { viewModel.saveCurrentBlock() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (viewModel.editingBlockIndex >= 0) "保存修改" else "保存并下一条 ▸",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.mainColor
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 已录入方块列表预览
        if (viewModel.collectedBlocks.isNotEmpty()) {
            Text(
                text = "已录入 ${viewModel.collectedBlocks.size} 条",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = CHelperTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(viewModel.collectedBlocks) { index, block ->
                    val typeColor = if (CHelperTheme.theme == CHelperTheme.Theme.Dark) {
                        block.type.darkColor
                    } else {
                        block.type.lightColor
                    }
                    val isEditing = viewModel.editingBlockIndex == index

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isEditing) typeColor.copy(alpha = 0.2f)
                                else CHelperTheme.colors.backgroundComponent
                            )
                            .border(
                                1.dp,
                                if (isEditing) typeColor else typeColor.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.editBlock(index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .width(120.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(typeColor)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "#${index + 1} ${block.type.label}",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = typeColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Text(
                                text = block.command,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CHelperTheme.colors.textSecondary
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step 2: MCD v2 预览 & 导出操作
 */
@Composable
private fun ExportStepPreview(viewModel: LoongFlowViewModel) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "MCD v2 预览",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = CHelperTheme.colors.textMain
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${viewModel.collectedBlocks.size} 条命令方块",
            style = TextStyle(
                fontSize = 12.sp,
                color = CHelperTheme.colors.textSecondary
            )
        )
        Spacer(Modifier.height(8.dp))

        // MCD 文本预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = viewModel.exportMcdText,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CHelperTheme.colors.textMain
                )
            )
        }

        Spacer(Modifier.height(10.dp))

        // 导出操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ExportOpButton(
                icon = "",
                label = "复制全文",
                onClick = { viewModel.copyExportText(context) }
            )
        }
    }
}

@Composable
private fun ExportOpButton(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CHelperTheme.colors.mainColor.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            style = TextStyle(fontSize = 20.sp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                color = CHelperTheme.colors.mainColor,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun ExportActionButton(
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
