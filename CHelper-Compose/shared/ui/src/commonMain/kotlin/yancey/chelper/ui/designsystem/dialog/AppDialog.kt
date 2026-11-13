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

package yancey.chelper.ui.designsystem.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.platform.AppBackHandler
import yancey.chelper.ui.platform.LocalAppBackRequest

/** In-window modal options; no platform Dialog, Popup or additional OS window is created. */
data class AppDialogProperties(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val usePlatformDefaultWidth: Boolean = true,
)

private class DialogEntry(val render: @Composable (Boolean) -> Unit)

private val LocalDialogs = staticCompositionLocalOf<MutableList<DialogEntry>?> { null }

/** Renders dialogs above the whole host, even when requested from a small nested layout. */
@Composable
internal fun AppDialogHost(content: @Composable BoxScope.() -> Unit) {
    val entries = remember { mutableStateListOf<DialogEntry>() }
    val backgroundFocus = remember { FocusRequester() }
    val covered = entries.isNotEmpty()
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (wasCovered && !covered) backgroundFocus.requestFocus()
        wasCovered = covered
    }
    CompositionLocalProvider(LocalDialogs provides entries) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)
                    .focusRequester(backgroundFocus)
                    .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
                    .focusRestorer()
                    .focusGroup(),
                content = content,
            )
            entries.forEach { entry -> key(entry) { entry.render(entry === entries.lastOrNull()) } }
        }
    }
}

/** Registers shared content in the existing Compose window, including Android system overlays. */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: AppDialogProperties = AppDialogProperties(),
    content: @Composable () -> Unit,
) {
    val entries = checkNotNull(LocalDialogs.current) { "AppDialog requires ClientInteractionHost" }
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val currentContent by rememberUpdatedState(content)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    val currentProperties by rememberUpdatedState(properties)
    val entry = remember {
        DialogEntry { topmost ->
            CompositionLocalProvider(locals) {
                DialogLayer(currentDismiss, currentProperties, topmost, currentContent)
            }
        }
    }
    DisposableEffect(entries, entry) {
        entries.add(entry)
        onDispose { entries.remove(entry) }
    }
}

@Composable
private fun DialogLayer(
    onDismissRequest: () -> Unit,
    properties: AppDialogProperties,
    topmost: Boolean,
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(topmost) {
        if (topmost) {
            focusManager.clearFocus(force = true)
            focus.requestFocus()
        }
    }
    AppBackHandler(enabled = topmost) { if (properties.dismissOnBackPress) onDismissRequest() }
    CompositionLocalProvider(
        LocalAppBackRequest provides { if (properties.dismissOnBackPress) onDismissRequest() }
    ) {
        Box(
            Modifier.fillMaxSize()
                .then(
                    if (topmost) Modifier.semantics { dialog() }
                    else Modifier.clearAndSetSemantics {}
                )
                .focusProperties {
                    onEnter = { if (!topmost) cancelFocusChange() }
                    onExit = { if (topmost) cancelFocusChange() }
                }
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            // Always intercept the backdrop, including non-dismissible dialogs and wheel input.
            Box(
                Modifier.matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (topmost && properties.dismissOnClickOutside) onDismissRequest()
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.type == PointerEventType.Scroll)
                                    event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
            Box(Modifier.size(0.dp).focusRequester(focus).focusable())
            Box(
                Modifier.safeDrawingPadding()
                    .imePadding()
                    .padding(20.dp)
                    .then(
                        if (properties.usePlatformDefaultWidth)
                            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Consume empty content space so clicks there never reach the backdrop.
                Box(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                ) {
                    content()
                }
            }
        }
    }
}

/** Project content slots hosted by our in-window modal, without AlertDialog's OS window. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    properties: AppDialogProperties = AppDialogProperties(),
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    containerColor: Color = CHelperTheme.colors.backgroundComponentNoTranslate,
) {
    AppDialog(onDismissRequest, properties) {
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(containerColor)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title?.invoke()
                if (text != null) Box(Modifier.weight(1f, fill = false)) { text() }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun DialogContainer(
    modifier: Modifier = Modifier,
    cornerSize: Dp = 10.dp,
    backgroundNoTranslate: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerSize))
            .background(
                if (backgroundNoTranslate) CHelperTheme.colors.backgroundComponentNoTranslate
                else CHelperTheme.colors.background
            )
    ) {
        content()
    }
}
