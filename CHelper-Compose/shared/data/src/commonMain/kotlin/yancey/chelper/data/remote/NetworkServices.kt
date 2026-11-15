package yancey.chelper.data.remote

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import yancey.chelper.data.auth.*
import yancey.chelper.data.library.*
import yancey.chelper.data.library.sync.NetworkLibrarySync
import yancey.chelper.data.remote.app.service.*
import yancey.chelper.data.remote.cache.CloudLibraryCache
import yancey.chelper.data.remote.interceptor.RateLimitInterceptor
import yancey.chelper.data.remote.library.service.*
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.domain.auth.BrowserVerifier
import yancey.chelper.domain.auth.TokenProvider

/**
 * Instance graph owned by the application. Auth requests never enter automatic token acquisition.
 */
class NetworkServices(
    engineFactory: (cacheEnabled: Boolean) -> HttpClientEngine,
    val config: NetworkConfig = NetworkConfig(),
    verifier: BrowserVerifier = BrowserVerifier { null },
) : AutoCloseable {
    private val browser = BrowserVerificationState(verifier)
    private val limiter = RateLimitInterceptor(2)
    private val authClient =
        createNetworkClient(engineFactory(false), config, browser = browser, limiter = limiter)
    val auth: CommandLabAuthService =
        KtorCommandLabUserService(RemoteApi(authClient, config.labBaseUrl))
    val cache = CloudLibraryCache()
    val login = LoginSession(auth, cache::invalidateAll)
    val guest = GuestSession(auth, login)
    private val tokens = TokenProvider {
        val formal =
            try {
                login.getToken()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        formal ?: guest.guestToken ?: if (guest.ensureLoggedIn()) guest.guestToken else null
    }
    private val client =
        try {
            createNetworkClient(engineFactory(true), config, tokens, browser, limiter)
        } catch (error: Throwable) {
            authClient.close()
            throw error
        }
    val app: CHelperService = KtorCHelperService(RemoteApi(client, config.appBaseUrl))
    val public: CommandLabPublicService =
        KtorCommandLabPublicService(RemoteApi(client, config.labBaseUrl))
    val user: CommandLabUserService =
        KtorCommandLabUserService(RemoteApi(client, config.labBaseUrl))
    val captcha: CaptchaService = KtorCaptchaService(RemoteApi(authClient, config.labBaseUrl))
    val webSso = CommandLabWebSso(login, user)
    val search =
        NetworkLibrarySearchRepository(
            { login.accountId.value },
            login::tokenForUser,
            { user },
            public,
        )
    val publicLibraries = NetworkPublicLibraryRepository(public, user, login)
    val sync = NetworkLibrarySync(user, login)

    override fun close() {
        client.coroutineContext.cancel()
        authClient.coroutineContext.cancel()
        client.close()
        authClient.close()
    }
}
