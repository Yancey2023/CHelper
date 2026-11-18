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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yancey.chelper.ui.designsystem.CHelperTheme

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 20.dp,
) {
    val selectionProgress by animateFloatAsState(if (selected) 1f else 0f)
    val color by
        animateColorAsState(
            if (selected) CHelperTheme.colors.mainColor else CHelperTheme.colors.iconMain
        )
    val radioButtonModifier =
        if (onClick == null) {
            modifier
        } else {
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
        }

    Canvas(modifier = radioButtonModifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2
        val displayedColor = color.copy(alpha = if (enabled) 1f else 0.5f)
        drawCircle(color = displayedColor, radius = radius, style = Stroke(width = strokeWidth))
        if (selectionProgress > 0f) {
            drawCircle(color = displayedColor, radius = radius * 0.5f * selectionProgress)
        }
    }
}
