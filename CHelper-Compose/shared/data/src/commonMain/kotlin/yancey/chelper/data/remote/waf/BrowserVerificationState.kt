package yancey.chelper.data.remote.waf

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import yancey.chelper.domain.auth.BrowserVerification
import yancey.chelper.domain.auth.BrowserVerifier

/** One browser verification state per application, shared by auth and API clients. */
class BrowserVerificationState(
    private val verifier: BrowserVerifier,
    initial: BrowserVerification? = null,
) {
    private val state = MutableStateFlow(initial)
    val current: BrowserVerification?
        get() = state.value

    private val mutex = Mutex()

    suspend fun refresh(previousCookie: String?): BrowserVerification? =
        mutex.withLock {
            current?.takeIf { it.cookie != previousCookie }
                ?: withTimeoutOrNull(30000) {
                    verifier
                        .verify()
                        ?.takeIf { it.cookie.isNotBlank() && it.userAgent.isNotBlank() }
                        ?.also { state.value = it }
                }
        }
}
