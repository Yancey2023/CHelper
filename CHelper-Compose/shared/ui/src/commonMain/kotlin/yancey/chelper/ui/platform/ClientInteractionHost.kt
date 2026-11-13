package yancey.chelper.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import yancey.chelper.ui.designsystem.dialog.AppDialogHost

/** One interaction root per window, linked to the host's system navigation dispatcher. */
@Composable
fun ClientInteractionHost(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showMessages: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val owner =
        checkNotNull(LocalNavigationEventDispatcherOwner.current) {
            "The window must provide a NavigationEventDispatcherOwner"
        }
    val messages = rememberClientMessageState(active = showMessages)
    val dispatcher = owner.navigationEventDispatcher
    val input = remember(dispatcher) { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    CompositionLocalProvider(
        LocalAppBackRequest provides input::backCompleted,
        LocalClientMessageState provides messages,
    ) {
        // NavHost handles navigation itself. Panels without a NavHost supply their close action.
        AppBackHandler(enabled = onBack != null) { onBack?.invoke() }
        Box(modifier.fillMaxSize()) {
            if (showMessages) {
                Box(Modifier.matchParentSize().zIndex(1f)) { ClientMessageHost(messages) }
            }
            AppDialogHost(content)
        }
    }
}
