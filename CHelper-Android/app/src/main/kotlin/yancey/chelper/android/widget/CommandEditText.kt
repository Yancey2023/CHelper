/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey & Ankanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.SelectedString
import yancey.chelper.core.Theme

/**
 * 命令输入框
 */
class CommandEditText : AppCompatEditText {
    private var onTextChanged: ((String) -> Unit)? = null
    private var onSelectionChanged: (() -> Unit)? = null
    private var errorReasons: Array<ErrorReason>? = null
    private var theme: Theme? = null
    private var normalColor: Int = 0
    private var errorReasonPaint: Paint? = null
    private var errorReasonOffsetY = 0
    private var lastTokens: IntArray? = null
    private var isSettingString = false
    private var isEditorMode: Boolean? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    fun init() {
        errorReasonPaint = Paint()
        errorReasonPaint!!.setColor(Color.RED)
        errorReasonPaint!!.strokeWidth = 2f
        errorReasonOffsetY = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            resources.displayMetrics
        ).toInt()
    }

    fun setListener(onTextChanged: (String) -> Unit, onSelectionChanged: () -> Unit) {
        this.onTextChanged = onTextChanged
        this.onSelectionChanged = onSelectionChanged
    }

    fun setTheme(theme: Theme, normalColor: Int) {
        if (this.theme == theme && this.normalColor == normalColor) {
            return
        }
        this.theme = theme
        this.normalColor = normalColor
        // 主题或普通文本颜色变化后，需要忽略缓存强制重新上色
        lastTokens = null
    }

    /**
     * 编辑器模式只改变显示方式，命令本身仍保持单行，避免复制出带换行符的无效命令。
     */
    fun setEditorMode(enabled: Boolean) {
        if (isEditorMode == enabled) return
        isEditorMode = enabled

        isSingleLine = true
        maxLines = if (enabled) Int.MAX_VALUE else 1
        setHorizontallyScrolling(!enabled)
        gravity = if (enabled) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
        val padding = if (enabled) {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics
            ).toInt()
        } else {
            0
        }
        setPadding(padding, padding, padding, padding)
        isVerticalScrollBarEnabled = enabled
        overScrollMode =
            if (enabled) View.OVER_SCROLL_IF_CONTENT_SCROLLS else View.OVER_SCROLL_NEVER
        requestLayout()
        post {
            bringPointIntoView(selectionStart.coerceAtLeast(0))
        }
    }

    override fun onTextChanged(
        text: CharSequence,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (!isSettingString) {
            onTextChanged?.invoke(text.toString())
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!isSettingString) {
            onSelectionChanged?.invoke()
        }
    }

    /**
     * 设置当前选中的内容
     * 
     * @param selectedString 被选择着的文本
     */
    fun setSelectedString(selectedString: SelectedString?) {
        if (selectedString == null) {
            setText(null)
            return
        }
        isSettingString = true
        if (selectedString.text != text.toString()) {
            setText(selectedString.text)
        }
        if (selectionStart != selectedString.selectionStart || selectionEnd != selectedString.selectionEnd) {
            setSelection(selectedString.selectionStart, selectedString.selectionEnd)
        }
        isSettingString = false
    }

    /**
     * 删除所有内容
     */
    fun clear() {
        setText(null)
    }

    private class SpanInfo(val color: Int, val start: Int, val end: Int)

    /**
     * 设置文本颜色
     * 
     * @param tokens 每个字符的类型
     */
    fun setColors(tokens: IntArray?) {
        if (tokens.contentEquals(lastTokens)) {
            return
        }
        val text = this.getText()
        if (text == null || (tokens != null && tokens.isNotEmpty() && text.length != tokens.size)) {
            return
        }

        lastTokens = tokens

        if (theme == null || tokens == null || tokens.isEmpty()) {
            removeForegroundColorSpans()
            return
        }
        installColorSpans(buildColorSpans(tokens))
    }

    /** 摘除全部前景色 span（清空高亮用） */
    private fun removeForegroundColorSpans() {
        val text = getText()
        if (text is Spannable) {
            text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach {
                text.removeSpan(it)
            }
        }
    }

    /** 由 token 序列生成非普通色的着色段（相邻同色已合并） */
    private fun buildColorSpans(tokens: IntArray): List<SpanInfo> {
        val t = theme ?: return emptyList()
        // 普通文本颜色跟随调用方传入的当前主题，而不是按系统 uiMode 解析的资源颜色，
        // 否则应用设置为夜间、系统为亮色时（例如悬浮窗）会解析出亮色主题的文字颜色
        val normalColor = this.normalColor
        val targetSpans = mutableListOf<SpanInfo>()
        var lastIndex = 0
        var lastColor = t.getColorByToken(tokens[0], normalColor)
        for (i in 1..<tokens.size) {
            val color = t.getColorByToken(tokens[i], normalColor)
            if (color != lastColor) {
                if (lastColor != normalColor) { // 普通颜色没必要加Span，可以节省Span对象数
                    targetSpans.add(SpanInfo(lastColor, lastIndex, i))
                }
                lastIndex = i
                lastColor = color
            }
        }
        if (lastColor != normalColor) {
            targetSpans.add(SpanInfo(lastColor, lastIndex, tokens.size))
        }
        return targetSpans
    }

    /** 把目标段与现有 span 做哈希 diff 后应用到文本 */
    private fun installColorSpans(targetSpans: List<SpanInfo>) {
        val text = getText()
        if (text is SpannableStringBuilder) {
            val existSpans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            // 哈希 diff：O(目标段 + 已有段)，取代原来的 O(段²) 双重循环——
            // 长命令（如大段 rawtext JSON）颜色段可达数百，每键一次二次循环会明显掉帧。
            // key = (start, end) 打包成 Long
            val targetMap = HashMap<Long, Int>(targetSpans.size * 2 + 1)
            for (target in targetSpans) {
                targetMap[(target.start.toLong() shl 32) or target.end.toLong()] = target.color
            }

            for (span in existSpans) {
                val spanStart = text.getSpanStart(span)
                val spanEnd = text.getSpanEnd(span)
                val key = (spanStart.toLong() shl 32) or spanEnd.toLong()
                val targetColor = targetMap[key]
                if (targetColor != null && targetColor == span.foregroundColor) {
                    // 命中：保留并消费，后续不再重复添加
                    targetMap.remove(key)
                } else {
                    text.removeSpan(span)
                }
            }

            for ((key, color) in targetMap) {
                val start = (key shr 32).toInt()
                val end = key.toInt()
                text.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else if (text != null) {
            val spannableStringBuilder = SpannableStringBuilder(text)
            for (target in targetSpans) {
                spannableStringBuilder.setSpan(
                    ForegroundColorSpan(target.color),
                    target.start,
                    target.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val selectionStart = getSelectionStart()
            val selectionEnd = getSelectionEnd()
            setText(spannableStringBuilder)
            setSelection(selectionStart, selectionEnd)
        }
    }

    /**
     * 设置错误文本样式
     * 
     * @param errorReasons 错误原因
     */
    fun setErrorReasons(errorReasons: Array<ErrorReason>?) {
        this.errorReasons = errorReasons
        invalidate()
    }

    /**
     * 聚焦并选中一条错误对应的文本，让长命令不用靠手动横向拖动定位。
     */
    fun focusErrorRange(start: Int, end: Int): Boolean {
        val length = text?.length ?: 0
        if (start < 0 || end < 0 || start > end || end > length) {
            return false
        }

        requestFocus()
        setSelection(start, end)
        post {
            bringPointIntoView(start)
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // 绘制命令错误位置的下划线
        if (errorReasons != null) {
            val layout = getLayout()
            val length = text?.length ?: 0
            for (errorReason in errorReasons) {
                var start = errorReason.start
                var end = errorReason.end
                if (start < 0 || end < start || end > length) {
                    continue
                }
                if (start == end && length != 0) {
                    if (start == length) {
                        start--
                    } else {
                        end++
                    }
                }

                val lineStart = layout.getLineForOffset(start)
                val lineEnd = layout.getLineForOffset(end)

                if (lineStart == lineEnd) {
                    val y = (layout.getLineBottom(lineStart) + errorReasonOffsetY).toFloat()
                    canvas.drawLine(
                        layout.getPrimaryHorizontal(start),
                        y,
                        layout.getSecondaryHorizontal(end),
                        y,
                        errorReasonPaint!!
                    )
                } else {
                    val firstLineY =
                        (layout.getLineBottom(lineStart) + errorReasonOffsetY).toFloat()
                    canvas.drawLine(
                        layout.getPrimaryHorizontal(start),
                        firstLineY,
                        layout.getPrimaryHorizontal(layout.getLineEnd(lineStart)),
                        firstLineY,
                        errorReasonPaint!!
                    )
                    for (i in lineStart + 1 until lineEnd) {
                        val y = (layout.getLineBottom(i) + errorReasonOffsetY).toFloat()
                        canvas.drawLine(
                            layout.getPrimaryHorizontal(layout.getLineStart(i)),
                            y,
                            layout.getPrimaryHorizontal(layout.getLineEnd(i)),
                            y,
                            errorReasonPaint!!
                        )
                    }
                    val lastLineY = (layout.getLineBottom(lineEnd) + errorReasonOffsetY).toFloat()
                    canvas.drawLine(
                        layout.getPrimaryHorizontal(layout.getLineStart(lineEnd)),
                        lastLineY,
                        layout.getSecondaryHorizontal(end),
                        lastLineY,
                        errorReasonPaint!!
                    )
                }
            }
        }
    }

}
