package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TextClipboard
internal constructor(private val clipboard: Clipboard, private val codec: TextClipCodec) {
    suspend fun copyText(text: String) {
        clipboard.setClipEntry(codec.create(text))
    }

    suspend fun clipboardText(): String? = clipboard.getClipEntry()?.let(codec::read)

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
fun rememberTextClipboard(
    codec: TextClipCodec = LocalClientServices.current.clipboardCodec
): TextClipboard {
    val clipboard = LocalClipboard.current
    return remember(clipboard, codec) { TextClipboard(clipboard, codec) }
}

@Composable
fun rememberCopyText(
    message: String = "已复制",
    codec: TextClipCodec = LocalClientServices.current.clipboardCodec,
): (String) -> Unit {
    val clipboard = rememberTextClipboard(codec)
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

/** Only native payload conversion is platform supplied; Compose still owns all clipboard access. */
interface TextClipCodec {
    fun create(text: String): ClipEntry

    fun read(entry: ClipEntry): String?
}
