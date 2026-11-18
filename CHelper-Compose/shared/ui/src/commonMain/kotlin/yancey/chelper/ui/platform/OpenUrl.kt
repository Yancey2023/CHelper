package yancey.chelper.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.CancellationException

/** All external web links use Compose's host integration and the same failure feedback. */
@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return remember(handler) { { url -> openWebUrl(handler, url) } }
}

internal fun openWebUrl(
    handler: UriHandler,
    url: String,
    onFailure: (String) -> Unit = ClientMessages::show,
) {
    try {
        require(url.substringBefore(':').lowercase() in setOf("http", "https"))
        handler.openUri(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        onFailure("无法打开链接")
    }
}
