package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import yancey.chelper.domain.enumeration.EnumerationRepository
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.Header
import yancey.chelper.ui.designsystem.widget.SecureTextField
import yancey.chelper.ui.designsystem.widget.TextField
import yancey.chelper.ui.designsystem.widget.TextFieldWithIcon
import yancey.chelper.ui.feature.enumeration.DataVariable
import yancey.chelper.ui.feature.enumeration.EnumerationScreenTimes
import yancey.chelper.ui.feature.enumeration.EnumerationScreenVariable
import yancey.chelper.ui.feature.enumeration.EnumerationViewModel

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
        assertTrue(field.boundsInRoot.height <= maximum, "Wrap-content editor must stay compact")
    }

    @Test
    fun compactAndMultilineEditorsKeepTheirAssignedSizesWithAndWithoutIcons() {
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Column(Modifier.width(300.dp)) {
                    TextField(
                        TextFieldState(),
                        Modifier.height(40.dp).testTag("search"),
                        hint = "搜索",
                    )
                    TextField(TextFieldState("昵称"), Modifier.height(45.dp).testTag("profile"))
                    TextFieldWithIcon(
                        TextFieldState(),
                        Modifier.height(50.dp).testTag("account"),
                        hint = "账号",
                        leadingIcon = { Box(Modifier.size(20.dp)) },
                    )
                    SecureTextField(
                        TextFieldState("password"),
                        Modifier.height(50.dp).testTag("password"),
                        leadingIcon = { Box(Modifier.size(20.dp)) },
                        trailingIcon = { Box(Modifier.size(48.dp)) },
                    )
                    TextField(
                        TextFieldState("say hello"),
                        Modifier.height(100.dp).testTag("expression"),
                    )
                }
            }
        }
        listOf(
                "search" to 40,
                "profile" to 45,
                "account" to 50,
                "password" to 50,
                "expression" to 100,
            )
            .forEach { (tag, height) ->
                compose.onNodeWithTag(tag).assertHeightIsEqualTo(height.dp)
            }
    }

    @Test
    fun enumerationCellsRetainThirtyDpHeightAndPaintTheirWholeFrame() {
        val model =
            EnumerationViewModel(EnumerationRepository { _, _, _ -> "" }).apply {
                times = TextFieldState("20")
                variableList.add(
                    DataVariable().apply {
                        name = TextFieldState("name")
                        start = TextFieldState("1")
                        interval = TextFieldState("2")
                    }
                )
            }
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Column(Modifier.width(420.dp).height(300.dp)) {
                    EnumerationScreenTimes(model)
                    EnumerationScreenVariable(Modifier.weight(1f), model)
                }
            }
        }
        listOf("20", "name", "1", "2").forEach { text ->
            val field = compose.onNode(hasSetTextAction() and hasText(text))
            field.assertHeightIsEqualTo(30.dp)
            val pixels = field.captureToImage().toPixelMap()
            val x = pixels.width / 4
            val painted = pixels[x, pixels.height / 2]
            assertTrue(
                pixels[x, 1] == painted && pixels[x, pixels.height - 2] == painted,
                "$text must paint its background across the whole cell height",
            )
        }
    }

    @Test
    fun aPlaceholderUsesTheEditorsFontMetricsInsteadOfChangingItsHeight() {
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Column(Modifier.width(300.dp)) {
                    TextField(
                        TextFieldState(),
                        Modifier.testTag("empty"),
                        hint = "Hint",
                        verticalPadding = 0.dp,
                        style = TextStyle(fontSize = 12.sp),
                    )
                    TextField(
                        TextFieldState("Hint"),
                        Modifier.testTag("filled"),
                        verticalPadding = 0.dp,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }
            }
        }
        val empty = compose.onNodeWithTag("empty").fetchSemanticsNode().boundsInRoot
        val filled = compose.onNodeWithTag("filled").fetchSemanticsNode().boundsInRoot
        assertTrue(empty.height == filled.height, "A hint must not make the editor taller")
    }

    @Test
    fun theBackButtonRetainsItsOriginalThirtyFourDpLayoutWidth() {
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) { Header("Header", onBack = {}) }
        }
        compose.onNodeWithText("Header").assertLeftPositionInRootIsEqualTo(44.dp)
    }

    private fun assertPosition(tag: String, fraction: Float) {
        val range =
            when (tag) {
                "top" -> 0f..0.2f
                "center" -> 0.35f..0.65f
                else -> 0.8f..1f
            }
        assertTrue(fraction in range, "$tag text starts at $fraction of the editor height")
    }
}
