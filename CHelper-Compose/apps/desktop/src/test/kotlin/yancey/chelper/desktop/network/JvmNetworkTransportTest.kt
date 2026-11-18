package yancey.chelper.desktop.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpProtocolVersion
import java.io.File
import java.net.Proxy
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.remote.createNetworkClient
import yancey.chelper.nativecore.network.jvmHttpEngine

class JvmNetworkTransportTest {
    private fun fixture(
        configure: (OkHttpClient.Builder) -> Unit = { it.proxy(Proxy.NO_PROXY) },
        block: suspend (HttpClient, File) -> Unit,
    ) = runBlocking {
        val directory = Files.createTempDirectory("CHelper-http-contract-").toFile()
        val engine = jvmHttpEngine(directory, true, configure)
        val client = createNetworkClient(engine)
        try {
            block(client, directory)
        } finally {
            client.close()
            client.coroutineContext.job.join()
            engine.coroutineContext.job.join()
            assertTrue(directory.deleteRecursively())
        }
    }

    @Test
    fun trustedTlsNegotiatesHttp2() {
        val certificate =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls =
            HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory())
            server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            server.enqueue(MockResponse(body = "verified TLS"))
            server.start()
            fixture({
                it.proxy(Proxy.NO_PROXY)
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            }) { client, _ ->
                val response = client.get(server.url("/").toString())
                assertEquals("verified TLS", response.bodyAsText())
                assertEquals(HttpProtocolVersion.HTTP_2_0, response.version)
            }
        }
    }

    @Test
    fun brotliResponsesAreDecodedByTheTransport() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Encoding", "br")
                    .body(Buffer().write("1b1100f8a5c3e0e6f294269c1423ff8c0f9105".decodeHex()))
                    .build()
            )
            server.start()
            fixture { client, _ ->
                assertEquals("say 中文 payload", client.get(server.url("/").toString()).bodyAsText())
            }
            assertTrue(server.takeRequest().headers["Accept-Encoding"].orEmpty().contains("br"))
        }
    }

    @Test
    fun publicCacheRevalidatesAnExpiredEntryUsingEtag() {
        MockWebServer().use { server ->
            val date =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Date", date)
                    .addHeader("Cache-Control", "public, max-age=0")
                    .addHeader("ETag", "\"fixture-v1\"")
                    .body("cached value")
                    .build()
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(304)
                    .addHeader("Date", date)
                    .addHeader("Cache-Control", "public, max-age=3600")
                    .build()
            )
            server.start()
            fixture { client, _ ->
                val url = server.url("/public").toString()
                assertEquals("cached value", client.get(url).bodyAsText())
                assertEquals("cached value", client.get(url).bodyAsText())
                assertEquals(2, server.requestCount)
                server.takeRequest()
                assertEquals("\"fixture-v1\"", server.takeRequest().headers["If-None-Match"])
            }
        }
    }

    @Test
    fun freshPublicResponsesAreServedWithoutAnotherNetworkRequest() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Cache-Control", "public, max-age=3600")
                    .body("cached value")
                    .build()
            )
            server.enqueue(MockResponse(code = 500, body = "Unexpected uncached request"))
            server.start()
            fixture { client, _ ->
                val url = server.url("/public").toString()
                repeat(2) { assertEquals("cached value", client.get(url).bodyAsText()) }
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun authenticatedResponsesAreNeverReadFromTheDiskCache() {
        MockWebServer().use { server ->
            repeat(2) {
                server.enqueue(
                    MockResponse.Builder()
                        .addHeader("Cache-Control", "public, max-age=3600")
                        .body("account data $it")
                        .build()
                )
            }
            server.start()
            fixture { client, _ ->
                val url = server.url("/private").toString()
                assertEquals(
                    "account data 0",
                    client.get(url) { headers.append("Authorization", "Bearer owner") }.bodyAsText(),
                )
                assertEquals(
                    "account data 1",
                    client.get(url) { headers.append("Authorization", "Bearer owner") }.bodyAsText(),
                )
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test
    fun transportHonorsAnExplicitHttpProxy() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse(body = "proxied"))
            proxy.start()
            fixture({ it.proxy(proxy.proxyAddress) }) { client, _ ->
                assertEquals("proxied", client.get("http://backend.invalid/public").bodyAsText())
            }
            assertTrue(proxy.takeRequest().requestLine.contains("http://backend.invalid/public"))
        }
    }

    @Test
    fun publicDiskCacheEvictsEntriesAtTenMebibytes() {
        MockWebServer().use { server ->
            val body = "x".repeat(4 * 1024 * 1024)
            repeat(3) {
                server.enqueue(
                    MockResponse.Builder()
                        .addHeader("Cache-Control", "public, max-age=3600")
                        .body(body)
                        .build()
                )
            }
            server.start()
            fixture { client, directory ->
                repeat(3) {
                    assertEquals(
                        body.length,
                        client.get(server.url("/entry-$it").toString()).bodyAsText().length,
                    )
                }
                // Closing waits for OkHttp's quota enforcement and journal release.
                client.close()
                client.coroutineContext.job.join()
                client.engine.coroutineContext.job.join()
                val bytes =
                    File(directory, "http_cache")
                        .walkTopDown()
                        .filter(File::isFile)
                        .sumOf(File::length)
                assertTrue("Unbounded HTTP cache: $bytes", bytes <= 10L * 1024 * 1024 + 65536)
                assertTrue(bytes > 0)
            }
        }
    }
}
