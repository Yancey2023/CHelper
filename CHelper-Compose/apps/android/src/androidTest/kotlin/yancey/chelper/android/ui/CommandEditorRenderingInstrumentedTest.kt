package yancey.chelper.android.ui

import android.os.Bundle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.text.highlightCommand
import yancey.chelper.ui.feature.command.ComposeCommandEditor

/** A paired software-drawing probe, separate from the release Macrobenchmark frame measurements. */
@RunWith(AndroidJUnit4::class)
class CommandEditorRenderingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compareProductionWindowWithPreviousWindow() {
        val text =
            "titleraw @a actionbar {\"rawtext\":[" +
                (1..200).joinToString(",") { "{\"text\":\"benchmark_$it\"}" } +
                "]}"
        val tokens = IntArray(text.length) { it / 3 % 8 }
        val command = TextFieldState(text, TextRange.Zero)
        lateinit var measurer: TextMeasurer
        compose.setContent {
            measurer = rememberTextMeasurer(cacheSize = 0)
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ComposeCommandEditor(
                    command,
                    false,
                    tokens,
                    emptyList(),
                    Modifier.size(240.dp, 72.dp).testTag("editor"),
                    null,
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("editor").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        val styled = layouts.single().layoutInput.text
        val reference = highlightCommand(text, tokens, false, 0, 768)
        assertEquals(text, styled.text)
        assertEquals(reference.subSequence(0, 32), styled.subSequence(0, 32))
        assertTrue(
            "The actual editor must keep off-screen color work small",
            styled.spanStyles.size < reference.spanStyles.size / 2,
        )
        compose.runOnIdle {
            val scopedText =
                AnnotatedString.Builder(styled)
                    .apply {
                        addStyle(
                            SpanStyle(fontFeatureSettings = ""),
                            styled.spanStyles.minOf { it.start },
                            styled.spanStyles.maxOf { it.end },
                        )
                    }
                    .toAnnotatedString()
            val baseline =
                measurer.measure(reference, TextStyle(fontSize = 16.sp), softWrap = false)
            val current = measurer.measure(styled, TextStyle(fontSize = 16.sp), softWrap = false)
            val scoped = measurer.measure(scopedText, TextStyle(fontSize = 16.sp), softWrap = false)
            val perRunText =
                AnnotatedString.Builder(styled)
                    .apply {
                        styled.spanStyles.forEach {
                            addStyle(SpanStyle(fontFeatureSettings = ""), it.start, it.end)
                        }
                    }
                    .toAnnotatedString()
            val chunkedText =
                AnnotatedString.Builder(styled)
                    .apply {
                        for (start in text.indices step 128) addStyle(
                            SpanStyle(fontFeatureSettings = ""),
                            start,
                            minOf(text.length, start + 128),
                        )
                    }
                    .toAnnotatedString()
            val perRun = measurer.measure(perRunText, TextStyle(fontSize = 16.sp), softWrap = false)
            val chunked =
                measurer.measure(chunkedText, TextStyle(fontSize = 16.sp), softWrap = false)
            val plain = measurer.measure(text, TextStyle(fontSize = 16.sp), softWrap = false)
            val stops = mutableListOf<Pair<Float, Color>>()
            val width = plain.size.width.toFloat()
            stops += 0f to Color.Black
            styled.spanStyles.forEach {
                val left = plain.getHorizontalPosition(it.start, true) / width
                val right = plain.getHorizontalPosition(it.end, true) / width
                stops += left to it.item.color
                stops += right to it.item.color
            }
            stops += stops.last().first to Color.Black
            stops += 1f to Color.Black
            val brush = Brush.horizontalGradient(*stops.toTypedArray(), endX = width)
            val expectedBitmap = ImageBitmap(720, 100)
            val brushBitmap = ImageBitmap(720, 100)
            current.multiParagraph.paint(Canvas(expectedBitmap), Color.Black)
            plain.multiParagraph.paint(Canvas(brushBitmap), brush, 1f)
            val expectedPixels = expectedBitmap.toPixelMap()
            val brushPixels = brushBitmap.toPixelMap()
            var differentPixels = 0
            for (y in 0 until 100) for (x in 0 until 720) {
                if (expectedPixels[x, y] != brushPixels[x, y]) differentPixels++
            }
            val canvas = Canvas(ImageBitmap(720, 100))
            repeat(8) {
                baseline.multiParagraph.paint(canvas, Color.Black)
                current.multiParagraph.paint(canvas, Color.Black)
                scoped.multiParagraph.paint(canvas, Color.Black)
                perRun.multiParagraph.paint(canvas, Color.Black)
                chunked.multiParagraph.paint(canvas, Color.Black)
                plain.multiParagraph.paint(canvas, Color.Black)
                plain.multiParagraph.paint(canvas, brush, 1f)
            }
            val before = mutableListOf<Double>()
            val after = mutableListOf<Double>()
            val context = mutableListOf<Double>()
            val perRunSamples = mutableListOf<Double>()
            val chunkSamples = mutableListOf<Double>()
            val plainSamples = mutableListOf<Double>()
            val brushSamples = mutableListOf<Double>()
            // Alternate ordering to reduce warm-up and thermal bias. Do not make CI depend
            // on a device-specific millisecond threshold; retain both median and tail samples.
            repeat(31) { iteration ->
                if (iteration % 2 == 0) {
                    before += drawMillis(baseline, canvas)
                    after += drawMillis(current, canvas)
                } else {
                    after += drawMillis(current, canvas)
                    before += drawMillis(baseline, canvas)
                }
                context += drawMillis(scoped, canvas)
                perRunSamples += drawMillis(perRun, canvas)
                chunkSamples += drawMillis(chunked, canvas)
                plainSamples += drawMillis(plain, canvas)
                val brushStarted = System.nanoTime()
                plain.multiParagraph.paint(canvas, brush, 1f)
                brushSamples += (System.nanoTime() - brushStarted) / 1_000_000.0
            }
            before.sort()
            after.sort()
            context.sort()
            perRunSamples.sort()
            chunkSamples.sort()
            plainSamples.sort()
            brushSamples.sort()
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    0,
                    Bundle().apply {
                        putString(
                            "stream",
                            "length=${text.length} baselineSpans=${reference.spanStyles.size} currentSpans=${styled.spanStyles.size} baselineMedianMs=${before[15]} baselineP95Ms=${before[29]} currentMedianMs=${after[15]} currentP95Ms=${after[29]} contextMedianMs=${context[15]} contextP95Ms=${context[29]} perRunMedianMs=${perRunSamples[15]} chunkMedianMs=${chunkSamples[15]} plainMedianMs=${plainSamples[15]} brushMedianMs=${brushSamples[15]} brushDifferentPixels=$differentPixels speedup=${before[15] / after[15]}\n",
                        )
                    },
                )
        }
    }

    private fun drawMillis(layout: TextLayoutResult, canvas: Canvas): Double {
        val started = System.nanoTime()
        layout.multiParagraph.paint(canvas, Color.Black)
        return (System.nanoTime() - started) / 1_000_000.0
    }
}
