package yancey.chelper.ui.designsystem.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import yancey.chelper.ui.designsystem.text.SyntaxTheme as Theme

fun highlightCommand(
    command: String,
    tokens: IntArray?,
    isDark: Boolean,
    start: Int = 0,
    end: Int = command.length,
): AnnotatedString {
    if (tokens == null || tokens.isEmpty() || command.isEmpty()) {
        return AnnotatedString(command)
    }
    val first = start.coerceIn(0, minOf(tokens.size, command.length))
    val limit = end.coerceIn(first, minOf(tokens.size, command.length))
    if (first == limit) return AnnotatedString(command)
    val theme = if (isDark) Theme.THEME_NIGHT else Theme.THEME_DAY
    val normalColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    return buildAnnotatedString {
        append(command)
        var lastIndex = first
        var lastColor = theme.getColorByToken(tokens[first], normalColor)
        for (i in first + 1 until limit) {
            val color = theme.getColorByToken(tokens[i], normalColor)
            if (color != lastColor) {
                addStyle(style = SpanStyle(color = Color(lastColor)), start = lastIndex, end = i)
                lastIndex = i
                lastColor = color
            }
        }
        addStyle(style = SpanStyle(color = Color(lastColor)), start = lastIndex, end = limit)
    }
}
