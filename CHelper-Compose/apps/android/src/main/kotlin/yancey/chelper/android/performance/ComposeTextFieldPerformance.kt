package yancey.chelper.android.performance

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import java.lang.ref.WeakReference
import yancey.chelper.ui.feature.command.CommandGlyphMaskColor

internal object ComposeTextFieldPerformance {
    private val last = ThreadLocal<Entry>()
    private val maskColor = CommandGlyphMaskColor.toArgb()

    // BoringLayout delegates even transparent strings to Canvas, which shapes them again.
    // Suppress the submission only when the shared editor owns the glyph drawing.
    @JvmStatic
    fun drawPlainText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val masked =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                text.length > 1024 &&
                paint.color == maskColor &&
                paint.shader == null &&
                paint.colorFilter == null &&
                paint.xfermode == null &&
                paint.shadowLayerRadius == 0f
        if (!masked) canvas.drawText(text, x, y, paint)
    }

    /** Both operands are immutable Foundation text snapshots backed by strings. */
    @JvmStatic
    fun sameText(left: CharSequence, right: CharSequence): Boolean =
        left === right || (left.length == right.length && left.toString() == right.toString())

    /** Backport the immutable-layout caret lookup used by upstream Foundation. */
    @JvmStatic
    fun cursorRect(layout: TextLayoutResult, offset: Int): Rect {
        if (layout.layoutInput.text.length <= 1024) return layout.getCursorRect(offset)
        val cached = last.get()
        if (cached != null && cached.layout.get() === layout && cached.offset == offset) {
            return cached.rect
        }
        return layout.getCursorRect(offset).also { rect ->
            last.set(Entry(WeakReference(layout), offset, rect))
        }
    }

    private class Entry(
        val layout: WeakReference<TextLayoutResult>,
        val offset: Int,
        val rect: Rect,
    )
}
