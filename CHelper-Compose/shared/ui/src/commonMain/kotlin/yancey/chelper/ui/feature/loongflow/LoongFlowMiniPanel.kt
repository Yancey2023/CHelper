package yancey.chelper.ui.feature.loongflow

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import yancey.chelper.domain.library.BlockType
import yancey.chelper.resources.Res
import yancey.chelper.resources.check
import yancey.chelper.resources.inbox
import yancey.chelper.resources.link
import yancey.chelper.resources.message_square
import yancey.chelper.resources.repeat
import yancey.chelper.resources.terminal
import yancey.chelper.resources.zap
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.feature.library.mcd.darkColor
import yancey.chelper.ui.feature.library.mcd.lightColor

data class ImportMiniState(
    val icon: DrawableResource,
    val title: String,
    val subtitle: String,
    val progress: String,
    val nextText: String,
    val canPrev: Boolean,
    val color: Color,
)

/** The original mini-window labels and command-block attributes, shared by both hosts. */
fun importMiniState(model: LoongFlowViewModel, dark: Boolean): ImportMiniState {
    val total = model.selectedCommands.size
    if (model.isImportComplete)
        return ImportMiniState(
            Res.drawable.check,
            "导入完成",
            "所有命令都已经复制过一遍",
            "$total/$total",
            "关闭",
            total > 0,
            Color(0xFF2E7D32),
        )
    val context =
        model.currentImportCommand
            ?: return ImportMiniState(
                Res.drawable.inbox,
                "没有可导入命令",
                "回到详情重新选择",
                "0/0",
                "下一条",
                false,
                Color(0xFF607D8B),
            )
    val block = context.blockData
    val progress = "${model.currentCopyIndex + 1}/$total"
    val next = if (model.currentCopyIndex >= total - 1) "完成" else "下一条"
    if (block == null) {
        val compact = context.command.replace(Regex("\\s+"), " ").trim()
        return ImportMiniState(
            Res.drawable.terminal,
            context.chainName?.takeIf(String::isNotBlank) ?: "纯命令",
            if (compact.length > 42) compact.take(42) + "..." else compact,
            progress,
            next,
            model.currentCopyIndex > 0,
            Color(0xFF546E7A),
        )
    }
    val label = context.blockTypeName
    val parts = mutableListOf(label)
    if (block.type != BlockType.CHAT) {
        if (block.conditional) parts += "条件"
        parts += if (block.needsRedstone) "红石控制" else "始终开启"
        if (block.tickDelay > 0) parts += "延迟 ${block.tickDelay}刻"
    }
    val icon =
        when (block.type) {
            BlockType.IMPULSE -> Res.drawable.zap
            BlockType.CHAIN -> Res.drawable.link
            BlockType.REPEAT -> Res.drawable.repeat
            BlockType.CHAT -> Res.drawable.message_square
        }
    return ImportMiniState(
        icon,
        context.chainName?.takeIf(String::isNotBlank) ?: label,
        parts.joinToString(" · "),
        progress,
        next,
        model.currentCopyIndex > 0,
        if (dark) block.type.darkColor else block.type.lightColor,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoongFlowMiniPanel(
    model: LoongFlowViewModel,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onMove: ((Float, Float) -> Unit)? = null,
) {
    val state = importMiniState(model, CHelperTheme.theme == CHelperTheme.Theme.Dark)
    LaunchedEffect(model.toastMessage) {
        model.toastMessage?.let {
            onMessage(it)
            model.toastMessage = null
        }
    }
    val dark = CHelperTheme.theme == CHelperTheme.Theme.Dark
    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color(0xF21E2024) else Color(0xF8FFFFFF))
            .border(1.2.dp, state.color, RoundedCornerShape(18.dp))
            .then(
                if (onMove == null) Modifier
                else
                    Modifier.pointerInput(onMove) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            onMove(delta.x, delta.y)
                        }
                    }
            )
            .combinedClickable(onClick = onExpand, onLongClick = onDismiss)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(state.color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(state.icon, Modifier.size(19.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    state.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                )
                Text(
                    state.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 11.sp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(28.dp).padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f)
                    .height(24.dp)
                    .background(state.color.copy(alpha = 0.11f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.progress,
                    style =
                        TextStyle(
                            color = state.color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
            MiniAction("上一条", state.color, state.canPrev) {
                if (!state.canPrev) model.toastMessage = "已经是第一条" else model.prevCommand()
            }
            MiniAction(state.nextText, state.color, true) {
                if (model.isImportComplete) onDismiss()
                else if (!model.nextCommand()) model.toastMessage = "全部导入完成"
            }
            MiniAction("详情", state.color, true, onExpand)
        }
    }
}

@Composable
private fun MiniAction(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        modifier = Modifier.padding(start = 5.dp),
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            text,
            style =
                TextStyle(
                    color = if (enabled) color else color.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}
