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

package yancey.chelper.ui.designsystem.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yancey.chelper.ui.designsystem.CHelperTheme

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
    val colors = CHelperTheme.colors
    BasicTextField(
        state = state,
        modifier = modifier,
        lineLimits = lineLimits,
        outputTransformation = outputTransformation,
        textStyle =
            style.copy(
                color = if (style.color == Color.Unspecified) colors.textMain else style.color,
                fontSize = if (style.fontSize == TextUnit.Unspecified) 16.sp else style.fontSize,
                textAlign =
                    if (contentAlignment is BiasAlignment && contentAlignment.horizontalBias == 0f)
                        TextAlign.Center
                    else TextAlign.Start,
            ),
        cursorBrush = SolidColor(colors.mainColor),
        decorator = { inner ->
            InputFrame(
                state.text.isEmpty(),
                hint,
                horizontalPadding,
                verticalPadding,
                clipCornerSize,
                contentAlignment,
                leadingIcon,
                trailingIcon,
                inner,
            )
        },
    )
}

@Composable
fun SecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    hint: String? = null,
    visible: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    BasicSecureTextField(
        state = state,
        modifier = modifier,
        textObfuscationMode =
            if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        textStyle = TextStyle(fontSize = 16.sp, color = CHelperTheme.colors.textMain),
        cursorBrush = SolidColor(CHelperTheme.colors.mainColor),
        decorator = { inner ->
            InputFrame(
                state.text.isEmpty(),
                hint,
                10.dp,
                10.dp,
                10.dp,
                Alignment.CenterStart,
                leadingIcon,
                trailingIcon,
                inner,
            )
        },
    )
}

@Composable
private fun InputFrame(
    empty: Boolean,
    hint: String?,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    corner: Dp,
    alignment: Alignment,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    inner: @Composable () -> Unit,
) {
    val inputAlignment =
        Alignment.Vertical { size, space ->
            alignment.align(IntSize(0, size), IntSize(0, space), LayoutDirection.Ltr).y
        }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(CHelperTheme.colors.backgroundComponent)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f).align(inputAlignment), contentAlignment = alignment) {
            if (empty && hint != null)
                Text(hint, style = TextStyle(color = CHelperTheme.colors.textHint))
            inner()
        }
        trailing?.invoke()
    }
}
