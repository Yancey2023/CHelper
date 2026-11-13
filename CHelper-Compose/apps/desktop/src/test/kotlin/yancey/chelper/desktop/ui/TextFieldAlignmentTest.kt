package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.TextField

class TextFieldAlignmentTest {
    @get:Rule val compose = createComposeRule()
    private val alignments =
        listOf(
            "top" to Alignment.TopStart,
            "center" to Alignment.CenterStart,
            "bottom" to Alignment.BottomStart,
        )

    @Composable
    private fun Fields(empty: Boolean) {
        CHelperTheme(CHelperTheme.Theme.Light, null) {
            Row {
                alignments.forEach { (tag, alignment) ->
                    TextField(
                        state = TextFieldState(if (empty) "" else "say hello"),
                        hint = "hint-$tag",
                        modifier = Modifier.width(200.dp).height(350.dp).testTag(tag),
                        contentAlignment = alignment,
                        style = TextStyle(color = Color.Red),
                    )
                }
            }
        }
    }

    @Test
    fun emptyEditorsPlaceHintsAtTheRequestedVerticalAlignment() {
        compose.setContent { Fields(empty = true) }
        alignments.forEach { (tag, _) ->
            val field = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val hint =
                compose
                    .onNodeWithText("hint-$tag", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertPosition(tag, (hint.top - field.top) / field.height)
        }
    }

    @Test
    fun shortCommandsAreDrawnAtTheRequestedVerticalAlignment() {
        compose.setContent { Fields(empty = false) }
        alignments.forEach { (tag, _) ->
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val firstTextRow =
                (0 until pixels.height).firstOrNull { y ->
                    (0 until pixels.width).any { x ->
                        val pixel = pixels[x, y]
                        pixel.red > .8f && pixel.green < .3f && pixel.blue < .3f
                    }
                }
            checkNotNull(firstTextRow) { "$tag editor must draw its command text" }
            assertPosition(tag, firstTextRow.toFloat() / pixels.height)
        }
    }

    @Test
    fun anEditorWithoutAFixedHeightStillWrapsItsContent() {
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                TextField(TextFieldState("name"), Modifier.width(200.dp).testTag("wrap"))
            }
        }
        val field = compose.onNodeWithTag("wrap").fetchSemanticsNode()
        val maximum = with(field.layoutInfo.density) { 80.dp.toPx() }
        assertTrue("Wrap-content editor must stay compact", field.boundsInRoot.height <= maximum)
    }

    private fun assertPosition(tag: String, fraction: Float) {
        val range =
            when (tag) {
                "top" -> 0f..0.2f
                "center" -> 0.35f..0.65f
                else -> 0.8f..1f
            }
        assertTrue("$tag text starts at $fraction of the editor height", fraction in range)
    }
}
