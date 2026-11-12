package yancey.chelper.ui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    RegisterAppBackHandler(enabled, onBack)
    BackHandler(enabled, onBack)
}
