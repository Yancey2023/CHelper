package yancey.chelper.ui.feature.command

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.core.ClickSuggestionResult
import yancey.chelper.core.ErrorReason
import yancey.chelper.core.Suggestion
import yancey.chelper.nativecore.command.CommandAnalysisRequest
import yancey.chelper.nativecore.command.CommandEngine
import yancey.chelper.nativecore.command.CommandSession
import yancey.chelper.nativecore.command.ParsedCommand

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
    fun `cursor changes reuse the AST and stale suggestion clicks are rejected`() = runBlocking {
        var parses = 0
        val closed = CompletableDeferred<Unit>()
        val session =
            CommandSession(
                load = {
                    object : CommandEngine {
                        override fun parse(text: String): ParsedCommand {
                            parses++
                            return FakeParsed(text)
                        }

                        override fun close() {
                            closed.complete(Unit)
                        }
                    }
                }
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
    fun `cancelled native parsing releases its result before a replacement is used`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val releaseParse = CountDownLatch(1)
            val engineClosed = CompletableDeferred<Unit>()
            val old = FakeParsed("old")
            val session =
                CommandSession(
                    load = {
                        object : CommandEngine {
                            override fun parse(text: String): ParsedCommand {
                                if (text == "old") {
                                    started.complete(Unit)
                                    check(releaseParse.await(5, TimeUnit.SECONDS))
                                    return old
                                }
                                return FakeParsed(text)
                            }

                            override fun close() {
                                engineClosed.complete(Unit)
                            }
                        }
                    }
                )
            try {
                val obsolete =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        session.analyze(request(1, "old"))
                    }
                withTimeout(5000) { started.await() }
                obsolete.cancel()
                val current = async { session.analyze(request(2, "new")) }
                releaseParse.countDown()
                obsolete.cancelAndJoin()
                assertEquals("new", withTimeout(5000) { current.await() }.structure)
                assertTrue(old.released)
                assertNull(session.apply(1, 0))
            } finally {
                releaseParse.countDown()
                session.close()
            }
            withTimeout(5000) { engineClosed.await() }
        }
}
