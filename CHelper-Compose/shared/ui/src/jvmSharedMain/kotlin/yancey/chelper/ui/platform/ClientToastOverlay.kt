package yancey.chelper.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text

/** Feedback shared by the main application and its floating panels. */
@Composable
fun BoxScope.ClientToastOverlay() {
    val toast by ClientToast.messages.collectAsState()
    LaunchedEffect(toast?.first) {
        toast?.let {
            delay(3500)
            ClientToast.clear(it.first)
        }
    }
    toast?.let {
        Text(
            it.second,
            Modifier.align(Alignment.BottomCenter)
                .padding(20.dp)
                .background(CHelperTheme.colors.backgroundComponentNoTranslate)
                .padding(12.dp),
        )
    }
}
