/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * LoongFlow 米窗风格主面板。
 * 包含拖拽暗示条、标题栏、模式内容区、右下角 resize 手柄的顶层 Composable 容器。
 */

package yancey.chelper.ui.loongflow

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.widget.Text

/**
 * 游龙面板——米窗风格的顶层布局。
 *
 * @param mode 工作模式（导入/导出）
 * @param library 导入模式时携带的命令库数据，导出模式为 null
 * @param viewModel 状态管理
 * @param onMinimize 收起为气泡的回调
 * @param onDismiss 关闭面板的回调
 * @param onResize  拖拽调整大小的增量回调 (deltaWidthPx, deltaHeightPx)
 */
@Composable
fun LoongFlowPanel(
    mode: LoongFlowMode,
    library: LibraryFunction?,
    viewModel: LoongFlowViewModel,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onToggleSize: () -> Unit = {},
    onMove: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val context = LocalContext.current

    // Toast 消息反馈
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.toastMessage = null
        }
    }

    // 用 Box 包一层来放 resize 手柄，它需要叠在内容之上
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(CHelperTheme.colors.background.copy(alpha = 0.94f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══ 拖拽层 ═══
            // 将拖拽事件放在标题栏和暗示条这一层，或者让拖拽暗示条+标题栏部分接收拖拽事件
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        ) { change, dragAmount ->
                            // 不完全 consume() 有可能导致深层组件仍能获取某些事件，但在窗口拖动场景下
                            // 只要没与其他同向滑动冲突，就可以让窗口随之移动
                            change.consume()
                            onMove(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {
                // ═══ 拖拽暗示条 ═══
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                CHelperTheme.colors.textSecondary.copy(alpha = 0.3f)
                            )
                    )
                }

                // ═══ 标题栏 ═══
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // 专属矢量图腾 + 模式标题
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = yancey.chelper.R.drawable.ic_loong_flow_bubble),
                    contentDescription = "LoongFlow Logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (mode) {
                        LoongFlowMode.IMPORT -> "游龙导入"
                        LoongFlowMode.EXPORT -> "游龙导出"
                    },
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CHelperTheme.colors.textMain
                    )
                )

                // 导入模式显示库名
                if (mode == LoongFlowMode.IMPORT && viewModel.libraryName.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· ${viewModel.libraryName}",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = CHelperTheme.colors.textSecondary
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // 缩放尺寸按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleSize() }
                        .background(CHelperTheme.colors.textSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⛶",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.textSecondary
                        )
                    )
                }

                Spacer(Modifier.width(6.dp))

                // 最小化气泡按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMinimize() }
                        .background(CHelperTheme.colors.textSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "─",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.textSecondary
                        )
                    )
                }

                Spacer(Modifier.width(6.dp))

                // 关闭按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .background(CHelperTheme.colors.textSecondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.textSecondary
                        )
                    )
                }
                }
            } // 结束外层 Column 包裹 title row 的 drag layer

            // ═══ 内容区 ═══
            Box(modifier = Modifier.weight(1f)) {
                when (mode) {
                    LoongFlowMode.IMPORT -> {
                        ImportWizard(
                            viewModel = viewModel,
                            onMinimize = onMinimize,
                            onDismiss = onDismiss,
                        )
                    }
                    LoongFlowMode.EXPORT -> {
                        ExportWizard(
                            viewModel = viewModel,
                            onMinimize = onMinimize,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        }
    }
}
