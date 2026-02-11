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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import yancey.chelper.ui.common.CHelperTheme

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    height: Dp = 24.dp,
    thumbRadius: Dp = 10.dp
) {
    val thumbPosition by animateFloatAsState(targetValue = if (checked) 1f else 0f)
    val trackColor by animateColorAsState(
        if (checked)
            CHelperTheme.colors.mainColorSecondary
        else
            Color(0xFFAFAFAF)
    )
    val thumbColor by animateColorAsState(
        if (checked)
            CHelperTheme.colors.mainColor
        else
            Color(0xFFECECEC)
    )
    Box(
        modifier = modifier
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .size(width, height)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
        ) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(((width.toPx() - thumbRadius.toPx() * 2) * thumbPosition).toInt(), 0)
                }
                .size(thumbRadius * 2)
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    clip = false
                )
        ) {
            drawCircle(
                color = thumbColor,
                radius = thumbRadius.toPx(),
                center = center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SwitchPreview() {
    var checked by remember { mutableStateOf(true) }
    CHelperTheme(
        theme = CHelperTheme.Theme.Light,
        backgroundBitmap = null
    ) {
        Switch(checked, { checked = it })
    }
}
