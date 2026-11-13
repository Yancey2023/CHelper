@file:OptIn(ExperimentalComposeUiApi::class)

package yancey.chelper.ui.platform

import android.content.ClipData
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext

internal actual fun textClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText(null, text))

@Composable
internal actual fun rememberClipTextReader(): (ClipEntry) -> String? {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        { entry ->
            entry.clipData
                .takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        }
    }
}
