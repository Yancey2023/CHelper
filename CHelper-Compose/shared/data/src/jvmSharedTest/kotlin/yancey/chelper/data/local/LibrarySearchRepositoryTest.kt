package yancey.chelper.data.local

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.library.NetworkLibrarySearchRepository
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.service.CommandLabPublicService.GetFunctionsResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.LibraryFunction

class LibrarySearchRepositoryTest {
    private fun service(
        fetch: (page: Int, authorization: String?) -> GetFunctionsResponse
    ): CommandLabUserService =
        Proxy.newProxyInstance(
            CommandLabUserService::class.java.classLoader,
            arrayOf(CommandLabUserService::class.java),
        ) { _, method, args ->
            check(method.name == "getMyLibraries") { "Unexpected service method: ${method.name}" }
            BaseResult(status = 0, data = fetch(args[1] as Int, args[3] as String?))
        } as CommandLabUserService

    private fun page(number: Int, total: Int = 101) =
        GetFunctionsResponse(
            functions = mutableListOf(LibraryFunction(name = "query page $number")),
            currentPage = number,
            perPage = 100,
            totalCount = total,
        )

    @Test
    fun `all private pages use the token captured for the original account`() = runBlocking {
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

        assertEquals(listOf("query page 1", "query page 2"), results.map { it.name })
        assertEquals(listOf(1 to "Bearer account-7-token", 2 to "Bearer account-7-token"), requests)
        assertEquals(1, tokenReads)
    }

    @Test
    fun `account switch discards accumulated pages and never requests the next page`() =
        runBlocking {
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
    fun `logout during the last page discards its results`() = runBlocking {
        var owner: Int? = 7
        val service = service { number, _ ->
            owner = null
            page(number, total = 1)
        }
        val repository = NetworkLibrarySearchRepository({ owner }, { "token" }, { service })

        assertTrue(repository.privateLibraries("query").isEmpty())
    }

    @Test
    fun `account change during authentication does not send a private request`() = runBlocking {
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
    fun `logged out search does not authenticate or request private data`() = runBlocking {
        val repository =
            NetworkLibrarySearchRepository(
                currentAccount = { null },
                tokenForUser = { error("Must not authenticate while logged out") },
                userService = { error("Must not request private data while logged out") },
            )

        assertTrue(repository.privateLibraries("query").isEmpty())
    }

    @Test
    fun `missing owner token cannot fall back to interceptor authentication`() = runBlocking {
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
