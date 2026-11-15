package yancey.chelper.data.local

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.IOException
import yancey.chelper.data.library.NetworkLibrarySearchRepository
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.createNetworkClient
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.service.CommandLabPublicService.GetFunctionsResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.data.remote.library.service.KtorCommandLabUserService
import yancey.chelper.domain.library.LibraryFunction

class LibrarySearchRepositoryTest {
    private fun service(
        fetch: (page: Int, authorization: String?) -> GetFunctionsResponse
    ): CommandLabUserService =
        KtorCommandLabUserService(
            RemoteApi(
                createNetworkClient(
                    MockEngine { request ->
                        val number = request.url.parameters["page_num"]!!.toInt()
                        respond(
                            Json.encodeToString(
                                BaseResult(
                                    status = 0,
                                    data = fetch(number, request.headers["Authorization"]),
                                )
                            ),
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                ),
                "https://commandlab.test/",
            )
        )

    private fun page(number: Int, total: Int = 101) =
        GetFunctionsResponse(
            functions = mutableListOf(LibraryFunction(name = "query page $number")),
            currentPage = number,
            perPage = 100,
            totalCount = total,
        )

    @Test
    fun `all private pages use the token captured for the original account`() = runTest {
        val requests = mutableListOf<Pair<Int, String?>>()
        var tokenReads = 0
        val service = service { number, authorization ->
            requests += number to authorization
            page(number)
        }
        val repository =
            NetworkLibrarySearchRepository(
                currentAccount = { 7 },
                tokenForUser = { owner ->
                    assertEquals(7, owner)
                    tokenReads++
                    "account-7-token"
                },
                userService = { service },
            )

        val results = repository.privateLibraries("query")

        assertEquals(listOf<String?>("query page 1", "query page 2"), results.map { it.name })
        assertEquals(
            listOf<Pair<Int, String?>>(
                1 to "Bearer account-7-token",
                2 to "Bearer account-7-token",
            ),
            requests,
        )
        assertEquals(1, tokenReads)
    }

    @Test
    fun `account switch discards accumulated pages and never requests the next page`() = runTest {
        var owner: Int? = 7
        var requests = 0
        val service = service { number, _ ->
            requests++
            if (number == 2) owner = 8
            page(number, total = 201)
        }
        val repository = NetworkLibrarySearchRepository({ owner }, { "token" }, { service })

        assertTrue(repository.privateLibraries("query").isEmpty())
        assertEquals(2, requests)
    }

    @Test
    fun `logout during the last page discards its results`() = runTest {
        var owner: Int? = 7
        val service = service { number, _ ->
            owner = null
            page(number, total = 1)
        }
        val repository = NetworkLibrarySearchRepository({ owner }, { "token" }, { service })

        assertTrue(repository.privateLibraries("query").isEmpty())
    }

    @Test
    fun `account change during authentication does not send a private request`() = runTest {
        var owner: Int? = 7
        val repository =
            NetworkLibrarySearchRepository(
                currentAccount = { owner },
                tokenForUser = {
                    owner = 8
                    "old-token"
                },
                userService = { error("Must not request another account's libraries") },
            )

        assertTrue(repository.privateLibraries("query").isEmpty())
    }

    @Test
    fun `logged out search does not authenticate or request private data`() = runTest {
        val repository =
            NetworkLibrarySearchRepository(
                currentAccount = { null },
                tokenForUser = { error("Must not authenticate while logged out") },
                userService = { error("Must not request private data while logged out") },
            )

        assertTrue(repository.privateLibraries("query").isEmpty())
    }

    @Test
    fun `missing owner token cannot fall back to interceptor authentication`() = runTest {
        val repository =
            NetworkLibrarySearchRepository(
                currentAccount = { 7 },
                tokenForUser = { null },
                userService = { error("Must not send an unauthenticated private request") },
            )

        try {
            repository.privateLibraries("query")
            fail("Expected authentication failure")
        } catch (_: IOException) {
            // The UI can offer a retry after the owner signs in again.
        }
    }
}
