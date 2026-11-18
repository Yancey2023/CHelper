package yancey.chelper.ui.feature.library.browse

import androidx.compose.animation.core.Transition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.library.mcd.McdPreview

enum class LibraryDetailPhase {
    Loading,
    Error,
    Ready,
}

/** Readiness follows the prepared document, without waiting for navigation or a minimum hold. */
fun libraryDetailPhase(ready: Boolean, failed: Boolean): LibraryDetailPhase =
    when {
        failed -> LibraryDetailPhase.Error
        ready -> LibraryDetailPhase.Ready
        else -> LibraryDetailPhase.Loading
    }

/** Only optional coloring waits for settled motion. The document itself is never held back. */
@Composable
fun rememberLibraryHighlightingStarted(
    preview: McdPreview?,
    transition: Transition<LibraryDetailPhase>,
): Boolean {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var started by remember(preview) { mutableStateOf(false) }
    val settled =
        transition.currentState == LibraryDetailPhase.Ready &&
            transition.targetState == LibraryDetailPhase.Ready &&
            !transition.isRunning
    LaunchedEffect(preview, settled, lifecycle) {
        if (preview != null && settled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
            withFrameNanos {}
            started = true
        }
    }
    // Keep colors on return previews instead of reverting highlighted rows to plain text.
    return started
}

/** One static placeholder for fetching, settings hydration and document preparation. */
@Composable
fun LibraryDetailSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 10.dp).semantics {
            contentDescription = "正在加载命令库"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        Spacer(
            Modifier.fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
        )
        Spacer(Modifier.height(18.dp))
        repeat(5) {
            Spacer(
                Modifier.fillMaxWidth(if (it % 2 == 0) 1f else 0.8f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CHelperTheme.colors.backgroundComponent)
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
