package yancey.chelper.ui.platform

import androidx.compose.runtime.*

val LocalAppBackAction = staticCompositionLocalOf<() -> Unit> { {} }
val LocalPlatformHeaderBackAction = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalWideLayout = staticCompositionLocalOf { false }

class AppBackDispatcher {
    private val handlers = mutableListOf<() -> Unit>()

    fun register(handler: () -> Unit): () -> Unit {
        handlers += handler
        return { handlers.remove(handler) }
    }

    fun back(fallback: () -> Unit) {
        handlers.lastOrNull()?.invoke() ?: fallback()
    }
}

val LocalAppBackDispatcher = staticCompositionLocalOf { AppBackDispatcher() }

@Composable
internal fun RegisterAppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalAppBackDispatcher.current
    val action by rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, enabled) {
        val remove = if (enabled) dispatcher.register { action() } else ({})
        onDispose { remove() }
    }
}

@Composable expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
