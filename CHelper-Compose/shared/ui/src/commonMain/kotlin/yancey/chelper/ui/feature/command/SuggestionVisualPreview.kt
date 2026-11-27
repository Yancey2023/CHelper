package yancey.chelper.ui.feature.command

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import yancey.chelper.domain.command.SuggestionPreview
import yancey.chelper.domain.command.isWikiSuggestionIconUrl
import yancey.chelper.ui.designsystem.CHelperTheme

@Composable
internal fun SuggestionVisualPreview(preview: SuggestionPreview?, modifier: Modifier = Modifier) {
    when (preview) {
        is SuggestionPreview.Icon -> {
            if (isWikiSuggestionIconUrl(preview.url)) {
                AsyncImage(
                    model = preview.url,
                    contentDescription = null,
                    modifier = modifier.size(28.dp),
                )
            }
        }
        is SuggestionPreview.Easing -> {
            val samples = remember(preview.id) { easingSamples(preview.id) } ?: return
            val line = CHelperTheme.colors.line
            val ink = CHelperTheme.colors.textSecondary
            Canvas(modifier.size(58.dp, 30.dp).semantics { contentDescription = "缓动曲线" }) {
                fun y(value: Double) = ((1.4 - value) * size.height / 1.8).toFloat()
                for (value in listOf(0.0, 1.0)) {
                    drawLine(line, Offset(0f, y(value)), Offset(size.width, y(value)))
                }
                val path =
                    Path().apply {
                        moveTo(0f, y(samples.first()))
                        for (i in 1..samples.lastIndex) {
                            lineTo(i * size.width / samples.lastIndex, y(samples[i]))
                        }
                    }
                drawPath(path, ink, style = Stroke(1.5.dp.toPx()))
            }
        }
        is SuggestionPreview.DyeColor -> {
            val color = dyeColors[preview.id] ?: return
            val shape = RoundedCornerShape(4.dp)
            Box(
                modifier
                    .size(20.dp)
                    .background(color, shape)
                    .border(1.dp, CHelperTheme.colors.line, shape)
                    .semantics { contentDescription = "染料色样" }
            )
        }
        else -> Unit
    }
}

private val dyeColors =
    mapOf(
        "white_dye" to Color(0xFFF9FFFE),
        "orange_dye" to Color(0xFFF9801D),
        "magenta_dye" to Color(0xFFC74EBD),
        "light_blue_dye" to Color(0xFF3AB3DA),
        "yellow_dye" to Color(0xFFFED83D),
        "lime_dye" to Color(0xFF80C71F),
        "pink_dye" to Color(0xFFF38BAA),
        "gray_dye" to Color(0xFF474F52),
        "light_gray_dye" to Color(0xFF9D9D97),
        "cyan_dye" to Color(0xFF169C9C),
        "purple_dye" to Color(0xFF8932B8),
        "blue_dye" to Color(0xFF3C44AA),
        "brown_dye" to Color(0xFF835432),
        "green_dye" to Color(0xFF5E7C16),
        "red_dye" to Color(0xFFB02E26),
        "black_dye" to Color(0xFF1D1D21),
    )

internal fun easingSamples(id: String): List<Double>? {
    val direction = listOf("in_out_", "out_", "in_").firstOrNull(id::startsWith)
    val family = direction?.let(id::removePrefix) ?: id
    val curve: (Double) -> Double =
        when (family) {
            "linear" -> if (direction == null) ({ t -> t }) else return null
            "spring" -> if (direction == null) ::spring else return null
            "quad" -> { t ->
                t * t
            }
            "cubic" -> { t ->
                t.pow(3)
            }
            "quart" -> { t ->
                t.pow(4)
            }
            "quint" -> { t ->
                t.pow(5)
            }
            "sine" -> { t ->
                1 - cos(t * PI / 2)
            }
            "expo" -> { t ->
                2.0.pow(10 * (t - 1))
            }
            "circ" -> { t ->
                1 - sqrt(1 - t * t)
            }
            "bounce" -> { t ->
                1 - bounceOut(1 - t)
            }
            "back" -> { t ->
                val overshoot = if (direction == "in_out_") 1.70158 * 1.525 else 1.70158
                t * t * ((overshoot + 1) * t - overshoot)
            }
            "elastic" -> { t ->
                val period = if (direction == "in_out_") 0.45 else 0.3
                -2.0.pow(10 * (t - 1)) * sin((t - 1 - period / 4) * 2 * PI / period)
            }
            else -> return null
        }
    if (direction == null && family != "linear" && family != "spring") return null
    return List(65) { index ->
        val t = index / 64.0
        when {
            index == 0 || index == 64 -> t
            direction == "out_" -> 1 - curve(1 - t)
            direction == "in_out_" -> if (t < 0.5) curve(2 * t) / 2 else 1 - curve(2 * (1 - t)) / 2
            else -> curve(t)
        }
    }
}

// 示意 spring 的阻尼振荡。公开命令文档没有给出游戏内部的弹簧参数。
private fun spring(t: Double): Double =
    (1 - exp(-6 * t) * cos(14 * t)) / (1 - exp(-6.0) * cos(14.0))

private fun bounceOut(t: Double): Double =
    when {
        t < 1 / 2.75 -> 7.5625 * t * t
        t < 2 / 2.75 -> 7.5625 * (t - 1.5 / 2.75).pow(2) + 0.75
        t < 2.5 / 2.75 -> 7.5625 * (t - 2.25 / 2.75).pow(2) + 0.9375
        else -> 7.5625 * (t - 2.625 / 2.75).pow(2) + 0.984375
    }
