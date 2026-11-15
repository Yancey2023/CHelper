package yancey.chelper.data.remote

import io.ktor.client.engine.mock.*
import io.ktor.client.request.get
import io.ktor.http.headersOf
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.auth.CredentialStore

class NetworkServicesTest {
    private class MemoryCredentials(private var content: String?) : CredentialStore {
        override fun read() = content

        override fun write(content: String) {
            this.content = content
        }

        override fun clear() {
            content = null
        }
    }

    private val expired =
        """{"mail":"a@example.test","password":"password","token":"expired","lastLoginTimestamp":0,"user":{"id":17,"is_guest":false}}"""

    private fun loginResponse(token: String, id: Int) =
        """{"status":0,"data":{"token":"$token","user":{"id":$id,"is_guest":false}}}"""

    @Test
    fun instancesDoNotShareEndpointsAccountsOrCaches() = runTest {
        fun graph(host: String, id: Int) =
            NetworkServices(
                {
                    MockEngine { request ->
                        assertEquals(host, request.url.host)
                        respond(
                            loginResponse("token-$id", id),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                },
                NetworkConfig(labBaseUrl = "https://$host/"),
            )
        graph("one.test", 1).use { one ->
            graph("two.test", 2).use { two ->
                assertTrue(one.login.login("one", "password").isSuccess)
                assertEquals(1, one.login.accountId.value)
                assertNull(two.login.accountId.value)
                assertTrue(two.login.login("two", "password").isSuccess)
                assertEquals(1, one.login.accountId.value)
                assertEquals(2, two.login.accountId.value)
                one.cache.setQuota(17, 1, 20)
                assertNull(two.cache.getQuota(17))
                two.cache.setQuota(17, 2, 30)
                one.login.logout()
                assertEquals(2, two.login.accountId.value)
                assertNull(one.cache.getQuota(17))
                assertEquals(2, two.cache.getQuota(17)?.used)
            }
        }
    }

    @Test
    fun expiredSessionRefreshUsesTheUnauthenticatedClientWithoutRecursion() = runTest {
        val paths = mutableListOf<String>()
        NetworkServices(
                {
                    MockEngine { request ->
                        paths += request.url.encodedPath
                        assertEquals("no-store", request.headers["Cache-Control"])
                        val response =
                            if (request.url.encodedPath == "/register/login") {
                                assertNull(request.headers["Authorization"])
                                loginResponse("fresh", 17)
                            } else {
                                assertEquals("Bearer fresh", request.headers["Authorization"])
                                """{"status":0,"data":{"used":1,"limit":20}}"""
                            }
                        respond(response, headers = headersOf("Content-Type", "application/json"))
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(MemoryCredentials(expired)) { throw AssertionError(it) }
                assertEquals(1, network.user.getQuota().data?.used)
                assertEquals(listOf("/register/login", "/library/quota"), paths)
            }
    }

    @Test
    fun logoutDuringRefreshCannotRestoreThePreviousAccount() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        NetworkServices(
                {
                    MockEngine {
                        started.complete(Unit)
                        release.await()
                        respond(
                            loginResponse("obsolete", 17),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(MemoryCredentials(expired)) { throw AssertionError(it) }
                val pending = async { network.login.getToken() }
                started.await()
                network.login.logout()
                release.complete(Unit)
                assertNull(pending.await())
                assertNull(network.login.accountId.value)
                assertNull(network.login.currentToken)
            }
    }

    @Test
    fun closingCancelsOutstandingRequestsAndReleasesEveryOwnedEngine() = runTest {
        val engines = mutableListOf<MockEngine>()
        val started = CompletableDeferred<Unit>()
        val network =
            NetworkServices(
                {
                    MockEngine {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                        .also(engines::add)
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
        val pending = async { runCatching { network.user.getQuota() } }
        started.await()
        network.close()
        assertTrue(pending.await().isFailure)
        engines.forEach {
            it.coroutineContext.job.join()
            assertFalse(it.coroutineContext.job.isActive)
        }
    }

    @Test
    fun constructionFailureReleasesAnAlreadyCreatedAuthenticationEngine() = runTest {
        val engine = MockEngine { error("Unexpected request") }
        var count = 0
        assertFailsWith<IllegalStateException> {
            NetworkServices({
                if (count++ == 0) engine else error("Transport initialization failed")
            })
        }
        engine.coroutineContext.job.join()
        assertFalse(engine.coroutineContext.job.isActive)
    }

    @Test
    fun debugLogNeverContainsUrlQueryOrFragmentSecrets() {
        val sanitized =
            redactNetworkLog(
                "REQUEST: https://backend.test/captcha?special_code=secret#private\nMETHOD: GET"
            )
        assertTrue(sanitized.contains("https://backend.test/captcha"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("private"))
    }

    @Test
    fun clearingGuestSessionDiscardsAnInFlightGuestLogin() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        NetworkServices(
                {
                    MockEngine {
                        requests++
                        started.complete(Unit)
                        release.await()
                        respond(
                            loginResponse("obsolete-guest", 99),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.guest.init("guest-device")
                val pending = async { network.guest.ensureLoggedIn() }
                started.await()
                network.guest.clearGuestSession()
                release.complete(Unit)
                assertFalse(pending.await())
                assertNull(network.guest.guestToken)
                assertEquals(1, requests)
            }
    }

    @Test
    fun registeringWhileCredentialsAreExpiredDoesNotTriggerAutomaticLogin() = runTest {
        var requests = 0
        NetworkServices(
                {
                    MockEngine { request ->
                        requests++
                        assertEquals("/register", request.url.encodedPath)
                        assertNull(request.headers["Authorization"])
                        respond(
                            """{"status":0,"data":null}""",
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(MemoryCredentials(expired)) { throw AssertionError(it) }
                assertTrue(
                    network.auth.register(CommandLabUserService.RegisterRequest()).isSuccess()
                )
                assertEquals(1, requests)
            }
    }
}
