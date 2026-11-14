package yancey.chelper.desktop.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.interceptor.AuthInterceptor
import yancey.chelper.data.remote.interceptor.WafInterceptor
import yancey.chelper.data.remote.waf.WafHelper

class DesktopWafBrowserTest {
    @Test
    fun realBrowserCompletesJavascriptChallengeAndOriginalApiRequestIsRetried() {
        val directory = Files.createTempDirectory("CHelper-browser-challenge-").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val verified =
                exchange.requestHeaders.getFirst("Cookie")?.contains("EO_Bot_Ssid=fixture") == true
            val body =
                if (verified) """{"verified":true}"""
                else
                    """<script>document.cookie='EO_Bot_Ssid=fixture; path=/';document.cookie='login=never-copy; path=/';</script>"""
            exchange.responseHeaders.add(
                "Content-Type",
                if (verified) "application/json" else "text/html",
            )
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        val previous = ServiceManager.LAB_BASE_URL
        val url = "http://127.0.0.1:${server.address.port}/captcha/verify"
        val client =
            OkHttpClient.Builder()
                .addInterceptor(WafInterceptor())
                .addInterceptor(AuthInterceptor.INSTANCE)
                .build()
        try {
            ServiceManager.LAB_BASE_URL = url
            DesktopWafBrowser(directory, url).use {
                it.install()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    assertEquals("""{"verified":true}""", response.body.string())
                    assertTrue(response.request.header("User-Agent").orEmpty().contains("Edg"))
                    assertFalse(response.request.header("Cookie").orEmpty().contains("login="))
                }
            }
            assertTrue(
                "Unreleased browser files in ${directory.absolutePath}: " +
                    directory
                        .walkTopDown()
                        .map { file ->
                            file.relativeTo(directory).path +
                                (if (file.isFile) " writable=${file.canWrite()}" else "")
                        }
                        .joinToString(),
                directory.listFiles().orEmpty().isEmpty(),
            )
        } finally {
            ServiceManager.LAB_BASE_URL = previous
            WafHelper.install({ null }, { it?.invoke(false) })
            server.stop(0)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }
}
