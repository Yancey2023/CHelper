package yancey.chelper.network.library.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EdgeOne WAF 绕过工具
 * 
 * 使用 WebView 加载页面以通过 JS 质询，并提取 Cookie
 */
object WafHelper {
    private const val TAG = "WafHelper"
    private const val TARGET_URL = "https://abyssous.site/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // Cookie 缓存
    private var cachedCookie: String? = null

    // 是否正在刷新
    private val isRefreshing = AtomicBoolean(false)

    private var appContext: Context? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext
        // 初始化时尝试刷新一次
        Handler(Looper.getMainLooper()).post {
            refreshCookie()
        }
    }

    /**
     * 获取当前 Cookie
     */
    fun getCookie(): String? {
        return cachedCookie
    }

    /**
     * 刷新 Cookie (异步)
     */
    fun refreshCookie(callback: ((Boolean) -> Unit)? = null) {
        if (isRefreshing.getAndSet(true)) {
            // 如果正在刷新，直接返回失败或等待（这里简单处理为失败，避免并发复杂性）
            callback?.invoke(false)
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                performRefresh { success ->
                    isRefreshing.set(false)
                    callback?.invoke(success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                isRefreshing.set(false)
                callback?.invoke(false)
            }
        }
    }

    // 移除同步方法避免死锁风险，因为 WebView 必须在主线程

    @SuppressLint("SetJavaScriptEnabled")
    private fun performRefresh(callback: (Boolean) -> Unit) {
        val context = appContext
        if (context == null) {
            callback(false)
            return
        }

        val webView = WebView(context)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // settings.databaseEnabled = true // Deprecated in API 24
        settings.userAgentString = USER_AGENT
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 设置 CookieManager
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true) // minSdk 24 >= 21

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 获取 Cookie
                val cookie = cookieManager.getCookie(url)
                Log.d(TAG, "Page finished: $url, Cookie: $cookie")

                if (cookie != null && cookie.contains("EO_Bot_Ssid")) {
                    cachedCookie = cookie
                    Log.i(TAG, "WAF Cookie obtained: $cookie")

                    // 成功获取到关键 Cookie，清理并返回
                    callback(true)
                    destroyWebView(webView)
                } else {
                    // 可能通过了质询但还没写 Cookie，或者还在跳转
                    // 延迟检查一下
                    Handler(Looper.getMainLooper()).postDelayed({
                        val lateCookie = cookieManager.getCookie(url)
                        if (lateCookie != null && lateCookie.contains("EO_Bot_Ssid")) {
                            cachedCookie = lateCookie
                            Log.i(TAG, "WAF Cookie obtained (delayed): $lateCookie")
                            callback(true)
                        } else {
                            // 即使没有 EO_Bot_Ssid，也尝试保存一下，也许是其他 Cookie
                            cachedCookie = lateCookie
                            Log.w(TAG, "WAF Cookie might be missing EO_Bot_Ssid: $lateCookie")
                            callback(true)
                        }
                        destroyWebView(webView)
                    }, 3000)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val errorCode = error?.errorCode ?: 0
                    val description = error?.description ?: "Unknown error"
                    Log.e(TAG, "WebView error: $errorCode, $description")
                    destroyWebView(webView)
                    callback(false)
                }
            }
        }

        Log.d(TAG, "Loading URL: $TARGET_URL")
        webView.loadUrl(TARGET_URL)
    }

    private fun destroyWebView(webView: WebView) {
        try {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
