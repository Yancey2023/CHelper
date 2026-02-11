/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.android.common.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import yancey.chelper.R;
import yancey.chelper.core.ErrorReason;
import yancey.chelper.core.SelectedString;
import yancey.chelper.core.Theme;

/**
 * 命令输入框
 */
public class CommandEditText extends AppCompatEditText {

    private final SelectedString[] history = new SelectedString[50];
    private int which = 0;
    @Nullable
    private Consumer<String> onTextChanged;
    @Nullable
    private Runnable onSelectionChanged;
    private BooleanSupplier isGuiLoaded;
    private ErrorReason[] errorReasons;
    @Nullable
    private Theme theme;
    private Paint errorReasonPaint;
    private int errorReasonOffsetY;
    private int[] lastTokens;

    public CommandEditText(Context context) {
        super(context);
        init();
    }

    public CommandEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CommandEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void init() {
        history[0] = new SelectedString("", 0);
        errorReasonPaint = new Paint();
        errorReasonPaint.setColor(Color.RED);
        errorReasonPaint.setStrokeWidth(2);
        errorReasonOffsetY = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                10,
                getResources().getDisplayMetrics()
        );
    }

    public void setListener(@Nullable Consumer<String> onTextChanged, @Nullable Runnable onSelectionChanged, @NonNull BooleanSupplier isGuiLoaded) {
        this.onTextChanged = onTextChanged;
        this.onSelectionChanged = onSelectionChanged;
        this.isGuiLoaded = isGuiLoaded;
    }

    public void setTheme(@NotNull Theme theme) {
        this.theme = theme;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (onTextChanged != null) {
            onTextChanged.accept(text.toString());
        }
        tryAddHistory(getSelectedString());
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (isGuiLoaded != null && isGuiLoaded.getAsBoolean() && onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    public void tryAddHistory(SelectedString selectedString) {
        // 如果界面没有加载出来或着与当前历史记录相同，就不记录
        if (isGuiLoaded == null || !isGuiLoaded.getAsBoolean() || (history[which] != null && Objects.equals(selectedString.text, history[which].text))) {
            return;
        }
        if (which == history.length - 1) {
            // 如果达到数量限制，就舍弃历史最旧的记录
            for (int i = 0; i < which; i++) {
                history[i] = history[i + 1];
            }
        } else {
            // 如果还可以记录，就把后面的记录全部设置为空
            which++;
            if (which != history.length - 1) {
                history[which + 1] = null;
            }
        }
        // 记录当前内容
        history[which] = selectedString;
    }

    /**
     * 获取当前选中的内容
     */
    public SelectedString getSelectedString() {
        Editable text = getText();
        if (text == null) {
            return new SelectedString("", 0, 0);
        } else {
            int selectionStart = Selection.getSelectionStart(text);
            int selectionEnd = Selection.getSelectionEnd(text);
            if (selectionStart > selectionEnd) {
                int temp = selectionStart;
                selectionStart = selectionEnd;
                selectionEnd = temp;
            }
            return new SelectedString(text.toString(), selectionStart, selectionEnd);
        }
    }

    /**
     * 设置当前选中的内容
     *
     * @param selectedString 被选择着的文本
     */
    public void setSelectedString(SelectedString selectedString) {
        if (selectedString == null) {
            setText(null);
            return;
        }
        tryAddHistory(selectedString);
        if (!Objects.equals(selectedString.text, Objects.requireNonNull(getText()).toString())) {
            setText(selectedString.text);
        }
        if (getSelectionStart() != selectedString.selectionStart || getSelectionEnd() != selectedString.selectionEnd) {
            setSelection(selectedString.selectionStart, selectedString.selectionEnd);
        }
    }

    /**
     * 撤回
     */
    public void undo() {
        if (which != 0) {
            setSelectedString(history[--which]);
        }
    }

    /**
     * 恢复
     */
    public void redo() {
        if (which != history.length - 1 && history[which + 1] != null) {
            setSelectedString(history[++which]);
        }
    }

    /**
     * 删除所有内容
     */
    public void clear() {
        setText(null);
    }

    /**
     * 设置文本颜色
     *
     * @param tokens 每个字符的类型
     */
    public void setColors(@Nullable int[] tokens) {
        if (tokens == lastTokens) {
            return;
        }
        Editable text = this.getText();
        if (text == null || (tokens != null && tokens.length != 0 && text.length() != tokens.length)) {
            return;
        }
        lastTokens = tokens;
        boolean isSpannableStringBuilder;
        SpannableStringBuilder spannableStringBuilder;
        if (text instanceof SpannableStringBuilder spannableStringBuilder0) {
            Arrays.stream(spannableStringBuilder0.getSpans(0, spannableStringBuilder0.length(), ForegroundColorSpan.class))
                    .forEach(spannableStringBuilder0::removeSpan);
            if (theme == null || tokens == null || tokens.length == 0) {
                return;
            }
            isSpannableStringBuilder = true;
            spannableStringBuilder = spannableStringBuilder0;
        } else {
            if (theme == null || tokens == null || tokens.length == 0) {
                return;
            }
            isSpannableStringBuilder = false;
            spannableStringBuilder = new SpannableStringBuilder(text);
        }
        int normalColor = getContext().getColor(R.color.text_main);
        int lastIndex = 0;
        int lastColor = theme.getColorByToken(tokens[0], normalColor);
        for (int i = 1; i < tokens.length; i++) {
            int color = theme.getColorByToken(tokens[i], normalColor);
            if (color != lastColor) {
                spannableStringBuilder.setSpan(new ForegroundColorSpan(lastColor), lastIndex, i, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                lastIndex = i;
                lastColor = color;
            }
        }
        spannableStringBuilder.setSpan(new ForegroundColorSpan(lastColor), lastIndex, tokens.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        if (!isSpannableStringBuilder) {
            int selectionStart = getSelectionStart();
            int selectionEnd = getSelectionEnd();
            setText(spannableStringBuilder);
            setSelection(selectionStart, selectionEnd);
        }
    }

    /**
     * 设置错误文本样式
     *
     * @param errorReasons 错误原因
     */
    public void setErrorReasons(ErrorReason[] errorReasons) {
        this.errorReasons = errorReasons;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        // 绘制命令错误位置的下划线
        if (errorReasons != null) {
            Layout layout = getLayout();
            int length = Objects.requireNonNull(getText()).length();
            for (ErrorReason errorReason : errorReasons) {
                int start = errorReason.start;
                int end = errorReason.end;
                if (start < 0 || end > length) {
                    continue;
                }
                if (start == end && length != 0) {
                    if (start == length) {
                        start--;
                    } else {
                        end++;
                    }
                }

                int lineStart = layout.getLineForOffset(start);
                int lineEnd = layout.getLineForOffset(end);

                if (lineStart == lineEnd) {
                    float y = layout.getLineBottom(lineStart) + errorReasonOffsetY;
                    canvas.drawLine(layout.getPrimaryHorizontal(start), y, layout.getSecondaryHorizontal(end), y, errorReasonPaint);
                } else {
                    float firstLineY = layout.getLineBottom(lineStart) + errorReasonOffsetY;
                    canvas.drawLine(layout.getPrimaryHorizontal(start), firstLineY, layout.getLineEnd(lineStart), firstLineY, errorReasonPaint);
                    for (int i = lineStart + 1; i < lineEnd - 1; i++) {
                        float y = layout.getLineBottom(i) + errorReasonOffsetY;
                        canvas.drawLine(layout.getLineStart(i), y, layout.getLineEnd(i), y, errorReasonPaint);
                    }
                    float lastLineY = layout.getLineBottom(lineEnd) + errorReasonOffsetY;
                    canvas.drawLine(layout.getLineStart(lineEnd), lastLineY, layout.getSecondaryHorizontal(end), lastLineY, errorReasonPaint);
                }
            }
        }
    }

}
