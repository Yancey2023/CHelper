package yancey.chelper.data.remote.interceptor

import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RateLimitInterceptor 用令牌桶节流到 abyssous.site 的请求。 这里 **不引** mockwebserver——重依赖让单测变慢且 flaky——直接手写一个
 * FakeChain。
 *
 * 用计时来断言节流是否生效，**只用上界容差**（"不应该超过这么多"）； 不写下界（"必须等够这么多"）容易在低性能 CI 上误报。
 *
 * 限速契约：limit=N 表示每秒最多 N 个，相邻两次最小间隔 = 1000/N 毫秒。
 */
class RateLimitInterceptorTest {

    /** 把 Interceptor.Chain 里非必要的方法都堵上，免得不小心被使用。 */
    private class FakeChain(
        private val request: Request,
        private val onProceed: (Request) -> Unit = {},
    ) : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            onProceed(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = throw UnsupportedOperationException()

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override val followSslRedirects: Boolean
            get() = false

        override val followRedirects: Boolean
            get() = false

        override val dns: Dns
            get() = Dns.SYSTEM

        override val socketFactory: SocketFactory
            get() = SocketFactory.getDefault()

        override val retryOnConnectionFailure: Boolean
            get() = false

        override val authenticator: Authenticator
            get() = Authenticator.NONE

        override val cookieJar: CookieJar
            get() = CookieJar.NO_COOKIES

        override val cache: Cache?
            get() = null

        override val proxy: Proxy?
            get() = null

        override val proxySelector: ProxySelector
            get() = ProxySelector.getDefault()

        override val proxyAuthenticator: Authenticator
            get() = Authenticator.NONE

        override val sslSocketFactoryOrNull: SSLSocketFactory?
            get() = null

        override val x509TrustManagerOrNull: X509TrustManager?
            get() = null

        override val hostnameVerifier: HostnameVerifier
            get() = throw UnsupportedOperationException()

        override val certificatePinner: CertificatePinner
            get() = CertificatePinner.DEFAULT

        override val connectionPool: ConnectionPool
            get() = throw UnsupportedOperationException()

        override val eventListener: EventListener
            get() = EventListener.NONE

        override fun withDns(dns: Dns): Interceptor.Chain = this

        override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain = this

        override fun withRetryOnConnectionFailure(
            retryOnConnectionFailure: Boolean
        ): Interceptor.Chain = this

        override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this

        override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain = this

        override fun withCache(cache: Cache?): Interceptor.Chain = this

        override fun withProxy(proxy: Proxy?): Interceptor.Chain = this

        override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this

        override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain =
            this

        override fun withSslSocketFactory(
            sslSocketFactory: SSLSocketFactory?,
            x509TrustManager: X509TrustManager?,
        ): Interceptor.Chain = this

        override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain =
            this

        override fun withCertificatePinner(
            certificatePinner: CertificatePinner
        ): Interceptor.Chain = this

        override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain = this
    }

    private fun req(host: String): Request = Request.Builder().url("https://$host/api").build()

    @Test
    fun `非目标主机不应被节流`() {
        // limit=1 表示每秒只能 1 个，但 host 不是 abyssous.site，应当瞬间通过两次
        val interceptor = RateLimitInterceptor(limit = 1)
        val req = req("example.com")

        val start = System.currentTimeMillis()
        interceptor.intercept(FakeChain(req))
        interceptor.intercept(FakeChain(req))
        val elapsed = System.currentTimeMillis() - start

        // 1 秒已经够宽了，仍超过说明被错误节流
        assertTrue("非目标主机不应触发节流，实际 ${elapsed}ms", elapsed < 500)
    }

    @Test
    fun `limit 为 0 时不应进行节流`() {
        val interceptor = RateLimitInterceptor(limit = 0)
        val req = req("abyssous.site")

        val start = System.currentTimeMillis()
        interceptor.intercept(FakeChain(req))
        interceptor.intercept(FakeChain(req))
        val elapsed = System.currentTimeMillis() - start

        assertTrue("limit=0 表示关闭节流，实际 ${elapsed}ms", elapsed < 500)
    }

    @Test
    fun `连续两次请求应当被节流到接近 minInterval`() {
        // limit=10 表示 minInterval=100ms。两次请求总耗时应该 >= 100ms 但 << 1s
        val interceptor = RateLimitInterceptor(limit = 10)
        val req = req("abyssous.site")

        // 第一次先打掉，让 lastRequestTime 落地
        interceptor.intercept(FakeChain(req))

        val start = System.currentTimeMillis()
        interceptor.intercept(FakeChain(req))
        val elapsed = System.currentTimeMillis() - start

        // 容差范围：最少要 sleep 一会儿（>=80ms 给 OS 调度抖动留点空间），但不能离谱（<1500ms）
        assertTrue("第二次请求应被节流，实际 ${elapsed}ms", elapsed in 80..1500)
    }

    @Test
    fun `节流后再次请求若已过冷却期不应再等`() {
        val interceptor = RateLimitInterceptor(limit = 10)
        val req = req("abyssous.site")

        // 打一次设置 lastRequestTime
        interceptor.intercept(FakeChain(req))
        // 主动等过冷却期（>100ms 即可）
        Thread.sleep(150)

        val start = System.currentTimeMillis()
        interceptor.intercept(FakeChain(req))
        val elapsed = System.currentTimeMillis() - start

        assertTrue("已过冷却期不应再 sleep，实际 ${elapsed}ms", elapsed < 80)
    }
}
