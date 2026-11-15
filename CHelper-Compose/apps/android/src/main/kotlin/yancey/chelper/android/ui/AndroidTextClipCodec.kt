@file:OptIn(ExperimentalComposeUiApi::class)

package yancey.chelper.android.ui

import android.content.ClipData
import android.content.Context
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import yancey.chelper.ui.platform.TextClipCodec

class AndroidTextClipCodec(context: Context) : TextClipCodec {
    private val context = context.applicationContext

    override fun create(text: String) = ClipEntry(ClipData.newPlainText(null, text))

    override fun read(entry: ClipEntry) =
        entry.clipData.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
