package yancey.chelper.data.library

import io.ktor.client.engine.mock.*
import io.ktor.http.headersOf
import kotlin.test.*
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import yancey.chelper.data.remote.NetworkConfig
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.data.remote.app.model.Announcement as AnnouncementDto
import yancey.chelper.domain.assets.Announcement as AnnouncementSnapshot
import yancey.chelper.domain.auth.CredentialStore
import yancey.chelper.domain.library.RepositoryResult
import yancey.chelper.domain.library.model.ProfileChanges
import yancey.chelper.domain.library.model.UserProfile

class CommunityRepositoryTest {
    private class Credentials : CredentialStore {
        var content: String? =
            """{"mail":"one","password":"password","token":"owner-one","lastLoginTimestamp":${Clock.System.now().toEpochMilliseconds()},"user":{"id":17,"nickname":"old","is_guest":false}}"""

        override fun read() = content

        override fun write(content: String) {
            this.content = content
        }

        override fun clear() {
            content = null
        }
    }

    @Test
    fun loginPublishesTheAcceptedAccountSnapshot() = runTest {
        NetworkServices({
                MockEngine {
                    respond(
                        """{"status":0,"data":{"token":"accepted","user":{"id":17,"nickname":"accepted","is_guest":false}}}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            })
            .use { network ->
                val accepted = network.accounts.login("one", "password").getOrThrow()
                assertEquals(17, accepted.id)
                assertEquals(accepted, network.accounts.currentUser)
            }
    }

    @Test
    fun malformedLoginCannotPublishAnAuthenticatedAccount() = runTest {
        NetworkServices({
                MockEngine {
                    respond(
                        """{"status":0,"data":{"user":{"id":17}}}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            })
            .use { network ->
                assertTrue(network.accounts.login("one", "password").isFailure)
                assertFalse(network.accounts.isLoggedIn)
                assertNull(network.accounts.currentUser)
                assertNull(network.accounts.accountId.value)
            }
    }

    @Test
    fun switchingAccountsDuringGuestMigrationCancelsTheEarlierLoginCompletion() = runTest {
        var logins = 0
        val entered = CompletableDeferred<Unit>()
        NetworkServices({
                MockEngine { request ->
                    if (request.url.encodedPath == "/guest/migrate") {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                    val id = if (++logins == 1) 17 else 18
                    respond(
                        """{"status":0,"data":{"token":"token-$id","user":{"id":$id,"is_guest":false}}}""",
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            })
            .use { network ->
                network.guest.init("device")
                val first = async { runCatching { network.accounts.login("one", "password") } }
                entered.await()
                network.login.login("two", "password")
                assertIs<CancellationException>(first.await().exceptionOrNull())
                assertEquals(18, network.accounts.currentUser?.id)
            }
    }

    @Test
    fun aForcedRefreshCannotBeOverwrittenByAnOlderConcurrentRead() = runTest {
        val cache = RepositoryCache()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            cache.read("profile:17", false) {
                entered.complete(Unit)
                release.await()
                RepositoryResult(UserProfile(nickname = "old"))
            }
        }
        entered.await()
        cache.read("profile:17", true) { RepositoryResult(UserProfile(nickname = "new")) }
        release.complete(Unit)
        old.await()
        assertEquals(
            "new",
            cache
                .read<UserProfile>("profile:17", false) { error("Unexpected cache miss") }
                .data
                ?.nickname,
        )
    }

    @Test
    fun browsingManyProfilesKeepsTheMemoryCacheBounded() = runTest {
        val cache = RepositoryCache()
        repeat(65) { id ->
            cache.read("profile:$id", false) { RepositoryResult(UserProfile(id = id)) }
        }
        assertEquals(
            64,
            cache
                .read<UserProfile>("profile:64", false) { error("Newest profile was evicted") }
                .data
                ?.id,
        )
        var reloaded = false
        cache.read("profile:0", false) {
            reloaded = true
            RepositoryResult(UserProfile(id = 0))
        }
        assertTrue(reloaded)
    }

    @Test
    fun announcementSnapshotsPreserveExistingIgnoredAnnouncementKeys() {
        val wire = AnnouncementDto(true, false, true, true, 87, "title", "message")
        val snapshot = AnnouncementSnapshot(true, false, true, true, 87, "title", "message")
        assertEquals(wire.hashCode(), snapshot.hashCode())
        assertEquals(
            snapshot,
            AnnouncementSnapshot(true, false, true, false, 88, "title", "message"),
        )
    }

    @Test
    fun anInvalidatedInFlightReadCannotReplaceTheNewCacheEntry() = runTest {
        val cache = RepositoryCache()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = async {
            cache.read("profile:17", false) {
                started.complete(Unit)
                release.await()
                RepositoryResult(UserProfile(nickname = "old"))
            }
        }
        started.await()
        cache.invalidate()
        cache.read("profile:17", false) { RepositoryResult(UserProfile(nickname = "new")) }
        release.complete(Unit)
        old.await()
        val result = cache.read<UserProfile>("profile:17", false) { error("Unexpected cache miss") }
        assertEquals("new", result.data?.nickname)
    }

    @Test
    fun profileMutationPublishesANewSnapshotPersistsItAndInvalidatesReads() = runTest {
        var profileReads = 0
        val credentials = Credentials()
        NetworkServices(
                {
                    MockEngine { request ->
                        val response =
                            when (request.url.encodedPath) {
                                "/users/17" -> """{"status":0}"""
                                "/users/public/17" -> {
                                    profileReads++
                                    """{"status":0,"data":{"id":17,"nickname":"profile"}}"""
                                }
                                else -> error("Unexpected ${request.url}")
                            }
                        respond(response, headers = headersOf("Content-Type", "application/json"))
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(credentials) { throw AssertionError(it) }
                val old = requireNotNull(network.accounts.currentUser)
                network.accounts.profile(17)
                network.accounts.profile(17)
                assertEquals(1, profileReads)
                assertTrue(
                    network.accounts.updateProfile(17, ProfileChanges(nickname = "new")).isSuccess()
                )
                assertEquals("old", old.nickname)
                assertEquals("new", network.accounts.currentUser?.nickname)
                assertTrue(credentials.content.orEmpty().contains("new"))
                network.accounts.profile(17)
                assertEquals(2, profileReads)
            }
    }

    @Test
    fun failedProfileMutationRetainsTheSessionAndCachedProfile() = runTest {
        var profileReads = 0
        NetworkServices(
                {
                    MockEngine { request ->
                        val response =
                            if (request.url.encodedPath == "/users/17")
                                """{"status":1,"message":"denied"}"""
                            else {
                                profileReads++
                                """{"status":0,"data":{"id":17}}"""
                            }
                        respond(response, headers = headersOf("Content-Type", "application/json"))
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(Credentials()) { throw AssertionError(it) }
                network.accounts.profile(17)
                assertFalse(
                    network.accounts.updateProfile(17, ProfileChanges(nickname = "new")).isSuccess()
                )
                assertEquals("old", network.accounts.currentUser?.nickname)
                network.accounts.profile(17)
                assertEquals(1, profileReads)
            }
    }

    @Test
    fun accountSwitchRejectsAnOldPrivateResponseAndDoesNotCacheItForTheNextAccount() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val authorizations = mutableListOf<String?>()
        var quotaReads = 0
        NetworkServices(
                {
                    MockEngine { request ->
                        val response =
                            if (request.url.encodedPath == "/register/login")
                                """{"status":0,"data":{"token":"owner-two","user":{"id":18,"is_guest":false}}}"""
                            else {
                                authorizations += request.headers["Authorization"]
                                if (++quotaReads == 1) {
                                    entered.complete(Unit)
                                    release.await()
                                }
                                """{"status":0,"data":{"used":$quotaReads,"limit":20}}"""
                            }
                        respond(response, headers = headersOf("Content-Type", "application/json"))
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(Credentials()) { throw AssertionError(it) }
                val old = async { runCatching { network.libraryContent.quota() } }
                entered.await()
                network.accounts.logout()
                network.login.login("two", "password")
                release.complete(Unit)
                assertIs<CancellationException>(old.await().exceptionOrNull())
                assertEquals(2, network.libraryContent.quota().data?.used)
                assertEquals(
                    listOf<String?>("Bearer owner-one", "Bearer owner-two"),
                    authorizations,
                )
            }
    }

    @Test
    fun reloggingIntoTheSameAccountRejectsAnEarlierProfileMutation() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        NetworkServices(
                {
                    MockEngine { request ->
                        val response =
                            if (request.url.encodedPath == "/register/login")
                                """{"status":0,"data":{"token":"new-session","user":{"id":17,"nickname":"new-session","is_guest":false}}}"""
                            else {
                                entered.complete(Unit)
                                release.await()
                                """{"status":0}"""
                            }
                        respond(response, headers = headersOf("Content-Type", "application/json"))
                    }
                },
                NetworkConfig(labBaseUrl = "https://backend.test/"),
            )
            .use { network ->
                network.login.init(Credentials()) { throw AssertionError(it) }
                val old = async {
                    runCatching {
                        network.accounts.updateProfile(17, ProfileChanges(nickname = "obsolete"))
                    }
                }
                entered.await()
                network.login.login("one", "password")
                release.complete(Unit)
                assertIs<CancellationException>(old.await().exceptionOrNull())
                assertEquals("new-session", network.accounts.currentUser?.nickname)
            }
    }

    @Test
    fun unsignedPrivateOperationsFailBeforeAnyHttpRequest() = runTest {
        NetworkServices({ MockEngine { error("Unauthenticated private request escaped") } }).use {
            network ->
            assertFailsWith<IllegalStateException> { network.libraryContent.upload("say hi") }
            assertFailsWith<IllegalStateException> { network.messages.load(1) }
        }
    }
}
