package yancey.chelper.desktop.network

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.remote.NetworkConfig
import yancey.chelper.data.remote.createNetworkClient
import yancey.chelper.data.remote.waf.BrowserVerificationState
import yancey.chelper.nativecore.network.jvmHttpEngine

class DesktopWafBrowserTest {
    @Test
    fun realBrowserCompletesJavascriptChallengeAndOriginalApiRequestIsRetried() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-browser-challenge-").toFile()
        val cache = Files.createTempDirectory("CHelper-browser-test-cache-").toFile()
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
        val url = "http://127.0.0.1:${server.address.port}/captcha/verify"
        val browser = DesktopWafBrowser(directory, url)
        val client =
            createNetworkClient(
                jvmHttpEngine(cache, false) { it.proxy(Proxy.NO_PROXY) },
                NetworkConfig(labBaseUrl = url),
                browser = BrowserVerificationState(browser),
            )
        try {
            browser.use {
                val response = client.get(url)
                assertEquals("""{"verified":true}""", response.bodyAsText())
                assertTrue(response.call.request.headers["User-Agent"].orEmpty().contains("Edg"))
                assertFalse(response.call.request.headers["Cookie"].orEmpty().contains("login="))
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
            server.stop(0)
            client.close()
            directory.deleteRecursively()
            cache.deleteRecursively()
        }
    }

    @Test
    fun cancellingDuringBrowserStartupReleasesTheIsolatedProfile() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-browser-cancel-").toFile()
        val browser = DesktopWafBrowser(directory, "http://127.0.0.1:9/")
        try {
            val verification = async { runCatching { browser.verify() } }
            withTimeout(5000) { while (directory.listFiles().orEmpty().isEmpty()) delay(10) }
            browser.close()
            assertTrue(verification.await().isFailure)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            browser.close()
            directory.deleteRecursively()
        }
    }
}
