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

package yancey.chelper.ui.common.dialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import yancey.chelper.ui.common.CHelperTheme

/**
 * 自定义 Dialog 组件，用于替代 Compose 的 Dialog
 */
@Composable
fun CustomDialog(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    properties: CustomDialogProperties = CustomDialogProperties(),
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (visible) 0.5f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "backgroundAlpha"
    )
    val dialogScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = tween(durationMillis = 300),
        label = "dialogScale"
    )
    val dialogAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "dialogAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(
            focusable = properties.focusable,
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = false
        ),
        onDismissRequest = onDismissRequest
    ) {
        if (properties.dimBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
                    .clickable(
                        enabled = dismissOnClickOutside,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (dismissOnClickOutside) {
                            onDismissRequest()
                        }
                    }
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (properties.usePlatformDefaultWidth) Modifier.fillMaxSize(0.8f) else Modifier
                    )
                    .scale(dialogScale)
                    .alpha(dialogAlpha),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

/**
 * 自定义 Dialog 属性
 */
data class CustomDialogProperties(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val usePlatformDefaultWidth: Boolean = true,
    val dimBackground: Boolean = true,
    val focusable: Boolean = true
)

/**
 * 对话框内容容器，提供统一的样式
 */
@Composable
fun DialogContainer(
    modifier: Modifier = Modifier,
    cornerSize: Dp = 10.dp,
    backgroundNoTranslate: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerSize))
            .background(
                if (backgroundNoTranslate) {
                    CHelperTheme.colors.backgroundComponentNoTranslate
                } else {
                    CHelperTheme.colors.background
                }
            )
    ) {
        content()
    }
}
