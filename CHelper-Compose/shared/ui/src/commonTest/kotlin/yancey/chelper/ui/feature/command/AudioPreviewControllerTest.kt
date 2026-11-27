package yancey.chelper.ui.feature.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import yancey.chelper.domain.command.SuggestionAudioSession

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPreviewControllerTest {
    private val first = AudioPreviewRequest(0, "first")
    private val second = AudioPreviewRequest(1, "second")

    @Test
    fun pausesSeeksResumesAndReplaysTheSameSession() = runTest {
        val session = TestSession()
        var opened = 0
        val controller =
            AudioPreviewController(
                backgroundScope,
                {
                    opened++
                    session
                },
            ) {
                error("Unexpected failure")
            }

        controller.toggle(first)
        assertEquals(AudioPreviewState.Status.Loading, controller.state?.status)
        runCurrent()
        assertTrue(session.playing)
        assertEquals(12_600L, controller.state?.durationMillis)

        controller.toggle(first)
        assertFalse(session.playing)
        controller.seek(second, 1_000L)
        assertEquals(0L, session.positionMillis)
        controller.seek(first, 4_300L)
        assertEquals(4_300L, controller.state?.positionMillis)

        controller.toggle(first)
        assertEquals(4_300L, session.positionMillis)
        assertTrue(session.playing)
        session.positionMillis = session.durationMillis
        session.finished = true
        advanceTimeBy(101L)
        assertEquals(AudioPreviewState.Status.Paused, controller.state?.status)
        assertEquals(session.durationMillis, controller.state?.positionMillis)

        controller.toggle(first)
        assertEquals(0L, session.positionMillis)
        assertTrue(session.playing)
        assertEquals(1, opened)
        controller.clear()
        runCurrent()
        assertTrue(session.closed)
        assertNull(controller.state)
    }

    @Test
    fun switchingClosesThePreviousSessionAndCancelsPendingLoads() = runTest {
        var cancelled = false
        val session = TestSession()
        val controller =
            AudioPreviewController(
                backgroundScope,
                { url ->
                    if (url == first.url) {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                    session
                },
            ) {
                error("Unexpected failure")
            }

        controller.toggle(first)
        runCurrent()
        controller.toggle(second)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(second, controller.state?.request)
        assertTrue(session.playing)

        controller.toggle(first)
        runCurrent()
        assertTrue(session.closed)
        assertEquals(first, controller.state?.request)
        assertEquals(AudioPreviewState.Status.Loading, controller.state?.status)
    }

    @Test
    fun failedLoadResetsTheRowAndReportsOnce() = runTest {
        var errors = 0
        val controller = AudioPreviewController(backgroundScope, { error("offline") }) { errors++ }
        controller.toggle(first)
        runCurrent()
        assertNull(controller.state)
        assertEquals(1, errors)
    }

    @Test
    fun formatsOnlyWholeSeconds() {
        assertEquals("0:00", formatAudioTime(999L))
        assertEquals("1:05", formatAudioTime(65_999L))
    }

    private class TestSession : SuggestionAudioSession {
        override val durationMillis = 12_600L
        override var positionMillis = 0L
        override var finished = false
        var playing = false
        var closed = false

        override fun play() {
            playing = true
        }

        override fun pause() {
            playing = false
        }

        override fun seekTo(positionMillis: Long) {
            this.positionMillis = positionMillis
            finished = false
        }

        override fun close() {
            closed = true
        }
    }
}
