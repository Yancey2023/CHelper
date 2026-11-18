package yancey.chelper.ui.feature.library.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import org.jetbrains.compose.resources.*
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.dialog.DialogContainer
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.DividerVertical
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.library.mcd.color

@Composable
fun LineCopyContent(commands: List<String>, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }

    // 自动复制当前命令
    LaunchedEffect(currentIndex) {
        if (currentIndex < commands.size) {
            onCopy(commands[currentIndex])
        }
    }

    AppDialog(onDismissRequest = onDismiss) {
        DialogContainer(backgroundNoTranslate = true) {
            Column(modifier = Modifier) {
                // 标题
                Text(
                    modifier = Modifier.fillMaxWidth().padding(0.dp, 10.dp),
                    text = "逐行复制 (${currentIndex + 1}/${commands.size})",
                    style =
                        TextStyle(
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                // 当前命令预览
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(16.dp, 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CHelperTheme.colors.backgroundComponent)
                            .padding(12.dp)
                ) {
                    Text(
                        text = if (currentIndex < commands.size) commands[currentIndex] else "",
                        style = TextStyle(color = CHelperTheme.colors.textMain, fontSize = 14.sp),
                    )
                }
                Text(
                    modifier = Modifier.fillMaxWidth().padding(0.dp, 4.dp, 0.dp, 8.dp),
                    text = "已自动复制到剪贴板",
                    style =
                        TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = CHelperTheme.colors.textSecondary,
                        ),
                )
                Divider(0.dp)
                // 底部按钮行
                Row(Modifier.height(45.dp)) {
                    Box(Modifier.fillMaxHeight().weight(1f).clickable { onDismiss() }) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = "关闭",
                            style =
                                TextStyle(
                                    fontSize = 20.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                    DividerVertical(0.dp)
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clickable {
                            if (currentIndex < commands.size - 1) {
                                currentIndex++
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = if (currentIndex < commands.size - 1) "下一条 ▸" else "全部完成 ✓",
                            style =
                                TextStyle(
                                    fontSize = 20.sp,
                                    color = CHelperTheme.colors.mainColor,
                                    textAlign = TextAlign.Center,
                                ),
                        )
                    }
                }
            }
        }
    }
}
