package yancey.chelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.CustomDialog
import yancey.chelper.ui.common.dialog.CustomDialogProperties
import yancey.chelper.ui.common.dialog.DialogContainer
import yancey.chelper.ui.common.widget.Switch
import yancey.chelper.ui.common.widget.Text
import yancey.chelper.ui.library.mcd.LineType

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 低代码 V2 状态标记辅助工具
// 独立模块：扫描 MCD 脚本中缺少 > 前缀状态行的命令，
// 提供可视化的方块类型 / 条件 / 红石 / Tick 延迟配置面板，
// 一键生成合规的 MCD v2 前缀语法并注入回原文。
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun LowCodeV2HelperDialog(
    rawContent: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    // 提取出所有需要标记的行
    val lines = rawContent.split(Regex("\\r?\\n"))
    val results = yancey.chelper.ui.library.mcd.validateMCDContent(rawContent).lines.associateBy { it.lineNumber }

    // 状态记录: map of lineNumber -> stateString (例如 ">C", ">I!")
    val lineStates = remember { mutableStateOf(mutableMapOf<Int, String>()) }

    var lastEffectiveType: LineType? = null
    val targetLines = mutableListOf<Pair<Int, String>>() // <LineNumber, RawText>

    for ((idx, line) in lines.withIndex()) {
        val lineNum = idx + 1
        val result = results[lineNum]
        if (result == null) continue

        if (result.type == LineType.COMMAND) {
            if (lastEffectiveType != LineType.STATE_LINE) {
                targetLines.add(lineNum to line)
                if (!lineStates.value.containsKey(lineNum)) {
                    lineStates.value[lineNum] = ">C" // 默认连锁
                }
            }
            lastEffectiveType = LineType.COMMAND
        } else {
            if (result.type != LineType.COMMENT && result.type != LineType.META) {
                lastEffectiveType = result.type
            }
        }
    }

    CustomDialog(
        onDismissRequest = onDismiss,
        properties = CustomDialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        DialogContainer(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .heightIn(max = 600.dp),
            backgroundNoTranslate = true
        ) {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)) {
                Text(
                    text = "V2 标记助手",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CHelperTheme.colors.textMain
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "以下指令缺少方块状态，请为它们分配：",
                    style = TextStyle(fontSize = 13.sp, color = CHelperTheme.colors.textSecondary)
                )
                Spacer(Modifier.height(12.dp))

                if (targetLines.isEmpty()) {
                    Box(modifier = Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("当前脚本中所有指令都已有正确的前置方块推断状态，无需再标记。", style = TextStyle(color = CHelperTheme.colors.textHint, textAlign = TextAlign.Center))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for ((lineNum, text) in targetLines) {
                            val currentState = lineStates.value[lineNum] ?: ">C"
                            LowCodeLineItem(
                                lineNumber = lineNum,
                                code = text,
                                currentState = currentState,
                                onStateChange = { newState ->
                                    val newMap = lineStates.value.toMutableMap()
                                    newMap[lineNum] = newState
                                    lineStates.value = newMap
                                }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                DialogActionBar(
                    targetLines = targetLines,
                    lines = lines,
                    results = results,
                    lineStates = lineStates,
                    onDismiss = onDismiss,
                    onApply = onApply
                )
            }
        }
    }
}

/**
 * 底部操作栏：取消 / 一键应用
 * 拆分出来降低 LowCodeV2HelperDialog 的嵌套层级
 */
@Composable
private fun DialogActionBar(
    targetLines: List<Pair<Int, String>>,
    lines: List<String>,
    results: Map<Int, yancey.chelper.ui.library.mcd.MCDLineResult?>,
    lineStates: androidx.compose.runtime.MutableState<MutableMap<Int, String>>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "取消",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(color = CHelperTheme.colors.textSecondary)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (targetLines.isEmpty()) Color(0xFFBDBDBD) else Color(0xFFE65100))
                .then(
                    if (targetLines.isNotEmpty()) Modifier.clickable {
                        val output = mutableListOf<String>()
                        var lastType: LineType? = null
                        for ((i, l) in lines.withIndex()) {
                            val ln = i + 1
                            val res = results[ln]
                            if (res?.type == LineType.COMMAND) {
                                if (lastType != LineType.STATE_LINE) {
                                    output.add(lineStates.value[ln] ?: ">C")
                                }
                                lastType = LineType.COMMAND
                            } else if (res != null) {
                                if (res.type != LineType.COMMENT && res.type != LineType.META) {
                                    lastType = res.type
                                }
                            }
                            output.add(l)
                        }
                        onApply(output.joinToString("\n"))
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "一键应用",
                style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 * 单行命令的低代码配置卡片
 * 显示行号 + 命令文本，提供方块类型选择芯片和条件/红石/延迟开关
 */
@Composable
private fun LowCodeLineItem(
    lineNumber: Int,
    code: String,
    currentState: String,
    onStateChange: (String) -> Unit
) {
    // 解析现有状态（忽略所有 _ 占位符）
    val cleanState = currentState.replace("_", "").trim()
    val isC = Regex("^>\\s*c", RegexOption.IGNORE_CASE).containsMatchIn(cleanState)
    val isI = Regex("^>\\s*i", RegexOption.IGNORE_CASE).containsMatchIn(cleanState)
    val isR = Regex("^>\\s*r", RegexOption.IGNORE_CASE).containsMatchIn(cleanState)
    val isH = Regex("^>\\s*h\\s*$", RegexOption.IGNORE_CASE).matches(cleanState)
    val isCond = cleanState.contains("?")
    val isRed = cleanState.contains("!")
    val delayMatch = Regex("t(\\d+)").find(cleanState)
    val delay = delayMatch?.groupValues?.get(1) ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(10.dp)
    ) {
        // 行号 + 命令预览
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "L$lineNumber",
                style = TextStyle(fontSize = 11.sp, color = Color(0xFFE65100), fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(32.dp)
            )
            Text(
                code,
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textMain, fontFamily = FontFamily.Monospace),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))

        // 方块类型选择芯片
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LowCodeChip("连锁(C)", isC) { onStateChange(buildState(">C", isCond, isRed, delay)) }
            LowCodeChip("脉冲(I)", isI) { onStateChange(buildState(">I", isCond, isRed, delay)) }
            LowCodeChip("循环(R)", isR) { onStateChange(buildState(">R", isCond, isRed, delay)) }
            LowCodeChip("手动(H)", isH) { onStateChange(">H") }
        }
        Spacer(Modifier.height(10.dp))

        // 条件 / 红石 / Tick 延迟控制行
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isH) 0.5f else 1f)) {
                Switch(
                    checked = isCond,
                    onCheckedChange = { if (!isH) onStateChange(buildState(if (isC) ">C" else if (isI) ">I" else ">R", it, isRed, delay)) }
                )
                Spacer(Modifier.width(6.dp))
                Text("条件?", style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary))
            }
            Spacer(Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isH) 0.5f else 1f)) {
                Switch(
                    checked = isRed,
                    onCheckedChange = { if (!isH) onStateChange(buildState(if (isC) ">C" else if (isI) ">I" else ">R", isCond, it, delay)) }
                )
                Spacer(Modifier.width(6.dp))
                Text("红石!", style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isH) 0.5f else 1f)) {
                Text("Tick:", style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary))
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = delay,
                    onValueChange = {
                        if (!isH) {
                            val newDelay = it.filter { ch -> ch.isDigit() }
                            if (newDelay.length <= 5) onStateChange(buildState(if (isC) ">C" else if (isI) ">I" else ">R", isCond, isRed, newDelay))
                        }
                    },
                    modifier = Modifier
                        .width(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CHelperTheme.colors.background)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textMain, textAlign = TextAlign.Center),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (delay.isEmpty()) {
                                Text("0", style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textHint, textAlign = TextAlign.Center))
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 拼装 MCD v2 状态前缀字符串
 * 例如 buildState(">C", true, false, "10") => ">C?t10"
 */
internal fun buildState(base: String, cond: Boolean, red: Boolean, delay: String): String {
    var s = base
    if (cond) s += "?"
    if (red) s += "!"
    if (delay.isNotEmpty()) s += "t$delay"
    return s
}

/**
 * 方块类型选择芯片，选中时高亮
 */
@Composable
private fun LowCodeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFFE65100) else CHelperTheme.colors.backgroundComponentNoTranslate)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                color = if (selected) Color.White else CHelperTheme.colors.textMain,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}
