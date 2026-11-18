@file:OptIn(ExperimentalComposeUiApi::class)

package yancey.chelper.desktop.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import yancey.chelper.ui.platform.TextClipCodec

object DesktopTextClipCodec : TextClipCodec {
    override fun create(text: String) = ClipEntry(StringSelection(text))

    override fun read(entry: ClipEntry): String? =
        (entry.nativeClipEntry as? Transferable)
            ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
            ?.getTransferData(DataFlavor.stringFlavor) as? String
}
