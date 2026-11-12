package yancey.chelper.ui.designsystem.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import yancey.chelper.ui.designsystem.text.SyntaxTheme as Theme

fun highlightCommand(command: String, tokens: IntArray?, isDark: Boolean): AnnotatedString {
    if (tokens == null || tokens.isEmpty() || command.isEmpty()) {
        return AnnotatedString(command)
    }
    val theme = if (isDark) Theme.THEME_NIGHT else Theme.THEME_DAY
    val normalColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    return buildAnnotatedString {
        append(command)
        var lastIndex = 0
        var lastColor = theme.getColorByToken(tokens[0], normalColor)
        val length = minOf(tokens.size, command.length)
        for (i in 1 until length) {
            val color = theme.getColorByToken(tokens[i], normalColor)
            if (color != lastColor) {
                addStyle(style = SpanStyle(color = Color(lastColor)), start = lastIndex, end = i)
                lastIndex = i
                lastColor = color
            }
        }
        addStyle(style = SpanStyle(color = Color(lastColor)), start = lastIndex, end = length)
    }
}
