package yancey.chelper.data.remote

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.domain.auth.BrowserVerification
import yancey.chelper.domain.auth.BrowserVerifier

class BrowserVerificationStateTest {
    @Test
    fun concurrentChallengesShareOneVerification() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val verified = BrowserVerification("browser=verified", "Browser-UA")
        val state =
            BrowserVerificationState(
                BrowserVerifier {
                    calls++
                    entered.complete(Unit)
                    release.await()
                    verified
                }
            )
        val first = async { state.refresh(null) }
        entered.await()
        val second = async { state.refresh(null) }
        release.complete(Unit)
        assertEquals(verified, first.await())
        assertEquals(verified, second.await())
        assertEquals(1, calls)
    }

    @Test
    fun cancellingVerificationCleansUpAndAllowsAnotherAttempt() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        var calls = 0
        val state =
            BrowserVerificationState(
                BrowserVerifier {
                    if (++calls == 1) {
                        try {
                            entered.complete(Unit)
                            awaitCancellation()
                        } finally {
                            cleaned = true
                        }
                    } else BrowserVerification("browser=verified", "Browser-UA")
                }
            )
        val cancelled = async { state.refresh(null) }
        entered.await()
        cancelled.cancelAndJoin()
        assertTrue(cleaned)
        assertNull(state.current)
        assertEquals("browser=verified", state.refresh(null)?.cookie)
    }
}
