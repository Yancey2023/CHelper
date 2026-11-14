package yancey.chelper.desktop.network

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import yancey.chelper.data.remote.waf.WafHelper
import yancey.chelper.ui.platform.ClientDiagnostics

/** Runs the site's JS challenge in an isolated Edge profile, never the user's browser profile. */
internal class DesktopWafBrowser(
    private val directory: File,
    private val targetUrl: String,
    private val executable: () -> File = ::findEdge,
) : Closeable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val mutex = Mutex()
    private val client =
        OkHttpClient.Builder().proxy(Proxy.NO_PROXY).callTimeout(3, TimeUnit.SECONDS).build()
    @Volatile private var cookie: String? = null
    @Volatile private var userAgent: String? = null
    private var generation = 0

    fun install() = WafHelper.install({ cookie }, ::refresh, { userAgent })

    fun refresh(callback: ((Boolean) -> Unit)? = null) {
        val previous = synchronized(this) { generation }
        scope.launch {
            val success =
                try {
                    mutex.withLock {
                        if (synchronized(this@DesktopWafBrowser) { generation } != previous) true
                        else {
                            verify()
                            synchronized(this@DesktopWafBrowser) { generation++ }
                            true
                        }
                    }
                } catch (e: CancellationException) {
                    callback?.invoke(false)
                    throw e
                } catch (e: Exception) {
                    ClientDiagnostics.generateCustomLog(e, "BrowserVerification")
                    false
                }
            callback?.invoke(success)
        }
    }

    private suspend fun verify() =
        withTimeout(17_000) {
            check(directory.isDirectory || directory.mkdirs()) { "无法创建浏览器验证目录" }
            val profile = Files.createTempDirectory(directory.toPath(), "waf-edge-").toFile()
            var process: Process? = null
            var connection: DevToolsConnection? = null
            try {
                process =
                    ProcessBuilder(
                            executable().absolutePath,
                            "--headless=new",
                            "--no-first-run",
                            "--no-default-browser-check",
                            "--disable-background-networking",
                            "--remote-debugging-address=127.0.0.1",
                            "--remote-debugging-port=0",
                            "--user-data-dir=${profile.absolutePath}",
                            targetUrl,
                        )
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val activePort = File(profile, "DevToolsActivePort")
                var endpoint: String? = null
                while (endpoint == null) {
                    ensureActive()
                    if (activePort.isFile) {
                        val lines = activePort.readLines()
                        val port = lines.firstOrNull()?.toIntOrNull()
                        val path = lines.getOrNull(1)
                        if (port in 1..65535 && path?.startsWith("/devtools/browser/") == true)
                            endpoint = "ws://127.0.0.1:$port$path"
                    }
                    if (endpoint == null) {
                        check(process.isAlive) { "Microsoft Edge 无法启动浏览器验证" }
                        delay(100)
                    }
                }
                connection = DevToolsConnection(client, endpoint)
                connection.opened.await()
                val agent =
                    connection.command("Browser.getVersion")["userAgent"]?.jsonPrimitive?.content
                while (true) {
                    val cookies =
                        connection.command("Storage.getCookies")["cookies"]?.jsonArray.orEmpty()
                    val scoped =
                        cookies.filter { element ->
                            val value = element.jsonObject
                            val domain =
                                value["domain"]?.jsonPrimitive?.content.orEmpty().removePrefix(".")
                            domain == targetUrl.toHttpUrl().host &&
                                value["name"]?.jsonPrimitive?.content.orEmpty().let {
                                    it.startsWith("EO_Bot_") || it.startsWith("__tst_")
                                }
                        }
                    if (
                        scoped.any {
                            it.jsonObject["name"]?.jsonPrimitive?.content == "EO_Bot_Ssid" &&
                                !it.jsonObject["value"]?.jsonPrimitive?.content.isNullOrBlank()
                        }
                    ) {
                        cookie =
                            scoped.joinToString("; ") {
                                "${it.jsonObject.getValue("name").jsonPrimitive.content}=${it.jsonObject.getValue("value").jsonPrimitive.content}"
                            }
                        userAgent = agent
                        break
                    }
                    delay(300)
                }
            } finally {
                withContext(NonCancellable) {
                    val children =
                        process?.descendants()?.use { it.toList() }.orEmpty().toMutableSet()
                    runCatching { withTimeout(1000) { connection?.command("Browser.close") } }
                    connection?.close()
                    process?.let {
                        it.descendants().use { current -> current.forEach(children::add) }
                        if (!it.waitFor(1, TimeUnit.SECONDS)) {
                            children
                                .filter { child -> child.isAlive }
                                .forEach { child -> child.destroy() }
                            it.destroyForcibly().waitFor(1, TimeUnit.SECONDS)
                        }
                    }
                    // Edge's root may exit before its utility processes. Await their termination
                    // before deleting the temporary profile, including after a cancelled refresh.
                    children.filter { it.isAlive }.forEach { it.destroyForcibly() }
                    withTimeoutOrNull(2000) { while (children.any { it.isAlive }) delay(25) }
                    deleteProfile(profile)
                }
            }
        }

    private suspend fun deleteProfile(profile: File) {
        // The fresh profile belongs directly to our verification cache, never a personal profile.
        val path = profile.toPath().toAbsolutePath().normalize()
        check(path.parent == directory.toPath().toAbsolutePath().normalize())
        var failure: IOException? = null
        repeat(50) {
            try {
                if (!Files.exists(path)) return
                Files.walkFileTree(
                    path,
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            Files.deleteIfExists(file)
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(
                            dir: Path,
                            error: IOException?,
                        ): FileVisitResult {
                            if (error != null) throw error
                            Files.deleteIfExists(dir)
                            return FileVisitResult.CONTINUE
                        }
                    },
                )
                return
            } catch (error: IOException) {
                failure = error
                // Windows may retain file handles briefly after a process has terminated.
                delay(100)
            }
        }
        throw IOException("Unable to remove temporary browser profile", failure)
    }

    override fun close() {
        runBlocking { job.cancelAndJoin() }
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private class DevToolsConnection(client: OkHttpClient, endpoint: String) : Closeable {
        val opened = CompletableDeferred<Unit>()
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        private val socket =
            client.newWebSocket(
                Request.Builder().url(endpoint).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching {
                            val message = Json.parseToJsonElement(text).jsonObject
                            val id = message["id"]?.jsonPrimitive?.intOrNull ?: return
                            val result = pending.remove(id) ?: return
                            if (message["error"] != null)
                                result.completeExceptionally(IOException("浏览器验证协议调用失败"))
                            else
                                result.complete(
                                    message["result"]?.jsonObject ?: JsonObject(emptyMap())
                                )
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        fail(t)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        fail(IOException("验证浏览器已关闭"))
                    }

                    private fun fail(error: Throwable) {
                        opened.completeExceptionally(error)
                        pending.values.forEach { it.completeExceptionally(error) }
                        pending.clear()
                    }
                },
            )

        suspend fun command(method: String): JsonObject =
            withTimeout(3000) {
                val id = ids.incrementAndGet()
                val result = CompletableDeferred<JsonObject>()
                pending[id] = result
                try {
                    check(
                        socket.send(
                            buildJsonObject {
                                    put("id", id)
                                    put("method", method)
                                }
                                .toString()
                        )
                    )
                    result.await()
                } finally {
                    pending.remove(id)
                }
            }

        override fun close() {
            socket.cancel()
        }
    }
}

private fun findEdge(): File =
    listOfNotNull(
            System.getenv("ProgramFiles(x86)"),
            System.getenv("ProgramFiles"),
            System.getenv("LOCALAPPDATA"),
        )
        .map { File(it, "Microsoft/Edge/Application/msedge.exe") }
        .firstOrNull(File::isFile) ?: throw IOException("站点验证需要安装 Microsoft Edge")
