@file:OptIn(ExperimentalCoroutinesApi::class)

package yancey.chelper.data.remote.interceptor

import io.ktor.http.Url
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class RateLimitInterceptorTest {
    @Test
    fun unrelatedHostsAreNotLimited() = runTest {
        val limiter = RateLimitInterceptor(2) { testScheduler.currentTime }
        repeat(3) { limiter.await(Url("https://example.com/")) }
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun disabledLimitDoesNotWait() = runTest {
        val limiter = RateLimitInterceptor(0) { testScheduler.currentTime }
        repeat(3) { limiter.await(Url("https://abyssous.site/")) }
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun successiveRequestsWaitForTheMinimumInterval() = runTest {
        val limiter = RateLimitInterceptor(2) { testScheduler.currentTime }
        repeat(3) { limiter.await(Url("https://abyssous.site/")) }
        assertEquals(1000, testScheduler.currentTime)
    }

    @Test
    fun expiredCooldownDoesNotWaitAgain() = runTest {
        val limiter = RateLimitInterceptor(2) { testScheduler.currentTime }
        limiter.await(Url("https://abyssous.site/"))
        advanceTimeBy(600)
        limiter.await(Url("https://abyssous.site/"))
        assertEquals(600, testScheduler.currentTime)
    }

    @Test
    fun cancellingAWaitingRequestDoesNotConsumeASlot() = runTest {
        val limiter = RateLimitInterceptor(2) { testScheduler.currentTime }
        limiter.await(Url("https://abyssous.site/"))
        val cancelled = launch { limiter.await(Url("https://abyssous.site/")) }
        runCurrent()
        cancelled.cancelAndJoin()
        limiter.await(Url("https://abyssous.site/"))
        assertEquals(500, testScheduler.currentTime)
    }
}
