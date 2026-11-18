package yancey.chelper.android.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import io.ktor.http.Url
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import yancey.chelper.domain.auth.BrowserVerification
import yancey.chelper.domain.auth.BrowserVerifier

/** Detached WebView verification, confined to the main thread and the request lifetime. */
class AndroidBrowserVerifier(context: Context, private val targetUrl: String) : BrowserVerifier {
    private val context = context.applicationContext
    private val origin = Url(targetUrl)

    private fun allowed(value: String) =
        runCatching {
                val url = Url(value)
                url.protocol == origin.protocol &&
                    url.host == origin.host &&
                    url.port == origin.port
            }
            .getOrDefault(false)

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun verify(): BrowserVerification? =
        withContext(Dispatchers.Main.immediate) {
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            try {
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = USER_AGENT
                }
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(webView, true)
                withTimeoutOrNull(17000) {
                    // Refresh only verification cookies; preserve the site's unrelated login
                    // cookies.
                    val names =
                        cookies
                            .getCookie(targetUrl)
                            .orEmpty()
                            .split(';')
                            .map { it.trim().substringBefore('=') }
                            .filter { it.startsWith("EO_Bot_") || it.startsWith("__tst_") }
                    for (name in names) suspendCancellableCoroutine<Unit> { continuation ->
                        cookies.setCookie(targetUrl, "$name=; Max-Age=0; Path=/") {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                    suspendCancellableCoroutine { continuation ->
                        fun checkCookie() {
                            if (!continuation.isActive) return
                            val scoped =
                                cookies
                                    .getCookie(targetUrl)
                                    .orEmpty()
                                    .split(';')
                                    .map(String::trim)
                                    .filter {
                                        val name = it.substringBefore('=')
                                        name.startsWith("EO_Bot_") || name.startsWith("__tst_")
                                    }
                            if (
                                scoped.any {
                                    it.substringBefore('=') == "EO_Bot_Ssid" &&
                                        it.substringAfter('=', "").isNotBlank()
                                }
                            )
                                continuation.resume(
                                    BrowserVerification(scoped.joinToString("; "), USER_AGENT)
                                )
                        }
                        val poll =
                            object : Runnable {
                                override fun run() {
                                    checkCookie()
                                    if (continuation.isActive) handler.postDelayed(this, 250)
                                }
                            }
                        webView.webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ) = request == null || !allowed(request.url.toString())

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (url != null && allowed(url)) checkCookie()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true && continuation.isActive)
                                        continuation.resume(null)
                                }
                            }
                        webView.loadUrl(targetUrl)
                        handler.post(poll)
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    handler.removeCallbacksAndMessages(null)
                    webView.stopLoading()
                    webView.webViewClient = WebViewClient()
                    webView.destroy()
                }
            }
        }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
