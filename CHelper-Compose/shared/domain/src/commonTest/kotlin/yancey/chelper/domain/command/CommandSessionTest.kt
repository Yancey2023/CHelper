package yancey.chelper.domain.command

import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class CommandSessionTest {
    private open class FakeParsed(private val text: String) : ParsedCommand {
        var released = false
        override val structure = text
        override val errors: Array<ErrorReason>? = null
        override val tokens: IntArray? = null
        override val nodeCount = 1

        override fun hint(index: Int) = "$text:$index"

        override fun suggestionCount(index: Int) = 2

        override fun suggestion(index: Int, which: Int): Suggestion? {
            check(!released)
            return null
        }

        override fun apply(index: Int, which: Int) =
            ClickSuggestionResult().apply {
                this.text = text
                selection = index
            }

        override fun close() {
            check(!released)
            released = true
        }
    }

    private fun request(revision: Long, text: String, index: Int = 0) =
        CommandAnalysisRequest(revision, "test", text, index, true, true)

    @Test
    fun `document highlighting never queries editor metadata or builds completion lists`() =
        runTest {
            val session =
                CommandSession(
                    load = {
                        object : CommandEngine {
                            override suspend fun parse(text: String) =
                                object : ParsedCommand {
                                    override val tokens = intArrayOf(7, 7, 7)
                                    override val structure: String?
                                        get() = error("structure is unnecessary for coloring")

                                    override val errors: Array<ErrorReason>?
                                        get() = error("errors are unnecessary for coloring")

                                    override val nodeCount: Int
                                        get() = error("node count is unnecessary for coloring")

                                    override fun hint(index: Int): String? =
                                        error("hint is unnecessary for coloring")

                                    override fun suggestionCount(index: Int): Int =
                                        error("must not build a completion list")

                                    override fun suggestion(index: Int, which: Int): Suggestion? =
                                        error("must not read completion values")

                                    override fun apply(
                                        index: Int,
                                        which: Int,
                                    ): ClickSuggestionResult? = null

                                    override fun close() = Unit
                                }

                            override fun close() = Unit
                        }
                    },
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            try {
                assertContentEquals(intArrayOf(7, 7, 7), session.highlight("test", "say"))
            } finally {
                session.close()
            }
        }

    @Test
    fun `cursor changes reuse the AST and stale suggestion clicks are rejected`() = runTest {
        var parses = 0
        val closed = CompletableDeferred<Unit>()
        val session =
            CommandSession(
                load = {
                    object : CommandEngine {
                        override suspend fun parse(text: String): ParsedCommand {
                            parses++
                            return FakeParsed(text)
                        }

                        override fun close() {
                            closed.complete(Unit)
                        }
                    }
                },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        try {
            session.analyze(request(1, "say hi"))
            val second = session.analyze(request(2, "say hi", 3))
            assertEquals(1, parses)
            assertEquals("say hi:3", second.hint)
            assertNull(session.apply(1, 0))
            assertEquals(3, session.apply(2, 0)?.selection)
        } finally {
            session.close()
        }
        withTimeout(5000) { closed.await() }
        assertNull(session.apply(2, 0))
    }

    @Test
    fun `cancelled native parsing releases its result before a replacement is used`() = runTest {
        val started = CompletableDeferred<Unit>()
        val releaseParse = CompletableDeferred<Unit>()
        val engineClosed = CompletableDeferred<Unit>()
        val old = FakeParsed("old")
        val session =
            CommandSession(
                load = {
                    object : CommandEngine {
                        override suspend fun parse(text: String): ParsedCommand {
                            if (text == "old") {
                                started.complete(Unit)
                                withContext(NonCancellable) {
                                    withTimeout(5000) { releaseParse.await() }
                                }
                                return old
                            }
                            return FakeParsed(text)
                        }

                        override fun close() {
                            engineClosed.complete(Unit)
                        }
                    }
                },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        try {
            val obsolete =
                async(start = CoroutineStart.UNDISPATCHED) { session.analyze(request(1, "old")) }
            withTimeout(5000) { started.await() }
            obsolete.cancel()
            val current = async { session.analyze(request(2, "new")) }
            releaseParse.complete(Unit)
            obsolete.cancelAndJoin()
            assertEquals("new", withTimeout(5000) { current.await() }.structure)
            assertTrue(old.released)
            assertNull(session.apply(1, 0))
        } finally {
            releaseParse.complete(Unit)
            session.close()
        }
        withTimeout(5000) { engineClosed.await() }
    }
}
