package yancey.chelper.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import yancey.chelper.ui.designsystem.dialog.AppAlertDialog
import yancey.chelper.ui.designsystem.layout.RootView
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton
import yancey.chelper.ui.platform.LocalAppBackAction

/** Finish the previous editor's writes before a new editor can replace its session registration. */
@Composable
fun PersistedEditor(session: AppSession, owner: Any, content: @Composable () -> Unit) {
    val restore = remember(session, owner) { session.prepareRestore(owner) }
    var ready by remember(session, owner) { mutableStateOf(restore == null) }
    var failure by remember(session, owner) { mutableStateOf<String?>(null) }
    var retry by remember(session, owner) { mutableIntStateOf(0) }
    val back = LocalAppBackAction.current
    LaunchedEffect(session, owner, retry) {
        if (ready) return@LaunchedEffect
        failure = null
        try {
            restore?.invoke()
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message ?: "保存当前内容失败"
        }
    }
    // Navigation measures the entering page before its effects finish. A zero-sized waiting
    // branch gives slideInHorizontally a zero distance and makes the editor appear without motion.
    Box(Modifier.fillMaxSize()) {
        if (ready) content()
        else
            RootView {
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
}
