package yancey.chelper.ui.feature.command

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import yancey.chelper.domain.command.CommandProblem
import yancey.chelper.resources.Res
import yancey.chelper.resources.layout_completion_command_hint
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.designsystem.widget.Text

data class CommandErrorSelection(val start: Int, val end: Int, val request: Long)

/** The Compose implementation of the original editor: wrapped display still holds one command. */
@Composable
fun ComposeCommandEditor(
    command: TextFieldState,
    editorMode: Boolean,
    tokens: IntArray?,
    errors: List<CommandProblem>,
    modifier: Modifier,
    errorSelection: CommandErrorSelection?,
) {
    val focus = remember { FocusRequester() }
    val dark = CHelperTheme.theme == CHelperTheme.Theme.Dark
    val colors = CHelperTheme.colors
    LaunchedEffect(command, errorSelection) {
        errorSelection?.let {
            command.edit {
                selection = TextRange(it.start.coerceIn(0, length), it.end.coerceIn(0, length))
            }
            focus.requestFocus()
        }
    }
    BasicTextField(
        state = command,
        modifier = modifier.focusRequester(focus),
        lineLimits =
            if (editorMode) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
        inputTransformation =
            InputTransformation {
                for (i in length - 1 downTo 0) {
                    if (charAt(i) == '\n' || charAt(i) == '\r') replace(i, i + 1, " ")
                }
            },
        textStyle = TextStyle(color = colors.textMain, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.mainColor),
        outputTransformation =
            OutputTransformation {
                val styled = highlightCommand(asCharSequence().toString(), tokens, dark)
                styled.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
                errors.forEach {
                    val start = it.start.coerceIn(0, length)
                    val end = it.end.coerceIn(start, length)
                    if (start < end)
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                }
            },
        decorator = { input ->
            Box {
                if (command.text.isEmpty())
                    Text(
                        stringResource(Res.string.layout_completion_command_hint),
                        style = TextStyle(color = colors.textHint),
                    )
                input()
            }
        },
    )
}
