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

package yancey.chelper.android.ui.completion

import android.util.TypedValue
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import yancey.chelper.R
import yancey.chelper.android.widget.CommandEditText
import yancey.chelper.core.SelectedString
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.SyntaxTheme as Theme
import yancey.chelper.ui.feature.command.CommandErrorSelection
import yancey.chelper.ui.feature.command.CompletionViewModel

@Composable
fun AndroidCommandEditor(
    viewModel: CompletionViewModel,
    modifier: androidx.compose.ui.Modifier,
    errorRange: CommandErrorSelection?,
) {
    val commandEditText = remember { arrayOfNulls<CommandEditText>(1) }
    LaunchedEffect(errorRange) {
        errorRange?.let { commandEditText[0]?.focusErrorRange(it.start, it.end) }
    }
    val theme = CHelperTheme.theme
    val textMain = CHelperTheme.colors.textMain
    val textSecondary = CHelperTheme.colors.textSecondary
    val hintStr = stringResource(R.string.layout_completion_command_hint)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CommandEditText(context).apply {
                commandEditText[0] = this
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                background = null
                hint = hintStr
                setEditorMode(viewModel.isCommandEditorMode)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setListener(
                    { str ->
                        viewModel.command.edit {
                            replace(0, length, str)
                            selection = TextRange(selectionStart, selectionEnd)
                        }
                    },
                    {
                        viewModel.command.edit {
                            selection = TextRange(selectionStart, selectionEnd)
                        }
                    },
                )
            }
        },
        update = { view ->
            view.setEditorMode(viewModel.isCommandEditorMode)

            view.setTextColor(textMain.toArgb())
            view.setHintTextColor(textSecondary.toArgb())
            view.setTheme(
                if (theme == CHelperTheme.Theme.Light) Theme.THEME_DAY else Theme.THEME_NIGHT,
                textMain.toArgb(),
            )
            val str = viewModel.command.text.toString()
            val selectionStart = viewModel.command.selection.start
            val selectionEnd = viewModel.command.selection.end
            if (
                view.text.toString() != str ||
                    view.selectionStart != selectionStart ||
                    view.selectionEnd != selectionEnd
            ) {
                view.setSelectedString(SelectedString(str, selectionStart, selectionEnd))
            }
            view.setErrorReasons(viewModel.errorReasons)
            view.setColors(viewModel.syntaxHighlightTokens)
        },
    )
}
