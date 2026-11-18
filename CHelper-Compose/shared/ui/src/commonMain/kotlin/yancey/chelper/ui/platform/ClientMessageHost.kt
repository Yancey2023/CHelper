package yancey.chelper.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextButton

/** Desktop feedback presentation; Android messages go directly to its Toaster presenter. */
@Stable
internal class ClientMessageState {
    var message by mutableStateOf<String?>(null)
}

internal val LocalClientMessageState = staticCompositionLocalOf<ClientMessageState?> { null }

@Composable
internal fun rememberClientMessageState(active: Boolean = true): ClientMessageState {
    LocalClientMessageState.current?.let {
        return it
    }
    val state = remember { ClientMessageState() }
    val owner = LocalLifecycleOwner.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state, owner, active, accessibility) {
        if (active)
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ClientMessages.messages.collectLatest { message ->
                    state.message = message
                    try {
                        delay(
                            accessibility?.calculateRecommendedTimeoutMillis(
                                originalTimeoutMillis = 3500L,
                                containsIcons = false,
                                containsText = true,
                                containsControls = true,
                            ) ?: 3500L
                        )
                    } finally {
                        state.message = null
                    }
                }
            }
    }
    return state
}

@Composable
internal fun BoxScope.ClientMessageHost(state: ClientMessageState) {
    val message = state.message ?: return
    Row(
        Modifier.align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .imePadding()
            .padding(12.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CHelperTheme.colors.backgroundComponentNoTranslate)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f).padding(vertical = 12.dp))
        TextButton(onClick = { state.message = null }) { Text("关闭") }
    }
}
