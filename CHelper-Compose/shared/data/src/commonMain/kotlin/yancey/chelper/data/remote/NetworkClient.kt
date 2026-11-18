package yancey.chelper.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import yancey.chelper.data.logging.ClientLog
import yancey.chelper.data.remote.interceptor.*
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.domain.auth.BrowserVerifier
import yancey.chelper.domain.auth.TokenProvider

internal val NetworkJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Ktor's official logger is routed through the application sink with URL queries removed. */
internal fun redactNetworkLog(message: String): String =
    Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).replace(message) { match ->
        match.value.substringBefore('?').substringBefore('#')
    }

fun createNetworkClient(
    engine: HttpClientEngine,
    config: NetworkConfig = NetworkConfig(),
    tokens: TokenProvider = TokenProvider { null },
    browser: BrowserVerificationState = BrowserVerificationState(BrowserVerifier { null }),
    limiter: RateLimitInterceptor = RateLimitInterceptor(2),
): HttpClient {
    val auth = AuthInterceptor(Url(config.labBaseUrl), tokens)
    val waf = WafInterceptor(Url(config.labBaseUrl), browser)
    return HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(NetworkJson)
                json(NetworkJson, ContentType.Text.Plain)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 15000
            }
            if (config.debug)
                install(Logging) {
                    level = LogLevel.INFO
                    logger =
                        object : Logger {
                            override fun log(message: String) =
                                ClientLog.d("HTTP", redactNetworkLog(message))
                        }
                    sanitizeHeader {
                        it.equals(HttpHeaders.Authorization, true) ||
                            it.equals(HttpHeaders.Cookie, true) ||
                            it.equals(HttpHeaders.SetCookie, true)
                    }
                }
        }
        .apply {
            coroutineContext.job.invokeOnCompletion { engine.close() }
            plugin(HttpSend).intercept { request ->
                auth.apply(request)
                val url = request.url.build()
                // Cache only public static app resources, never credentials, private data or WAF
                // pages.
                if (
                    url.sameOrigin(Url(config.labBaseUrl)) ||
                        request.headers[HttpHeaders.Authorization] != null
                )
                    request.headers[HttpHeaders.CacheControl] = "no-store"
                limiter.await(url)
                waf.prepare(request)
                val call = execute(request)
                if (waf.isChallenge(call.response)) {
                    call.response.bodyAsChannel().cancel(null)
                    waf.refresh(request)
                    limiter.await(request.url.build())
                    execute(request)
                } else call
            }
        }
}
