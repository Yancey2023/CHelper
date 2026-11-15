package yancey.chelper.ui.feature.command

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import yancey.chelper.domain.command.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommandCompletionControllerTest {
    private class Engine : CommandEngine {
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val reads = mutableListOf<Pair<String, Int>>()
        val applied = mutableListOf<String>()
        var failure = false

        override suspend fun parse(text: String): ParsedCommand {
            gates[text]?.let { gate ->
                if (text == "obsolete") withContext(NonCancellable) { gate.await() }
                else gate.await()
            }
            if (failure) error("parse failed")
            return object : ParsedCommand {
                override val structure = "structure-$text"
                override val errors = arrayOf(ErrorReason("error-$text", 0, text.length))
                override val tokens = IntArray(text.length) { 7 }
                override val nodeCount = text.length

                override fun hint(index: Int) = "hint-$text-$index"

                override fun suggestionCount(index: Int) = 100_000

                override fun suggestion(index: Int, which: Int): Suggestion {
                    reads += text to which
                    return Suggestion("$text-$which", "description-$text-$which")
                }

                override fun apply(index: Int, which: Int): ClickSuggestionResult {
                    applied += text
                    return ClickSuggestionResult("$text-$which", index)
                }

                override fun close() = Unit
            }
        }

        override fun close() = Unit
    }

    private fun request(text: String) =
        CommandAnalysisRequest(0, "test", text, text.length, true, true)

    @Test
    fun pendingAnalysisKeepsTheWholePreviousSnapshotAndRejectsOldClicks() = runTest {
        val engine = Engine()
        var input = "old"
        val session = CommandSession({ engine }, StandardTestDispatcher(testScheduler))
        val controller =
            CommandCompletionController(session, backgroundScope, { input to input.length })
        try {
            controller.analyze(request(input))
            runCurrent()
            val old = controller.snapshot
            assertEquals("structure-old", old.analysis.structure)
            assertEquals("old-0", controller.suggestionAt(0)?.name)
            val ready = CompletableDeferred<Unit>()
            engine.gates["new"] = ready
            input = "new"
            controller.analyze(request(input))
            runCurrent()
            assertTrue(controller.isAnalyzing)
            assertSame(old, controller.snapshot)
            assertFalse(controller.resultsEnabled)
            assertNull(controller.apply(0, old.revision))
            controller.showSuggestions((100..106).toList())
            ready.complete(Unit)
            runCurrent()
            assertFalse(controller.isAnalyzing)
            assertEquals("structure-new", controller.snapshot.analysis.structure)
            assertEquals("hint-new-3", controller.snapshot.analysis.hint)
            assertEquals("error-new", controller.snapshot.analysis.errors?.single()?.errorReason)
            assertEquals("new-0", controller.suggestionAt(0)?.name)
            assertEquals("new-106", controller.suggestionAt(106)?.name)
            assertEquals("description-new-106", controller.suggestionAt(106)?.description)
            assertEquals(100_000, controller.snapshot.analysis.suggestionCount)
            assertNull(controller.apply(0, old.revision))
            assertTrue(engine.applied.isEmpty())
            assertEquals("new-0", controller.apply(0, controller.snapshot.revision)?.text)
            assertEquals(listOf("new"), engine.applied)
        } finally {
            session.close()
            runCurrent()
        }
    }

    @Test
    fun largeSuggestionSetsReadAndRetainOnlyTheHeadAndViewport() = runTest {
        val engine = Engine()
        val session = CommandSession({ engine }, StandardTestDispatcher(testScheduler))
        val controller = CommandCompletionController(session, backgroundScope, { "test" to 4 })
        try {
            controller.analyze(request("test"))
            runCurrent()
            assertEquals(32, engine.reads.size)
            controller.showSuggestions((100..106).toList())
            runCurrent()
            assertEquals("test-106", controller.suggestionAt(106)?.name)
            controller.showSuggestions((10_000..10_006).toList())
            runCurrent()
            assertEquals("test-10006", controller.suggestionAt(10_006)?.name)
            assertNull(controller.suggestionAt(106))
            assertTrue(engine.reads.size < 100)
            assertTrue(controller.snapshot.analysis.suggestions.size < 64)
            assertEquals(100_000, controller.snapshot.analysis.suggestionCount)
        } finally {
            session.close()
            runCurrent()
        }
    }

    @Test
    fun obsoleteNativeResultsNeverReplaceTheDisplayedSnapshot() = runTest {
        val engine = Engine()
        var input = "old"
        val session = CommandSession({ engine }, StandardTestDispatcher(testScheduler))
        val controller =
            CommandCompletionController(session, backgroundScope, { input to input.length })
        try {
            controller.analyze(request(input))
            runCurrent()
            val old = controller.snapshot
            val release = CompletableDeferred<Unit>()
            engine.gates["obsolete"] = release
            input = "obsolete"
            controller.analyze(request(input))
            runCurrent()
            input = "latest"
            controller.analyze(request(input))
            runCurrent()
            assertSame(old, controller.snapshot)
            release.complete(Unit)
            runCurrent()
            assertEquals("structure-latest", controller.snapshot.analysis.structure)
            assertEquals("latest-0", controller.suggestionAt(0)?.name)
        } finally {
            session.close()
            runCurrent()
        }
    }

    @Test
    fun discardedInputCanBeAnalyzedAgainWhenTheUserReturnsToIt() = runTest {
        val engine = Engine()
        var input = "old"
        val session = CommandSession({ engine }, StandardTestDispatcher(testScheduler))
        val controller =
            CommandCompletionController(session, backgroundScope, { input to input.length })
        try {
            val release = CompletableDeferred<Unit>()
            engine.gates["old"] = release
            controller.analyze(request(input))
            runCurrent()
            input = "other"
            release.complete(Unit)
            runCurrent()
            assertEquals(-1, controller.snapshot.revision)
            input = "old"
            controller.analyze(request(input))
            runCurrent()
            assertEquals("structure-old", controller.snapshot.analysis.structure)
            assertTrue(controller.resultsEnabled)
        } finally {
            session.close()
            runCurrent()
        }
    }

    @Test
    fun failedAnalysisKeepsPreviousRowsAndCanRetryTheSameInput() = runTest {
        val engine = Engine()
        var input = "old"
        val session = CommandSession({ engine }, StandardTestDispatcher(testScheduler))
        val controller =
            CommandCompletionController(session, backgroundScope, { input to input.length })
        try {
            controller.analyze(request(input))
            runCurrent()
            val old = controller.snapshot
            input = "new"
            engine.failure = true
            controller.analyze(request(input))
            runCurrent()
            assertSame(old, controller.snapshot)
            assertNotNull(controller.failure)
            assertFalse(controller.resultsEnabled)
            engine.failure = false
            controller.analyze(request(input))
            runCurrent()
            assertNull(controller.failure)
            assertEquals("new-0", controller.suggestionAt(0)?.name)
            assertTrue(controller.resultsEnabled)
        } finally {
            session.close()
            runCurrent()
        }
    }
}
