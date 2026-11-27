package yancey.chelper.ui.feature.command

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import yancey.chelper.resources.Res
import yancey.chelper.resources.pause
import yancey.chelper.resources.play
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text

@Composable
internal fun SuggestionAudioControls(
    state: AudioPreviewState?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state != null) {
            AudioTimeline(state, enabled, onSeek, Modifier.weight(1f))
        }
        val canPause = state?.canPause == true
        val label = if (canPause) "暂停试听" else "播放试听"
        Box(
            modifier =
                Modifier.size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                    .semantics {
                        contentDescription = label
                        if (state?.status == AudioPreviewState.Status.Loading)
                            stateDescription = "正在加载"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(38.dp)
                    .background(CHelperTheme.colors.backgroundComponent, CircleShape)
                    .border(1.dp, CHelperTheme.colors.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (canPause) Res.drawable.pause else Res.drawable.play, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AudioTimeline(
    state: AudioPreviewState,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val duration = state.durationMillis
    val seekEnabled = enabled && duration > 0L
    var dragPosition by
        remember(state.request, duration, seekEnabled) { mutableStateOf<Long?>(null) }
    val position = dragPosition ?: state.positionMillis
    val elapsed = formatAudioTime(position)
    val total = if (duration > 0L) formatAudioTime(duration) else "–:––"
    val seek by rememberUpdatedState(onSeek)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = CHelperTheme.colors
    val timeStyle = TextStyle(fontSize = 11.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum")

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth()
                .height(24.dp)
                .alpha(if (seekEnabled) 1f else 0.5f)
                .semantics {
                    contentDescription = "播放进度"
                    stateDescription = "$elapsed / $total"
                    if (seekEnabled) {
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(position.toFloat(), 0f..duration.toFloat())
                        setProgress {
                            seek(it.roundToLong())
                            true
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (!seekEnabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val target =
                        when (event.key) {
                            Key.DirectionLeft,
                            Key.DirectionDown -> position - 1_000L
                            Key.DirectionRight,
                            Key.DirectionUp -> position + 1_000L
                            Key.MoveHome -> 0L
                            Key.MoveEnd -> duration
                            else -> return@onKeyEvent false
                        }
                    seek(target.coerceIn(0L, duration))
                    true
                }
                .focusable(enabled = seekEnabled, interactionSource = interactions)
                .pointerInput(state.request, duration, seekEnabled) {
                    if (seekEnabled) {
                        detectTapGestures { tap ->
                            seek(
                                timelinePosition(tap.x, size.width.toFloat(), 6.dp.toPx(), duration)
                            )
                        }
                    }
                }
                .pointerInput(state.request, duration, seekEnabled) {
                    if (seekEnabled) {
                        detectHorizontalDragGestures(
                            onDragStart = { start ->
                                dragPosition =
                                    timelinePosition(
                                        start.x,
                                        size.width.toFloat(),
                                        6.dp.toPx(),
                                        duration,
                                    )
                            },
                            onDragEnd = {
                                dragPosition?.let(seek)
                                dragPosition = null
                            },
                            onDragCancel = { dragPosition = null },
                        ) { change, _ ->
                            change.consume()
                            dragPosition =
                                timelinePosition(
                                    change.position.x,
                                    size.width.toFloat(),
                                    6.dp.toPx(),
                                    duration,
                                )
                        }
                    }
                }
        ) {
            val radius = 6.dp.toPx().coerceAtMost(size.width / 2)
            val trackWidth = (size.width - 2 * radius).coerceAtLeast(0f)
            val trackHeight = 4.dp.toPx()
            val start = Offset(radius, center.y - trackHeight / 2)
            val fraction = if (duration > 0L) position.toFloat() / duration else 0f
            val progress = trackWidth * fraction.coerceIn(0f, 1f)
            val corners = CornerRadius(trackHeight / 2)
            drawRoundRect(colors.line, start, Size(trackWidth, trackHeight), corners)
            drawRoundRect(colors.iconMain, start, Size(progress, trackHeight), corners)
            val thumb = Offset(radius + progress, center.y)
            if (focused || dragPosition != null) {
                drawCircle(colors.iconMain.copy(alpha = 0.25f), radius + 3.dp.toPx(), thumb)
            }
            drawCircle(Color.White, radius, thumb)
            drawCircle(colors.iconMain, radius, thumb, style = Stroke(1.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(elapsed, style = timeStyle)
            Text(total, style = timeStyle.copy(color = colors.textSecondary))
        }
    }
}

private fun timelinePosition(x: Float, width: Float, radius: Float, duration: Long): Long =
    (((x - radius) / (width - 2 * radius).coerceAtLeast(1f)).coerceIn(0f, 1f) * duration)
        .roundToLong()

internal fun formatAudioTime(positionMillis: Long): String {
    val seconds = positionMillis / 1_000L
    return "${seconds / 60L}:${(seconds % 60L).toString().padStart(2, '0')}"
}
