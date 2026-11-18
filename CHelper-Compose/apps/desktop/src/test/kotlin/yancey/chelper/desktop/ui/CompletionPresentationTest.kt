package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import yancey.chelper.domain.command.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.command.CommandCompletionController
import yancey.chelper.ui.feature.command.CompletionContent
import yancey.chelper.ui.feature.command.ComposeCommandEditor

class CompletionPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun typingKeepsStructureDescriptionAndVisibleSuggestionsUntilTheReplacementIsReady() {
        val ready = CompletableDeferred<Unit>()
        val command = TextFieldState("old")
        val engine =
            object : CommandEngine {
                override suspend fun parse(text: String): ParsedCommand {
                    if (text == "new") ready.await()
                    return object : ParsedCommand {
                        override val structure = "结构-$text"
                        override val errors: Array<ErrorReason>? = null
                        override val tokens: IntArray? = null
                        override val nodeCount = 1

                        override fun hint(index: Int) = "介绍-$text"

                        override fun suggestionCount(index: Int) = 3

                        override fun suggestion(index: Int, which: Int) =
                            Suggestion("建议-$text-$which", "说明-$text-$which")

                        override fun apply(index: Int, which: Int): ClickSuggestionResult? = null

                        override fun close() = Unit
                    }
                }

                override fun close() = Unit
            }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val session = remember { CommandSession({ engine }) }
            val controller = remember {
                CommandCompletionController(
                    session,
                    scope,
                    { command.text.toString() to command.text.length },
                )
            }
            DisposableEffect(session) { onDispose { session.close() } }
            LaunchedEffect(controller) {
                snapshotFlow { command.text.toString() }
                    .collect { text ->
                        controller.analyze(
                            CommandAnalysisRequest(0, "test", text, text.length, true, true)
                        )
                    }
            }
            val snapshot = controller.snapshot.analysis
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                Box(Modifier.requiredSize(420.dp, 640.dp)) {
                    CompletionContent(
                        state =
                            CommandResult(
                                structure = snapshot.structure.orEmpty(),
                                hint = snapshot.hint.orEmpty(),
                            ),
                        suggestionCount = snapshot.suggestionCount,
                        suggestionAt = { controller.suggestionAt(it) },
                        resultsEnabled = controller.resultsEnabled,
                        onVisibleSuggestions = controller::showSuggestions,
                        showMenu = false,
                        editorMode = false,
                        onErrorClick = {},
                        onSuggestion = {},
                        onAction = {},
                    ) { modifier ->
                        ComposeCommandEditor(
                            command,
                            false,
                            null,
                            emptyList(),
                            modifier.testTag("editor"),
                            null,
                        )
                    }
                }
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("建议-old-0").fetchSemanticsNodes().isNotEmpty()
        }
        val before = compose.onNodeWithText("建议-old-0").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("editor").performTextReplacement("new")
        repeat(5) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("结构-old").assertIsDisplayed()
            compose.onNodeWithText("介绍-old").assertIsDisplayed()
            compose.onNodeWithText("建议-old-0").assertIsDisplayed()
            compose.onNodeWithText("说明-old-0").assertIsDisplayed()
            compose.onNodeWithText("建议-old-0").assertIsNotEnabled()
            assertEquals(before, compose.onNodeWithText("建议-old-0").getUnclippedBoundsInRoot())
        }
        compose.runOnIdle { ready.complete(Unit) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("结构-new").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("介绍-new").assertIsDisplayed()
        compose.onNodeWithText("建议-new-0").assertIsDisplayed()
        compose.onNodeWithText("说明-new-0").assertIsDisplayed()
        compose.onNodeWithText("建议-old-0").assertDoesNotExist()
    }
}
