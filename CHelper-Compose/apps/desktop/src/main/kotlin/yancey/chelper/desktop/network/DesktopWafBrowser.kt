package yancey.chelper.desktop.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.Url
import io.ktor.websocket.*
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.ProcessHandle
import java.net.Proxy
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import yancey.chelper.domain.auth.BrowserVerification
import yancey.chelper.domain.auth.BrowserVerifier
import yancey.chelper.nativecore.network.jvmHttpEngine

/** Runs the site's JS challenge in an isolated Edge profile, never the user's browser profile. */
internal class DesktopWafBrowser(
    private val directory: File,
    private val targetUrl: String,
    private val executable: () -> File = ::findEdge,
) : Closeable, BrowserVerifier {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val mutex = Mutex()
    private val engine = jvmHttpEngine(directory, cacheEnabled = false) { it.proxy(Proxy.NO_PROXY) }
    private val client = HttpClient(engine) { install(WebSockets) }
    private var cookie: String? = null
    private var userAgent: String? = null

    override suspend fun verify(): BrowserVerification? {
        val operation =
            scope.async {
                mutex.withLock {
                    verifyChallenge()
                    BrowserVerification(requireNotNull(cookie), requireNotNull(userAgent))
                }
            }
        return try {
            operation.await()
        } finally {
            withContext(NonCancellable) { operation.cancelAndJoin() }
        }
    }

    private suspend fun verifyChallenge() =
        withTimeout(17_000) {
            check(directory.isDirectory || directory.mkdirs()) { "无法创建浏览器验证目录" }
            val profile = Files.createTempDirectory(directory.toPath(), "waf-edge-").toFile()
            var process: Process? = null
            var connection: DevToolsConnection? = null
            val browserProcesses = mutableSetOf<ProcessHandle>()
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
                var endpoint: String? = null
                while (endpoint == null) {
                    ensureActive()
                    process.descendants().use { children ->
                        children.forEach(browserProcesses::add)
                    }
                    endpoint = browserEndpoint(profile)
                    if (endpoint == null) {
                        // Recent Edge versions hand startup to a child and exit the launcher with
                        // 0.
                        check(process.isAlive || process.exitValue() == 0) {
                            "Microsoft Edge 无法启动浏览器验证"
                        }
                        delay(100)
                    }
                }
                connection = DevToolsConnection(client, endpoint)
                connection.opened.await()
                // CDP identifies the processes in this isolated profile even after launcher exit.
                connection
                    .command("SystemInfo.getProcessInfo")["processInfo"]
                    ?.jsonArray
                    .orEmpty()
                    .forEach { info ->
                        info.jsonObject["id"]?.jsonPrimitive?.longOrNull?.let { id ->
                            ProcessHandle.of(id).orElse(null)?.let(browserProcesses::add)
                        }
                    }
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
                            domain == Url(targetUrl).host &&
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
                    // Cancellation can precede DevTools readiness after the launcher hands off.
                    val launched = process
                    if (connection == null && launched != null) {
                        runCatching {
                            withTimeout(5000) {
                                var endpoint = browserEndpoint(profile)
                                while (endpoint == null) {
                                    if (!launched.isAlive && launched.exitValue() != 0)
                                        return@withTimeout
                                    delay(50)
                                    endpoint = browserEndpoint(profile)
                                }
                                val cleanupConnection = DevToolsConnection(client, endpoint)
                                connection = cleanupConnection
                                cleanupConnection.opened.await()
                            }
                        }
                    }
                    val children =
                        process
                            ?.descendants()
                            ?.use { it.toList() }
                            .orEmpty()
                            .toMutableSet()
                            .apply { addAll(browserProcesses) }
                    runCatching {
                        withTimeout(1000) {
                            connection
                                ?.command("SystemInfo.getProcessInfo")
                                ?.get("processInfo")
                                ?.jsonArray
                                .orEmpty()
                                .forEach { info ->
                                    info.jsonObject["id"]?.jsonPrimitive?.longOrNull?.let { id ->
                                        ProcessHandle.of(id).orElse(null)?.let(children::add)
                                    }
                                }
                        }
                    }
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

    private fun browserEndpoint(profile: File): String? {
        val activePort = File(profile, "DevToolsActivePort")
        if (!activePort.isFile) return null
        val lines = activePort.readLines()
        val port = lines.firstOrNull()?.toIntOrNull()
        val path = lines.getOrNull(1)
        return if (port in 1..65535 && path?.startsWith("/devtools/browser/") == true)
            "ws://127.0.0.1:$port$path"
        else null
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
        client.close()
        engine.close()
    }

    private class DevToolsConnection(client: HttpClient, endpoint: String) : Closeable {
        val opened = CompletableDeferred<Unit>()
        private val job = SupervisorJob()
        private val scope = CoroutineScope(job + Dispatchers.IO)
        private val ids = AtomicInteger()
        private val mutex = Mutex()
        private val pending = mutableMapOf<Int, CompletableDeferred<JsonObject>>()
        private val session = CompletableDeferred<DefaultClientWebSocketSession>()

        init {
            scope.launch {
                try {
                    val socket = client.webSocketSession(endpoint)
                    session.complete(socket)
                    opened.complete(Unit)
                    for (frame in socket.incoming) {
                        if (frame !is Frame.Text) continue
                        val message = Json.parseToJsonElement(frame.readText()).jsonObject
                        val id = message["id"]?.jsonPrimitive?.intOrNull ?: continue
                        val result = mutex.withLock { pending.remove(id) } ?: continue
                        if (message["error"] != null)
                            result.completeExceptionally(IOException("浏览器验证协议调用失败"))
                        else
                            result.complete(message["result"]?.jsonObject ?: JsonObject(emptyMap()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    withContext(NonCancellable) {
                        fail(IOException("验证浏览器已关闭"))
                        if (session.isCompleted && !session.isCancelled)
                            runCatching { session.await().cancel() }
                    }
                }
            }
        }

        private suspend fun fail(error: Throwable) {
            opened.completeExceptionally(error)
            session.completeExceptionally(error)
            mutex.withLock {
                pending.values.forEach { it.completeExceptionally(error) }
                pending.clear()
            }
        }

        suspend fun command(method: String): JsonObject =
            withTimeout(3000) {
                opened.await()
                val id = ids.incrementAndGet()
                val result = CompletableDeferred<JsonObject>()
                mutex.withLock { pending[id] = result }
                try {
                    session
                        .await()
                        .send(
                            Frame.Text(
                                buildJsonObject {
                                        put("id", id)
                                        put("method", method)
                                    }
                                    .toString()
                            )
                        )
                    result.await()
                } finally {
                    withContext(NonCancellable) { mutex.withLock { pending.remove(id) } }
                }
            }

        override fun close() {
            runBlocking { job.cancelAndJoin() }
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
