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

package yancey.chelper.ui.common.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.layout.Surface

@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    hint: String? = null,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 10.dp,
    clipCornerSize: Dp = 10.dp,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    style: TextStyle = TextStyle(),
    outputTransformation: OutputTransformation? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    BasicTextField(
        state = state,
        modifier = modifier,
        outputTransformation = outputTransformation,
        textStyle = style.copy(
            color = if (style.color == Color.Unspecified) CHelperTheme.colors.textMain else style.color,
            fontSize = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize,
            textAlign =
                if (contentAlignment is BiasAlignment && contentAlignment.horizontalBias == 0f)
                    TextAlign.Center
                else
                    TextAlign.Start,
        ),
        lineLimits = lineLimits,
        cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
        decorator = { innerTextField ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = contentAlignment,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding,
                clipCornerSize = clipCornerSize,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        innerTextField()
                        if (state.text.isEmpty() && hint != null) {
                            Text(
                                text = hint,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = CHelperTheme.colors.textHint,
                                    fontSize = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize
                                ),
                            )
                        }
                    }
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        trailingIcon()
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TextFieldPreview() {
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        TextField(
            state = rememberTextFieldState(),
            modifier = Modifier
                .padding(15.dp)
                .width(100.dp)
                .height(40.dp)
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.CenterStart,
            hint = "Hint",
            clipCornerSize = 20.dp,
            verticalPadding = 0.dp,
            lineLimits = TextFieldLineLimits.SingleLine
        )
    }
}
