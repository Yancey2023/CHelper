package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/** Navigation after a screen has approved leaving; bypasses its own exit guard. */
val LocalAppBackAction = staticCompositionLocalOf<() -> Unit> { {} }
/** User-initiated back, dispatched through the same chain as the system back event. */
val LocalAppBackRequest = staticCompositionLocalOf<() -> Unit> { {} }
val LocalWideLayout = staticCompositionLocalOf { false }

@Composable
fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
