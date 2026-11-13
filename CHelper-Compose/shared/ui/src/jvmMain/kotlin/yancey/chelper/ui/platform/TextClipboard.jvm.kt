@file:OptIn(ExperimentalComposeUiApi::class)

package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

internal actual fun textClipEntry(text: String): ClipEntry = ClipEntry(StringSelection(text))

@Composable
internal actual fun rememberClipTextReader(): (ClipEntry) -> String? = remember {
    { entry ->
        (entry.nativeClipEntry as? Transferable)
            ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
            ?.getTransferData(DataFlavor.stringFlavor) as? String
    }
}
