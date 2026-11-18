package yancey.chelper.data.remote

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import yancey.chelper.data.auth.*
import yancey.chelper.data.library.*
import yancey.chelper.data.library.sync.NetworkLibrarySync
import yancey.chelper.data.local.library.FavoriteLibraryDataStore
import yancey.chelper.data.remote.app.service.*
import yancey.chelper.data.remote.interceptor.RateLimitInterceptor
import yancey.chelper.data.remote.library.service.*
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.domain.assets.Announcement as AppAnnouncement
import yancey.chelper.domain.assets.AppContentRepository
import yancey.chelper.domain.assets.VersionInfo as AppVersionInfo
import yancey.chelper.domain.auth.BrowserVerifier
import yancey.chelper.domain.auth.TokenProvider
import yancey.chelper.domain.library.*

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
    private val repositoryCache = RepositoryCache()
    val login = LoginSession(auth) { repositoryCache.invalidate() }
    val guest = GuestSession(auth, login)
    private val tokens = TokenProvider {
        currentCoroutineContext()[AccountCredentials]?.let {
            return@TokenProvider it.token
        }
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
    val sync = NetworkLibrarySync(user, login)
    private val accountRequests = AccountRequests(login)
    val accounts: AccountRepository =
        NetworkAccountRepository(auth, user, public, login, guest, repositoryCache, accountRequests)
    val libraryContent: LibraryContentRepository =
        NetworkLibraryContentRepository(public, user, accountRequests, repositoryCache)
    val activity: ActivityRepository = NetworkActivityRepository(user, accountRequests)
    val messages: MessageRepository = NetworkMessageRepository(user, accountRequests)
    val publicLibraries = NetworkPublicLibraryRepository(public, accounts, libraryContent, messages)
    val appContent: AppContentRepository =
        object : AppContentRepository {
            override suspend fun announcement() =
                app.getAnnouncement().let {
                    AppAnnouncement(
                        it.isEnable,
                        it.isForce,
                        it.isBigDialog,
                        it.isEnableCommandLab,
                        it.publicLibraryMinVersion,
                        it.title,
                        it.message,
                    )
                }

            override suspend fun latestVersion() =
                app.getLatestVersionInfo().let {
                    AppVersionInfo(it.versionCode, it.versionName, it.link, it.changelog)
                }
        }

    fun favorites(store: FavoriteLibraryDataStore): FavoriteCollectionRepository =
        NetworkFavoriteCollectionRepository(libraryContent, store, accountRequests)

    val browserLinks = BrowserLinkRepository(webSso::resolveBrowserUrl)

    override fun close() {
        client.coroutineContext.cancel()
        authClient.coroutineContext.cancel()
        client.close()
        authClient.close()
    }
}
