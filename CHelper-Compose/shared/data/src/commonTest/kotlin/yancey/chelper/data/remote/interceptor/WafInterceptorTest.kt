package yancey.chelper.data.remote.interceptor

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlin.coroutines.resume
import kotlin.test.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import okio.IOException
import yancey.chelper.data.remote.createNetworkClient
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.domain.auth.BrowserVerification
import yancey.chelper.domain.auth.BrowserVerifier

class WafInterceptorTest {
    private class BrowserFixture {
        var state = BrowserVerificationState(BrowserVerifier { null })

        fun install(
            cookie: () -> String?,
            refresh: (((Boolean) -> Unit)?) -> Unit,
            userAgent: () -> String? = { "Test-UA" },
        ) {
            state =
                BrowserVerificationState(
                    BrowserVerifier {
                        suspendCancellableCoroutine { continuation ->
                            refresh { ok ->
                                if (continuation.isActive)
                                    continuation.resume(
                                        if (ok)
                                            cookie()?.takeIf(String::isNotBlank)?.let {
                                                BrowserVerification(it, userAgent() ?: "Test-UA")
                                            }
                                        else null
                                    )
                            }
                        }
                    },
                    cookie()?.let { BrowserVerification(it, userAgent() ?: "Test-UA") },
                )
        }
    }

    private val browser = BrowserFixture()

    private fun client(engine: HttpClientEngine) =
        createNetworkClient(engine, browser = browser.state)

    @Test
    fun verificationCookiesStayOnTheLibraryOrigin() = runTest {
        browser.install({ "browser=secret" }, { it?.invoke(false) }, { "Browser-UA" })
        val client =
            client(
                MockEngine { request ->
                    val library =
                        request.url.protocol.name == "https" &&
                            request.url.host == "abyssous.site" &&
                            request.url.port == 443
                    assertEquals(if (library) "browser=secret" else null, request.headers["Cookie"])
                    if (library) assertEquals("Browser-UA", request.headers["User-Agent"])
                    respond("{}", headers = headersOf("Content-Type", "application/json"))
                }
            )
        client.use {
            for (url in
                listOf(
                    "https://abyssous.site/captcha",
                    "http://abyssous.site/captcha",
                    "https://abyssous.site:444/captcha",
                    "https://example.com/",
                )) assertEquals("{}", it.get(url).bodyAsText())
        }
    }

    @Test
    fun refreshWithoutCookieFailsInsteadOfReturningAConsumedResponse() = runTest {
        browser.install({ null }, { it?.invoke(true) })
        browser.install({ null }, { it?.invoke(true) })
        client(
                MockEngine {
                    respond(
                        "<html>EO_Bot_Ssid</html>",
                        headers = headersOf("Content-Type", "text/html"),
                    )
                }
            )
            .use { client ->
                assertFailsWith<IOException> { client.get("https://abyssous.site/captcha") }
            }
    }

    @Test
    fun challengeReplaysOnlyOnceWithMatchingCookieAndAgent() = runTest {
        var cookie: String? = null
        var refreshes = 0
        browser.install(
            { cookie },
            {
                refreshes++
                cookie = "verified=yes"
                it?.invoke(true)
            },
            { "Verified-UA" },
        )
        var sends = 0
        client(
                MockEngine { request ->
                    sends++
                    if (sends == 1)
                        respond(
                            "<html>__tst_status=1</html>",
                            HttpStatusCode.Accepted,
                            headersOf("Content-Type", "text/html"),
                        )
                    else {
                        assertEquals("verified=yes", request.headers["Cookie"])
                        assertEquals("Verified-UA", request.headers["User-Agent"])
                        respond("{}", headers = headersOf("Content-Type", "application/json"))
                    }
                }
            )
            .use { assertEquals("{}", it.get("https://abyssous.site/captcha").bodyAsText()) }
        assertEquals(1, refreshes)
        assertEquals(2, sends)
    }

    @Test
    fun ordinaryShortHtmlRemainsReadable() = runTest {
        browser.install({ null }, { error("Unexpected browser verification") })
        client(
                MockEngine {
                    respond("<html>normal</html>", headers = headersOf("Content-Type", "text/html"))
                }
            )
            .use {
                assertEquals(
                    "<html>normal</html>",
                    it.get("https://abyssous.site/captcha").bodyAsText(),
                )
            }
    }

    @Test
    fun redirectCannotForwardBrowserCredentialsToAnotherOrigin() = runTest {
        browser.install({ "browser=secret" }, { it?.invoke(false) }, { "Browser-UA" })
        var sends = 0
        client(
                MockEngine { request ->
                    sends++
                    if (request.url.host == "abyssous.site") {
                        assertEquals("browser=secret", request.headers["Cookie"])
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf("Location", "https://other.example/"),
                        )
                    } else {
                        assertNull(request.headers["Cookie"])
                        assertNotEquals("Browser-UA", request.headers["User-Agent"])
                        respond("done")
                    }
                }
            )
            .use { assertEquals("done", it.get("https://abyssous.site/captcha").bodyAsText()) }
        assertEquals(2, sends)
    }
}
