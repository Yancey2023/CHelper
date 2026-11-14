package yancey.chelper.ui.app

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import yancey.chelper.ui.designsystem.dialog.AppAlertDialog
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.platform.LocalAppBackAction

/** Finish the previous editor's writes before a new editor can replace its session registration. */
@Composable
fun PersistedEditor(session: AppSession, owner: Any, content: @Composable () -> Unit) {
    var ready by remember(session, owner) { mutableStateOf(false) }
    var failure by remember(session, owner) { mutableStateOf<String?>(null) }
    var retry by remember(session, owner) { mutableIntStateOf(0) }
    val back = LocalAppBackAction.current
    LaunchedEffect(session, owner, retry) {
        failure = null
        try {
            session.flush(owner)
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message ?: "保存当前内容失败"
        }
    }
    if (ready) content()
    else {
        Text("正在恢复编辑内容…")
        failure?.let { message ->
            AppAlertDialog(
                onDismissRequest = back,
                title = { Text("暂时无法恢复编辑") },
                text = { Text("上次编辑内容尚未保存。\n$message") },
                confirmButton = { TextButton(onClick = { retry++ }) { Text("重试") } },
                dismissButton = { TextButton(onClick = back) { Text("返回") } },
            )
        }
    }
}
