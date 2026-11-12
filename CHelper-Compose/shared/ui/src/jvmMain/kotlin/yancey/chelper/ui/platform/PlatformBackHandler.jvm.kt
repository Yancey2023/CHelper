package yancey.chelper.ui.platform

import androidx.compose.runtime.*

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    RegisterAppBackHandler(enabled, onBack)
}
