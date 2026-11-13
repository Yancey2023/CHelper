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

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TextFieldWithIcon(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    hint: String? = null,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 10.dp,
    clipCornerSize: Dp = 10.dp,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    style: TextStyle = TextStyle(),
    outputTransformation: OutputTransformation? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    TextField(
        state,
        modifier,
        contentAlignment,
        hint,
        horizontalPadding,
        verticalPadding,
        clipCornerSize,
        lineLimits,
        style,
        outputTransformation,
        leadingIcon,
        trailingIcon,
    )
}
