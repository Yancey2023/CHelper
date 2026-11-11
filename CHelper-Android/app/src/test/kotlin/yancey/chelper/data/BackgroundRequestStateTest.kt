package yancey.chelper.data

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class BackgroundRequestStateTest {
    @Test
    fun `restore supersedes both a pending image and a startup read`() {
        val state = BackgroundRequestState<String?>("original")
        val load = state.beginLoad()!!
        val image = state.begin()
        val restore = state.begin()
        assertTrue(state.publish(restore, null))
        assertFalse(state.publish(image, "selected"))
        assertFalse(state.publish(load, "from disk"))
        assertNull(state.value)
        assertNull(state.beginLoad())
    }

    @Test
    fun `starting a request retains the last displayed image`() {
        val state = BackgroundRequestState("original")
        state.begin()
        assertEquals("original", state.value)
        assertNull(state.beginLoad())
    }

    @Test
    fun `obsolete publication racing a new request cannot win`() {
        repeat(100) {
            val state = BackgroundRequestState<String?>("original")
            val old = state.begin()
            val ready = CountDownLatch(1)
            val racing = thread {
                ready.await()
                state.publish(old, "old")
            }
            ready.countDown()
            val current = state.begin()
            state.publish(current, null)
            racing.join()
            assertNull(state.value)
        }
    }

    @Test
    fun `sample limits either orientation without upscaling`() {
        assertEquals(8, backgroundSampleSize(4096, 2048, 512))
        assertEquals(8, backgroundSampleSize(2048, 4096, 512))
        assertEquals(4, backgroundSampleSize(2049, 100, 1024))
        assertEquals(1, backgroundSampleSize(256, 512, 1024))
        assertEquals(Int.MAX_VALUE, backgroundSampleSize(Int.MAX_VALUE, 1, 1))
    }
}
