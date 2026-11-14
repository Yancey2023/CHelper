package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TextClipboard
internal constructor(private val clipboard: Clipboard, private val read: (ClipEntry) -> String?) {
    suspend fun copyText(text: String) {
        clipboard.setClipEntry(textClipEntry(text))
    }

    suspend fun clipboardText(): String? = clipboard.getClipEntry()?.let(read)

    suspend fun withFeedback(
        failureMessage: String = "剪贴板访问失败，请稍后重试",
        action: suspend TextClipboard.() -> Unit,
    ) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ClientMessages.show(failureMessage)
        }
    }
}

@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val read = rememberClipTextReader()
    return remember(clipboard, read) { TextClipboard(clipboard, read) }
}

@Composable
fun rememberCopyText(message: String = "已复制"): (String) -> Unit {
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope, message) {
        { text ->
            scope.launch {
                clipboard.withFeedback("复制失败") {
                    copyText(text)
                    ClientMessages.show(message)
                }
            }
        }
    }
}

// Compose owns clipboard access; only the native payload's plain-text conversion differs.
internal expect fun textClipEntry(text: String): ClipEntry

@Composable internal expect fun rememberClipTextReader(): (ClipEntry) -> String?
