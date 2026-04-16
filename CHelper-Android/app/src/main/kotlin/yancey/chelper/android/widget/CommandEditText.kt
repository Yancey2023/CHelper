/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatEditText
import yancey.chelper.R
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
    private var errorReasonPaint: Paint? = null
    private var errorReasonOffsetY = 0
    private var lastTokens: IntArray? = null
    private var isSettingString = false

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

    fun setTheme(theme: Theme) {
        this.theme = theme
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
            if (text is SpannableStringBuilder) {
                text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach {
                    text.removeSpan(it)
                }
            }
            return
        }

        val normalColor = context.getColor(R.color.text_main)
        val targetSpans = mutableListOf<SpanInfo>()
        
        var lastIndex = 0
        var lastColor = theme!!.getColorByToken(tokens[0], normalColor)
        for (i in 1..<tokens.size) {
            val color = theme!!.getColorByToken(tokens[i], normalColor)
            if (color != lastColor) {
                if (lastColor != normalColor) { // 普通颜色没必要加Span，可以节省Span对象数，如果是整段清除的话下面会处理
                    targetSpans.add(SpanInfo(lastColor, lastIndex, i))
                }
                lastIndex = i
                lastColor = color
            }
        }
        if (lastColor != normalColor) {
            targetSpans.add(SpanInfo(lastColor, lastIndex, tokens.size))
        }

        if (text is SpannableStringBuilder) {
            val existSpans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            val matchedSpans = BooleanArray(targetSpans.size)
            
            for (span in existSpans) {
                val spanStart = text.getSpanStart(span)
                val spanEnd = text.getSpanEnd(span)
                val color = span.foregroundColor
                
                var found = false
                for (j in targetSpans.indices) {
                    if (!matchedSpans[j]) {
                        val target = targetSpans[j]
                        if (target.start == spanStart && target.end == spanEnd && target.color == color) {
                            matchedSpans[j] = true
                            found = true
                            break
                        }
                    }
                }
                
                if (!found) {
                    text.removeSpan(span)
                }
            }
            
            for (j in targetSpans.indices) {
                if (!matchedSpans[j]) {
                    val target = targetSpans[j]
                    text.setSpan(
                        ForegroundColorSpan(target.color),
                        target.start,
                        target.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        } else {
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
                if (start < 0 || end > length) {
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
                        layout.getLineEnd(lineStart).toFloat(),
                        firstLineY,
                        errorReasonPaint!!
                    )
                    for (i in lineStart + 1..<lineEnd - 1) {
                        val y = (layout.getLineBottom(i) + errorReasonOffsetY).toFloat()
                        canvas.drawLine(
                            layout.getLineStart(i).toFloat(),
                            y,
                            layout.getLineEnd(i).toFloat(),
                            y,
                            errorReasonPaint!!
                        )
                    }
                    val lastLineY = (layout.getLineBottom(lineEnd) + errorReasonOffsetY).toFloat()
                    canvas.drawLine(
                        layout.getLineStart(lineEnd).toFloat(),
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
