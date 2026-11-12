package yancey.chelper.desktop.instance

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import yancey.chelper.desktop.storage.atomicWrite
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.navigation.CommandLabDeepLinkParser

internal data class DesktopOpenRequest(val target: CommandLabDeepLink?, val sequence: Long)

/** Exactly one writer per application directory; later launches forward links and activate it. */
internal class DesktopInstance
private constructor(
    private val directory: File,
    private val file: RandomAccessFile,
    private val lock: FileLock,
    arguments: List<String>,
) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val token = UUID.randomUUID().toString()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val opens = MutableSharedFlow<DesktopOpenRequest>(replay = 1, extraBufferCapacity = 8)
    val requests = opens.asSharedFlow()
    private var sequence = 0L

    init {
        publish(arguments)
        atomicWrite(
            File(directory, "instance.endpoint"),
            "${server.localPort}\n$token".encodeToByteArray(),
        )
        scope.launch {
            while (isActive) {
                val socket =
                    try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                socket.use {
                    try {
                        it.soTimeout = 1500
                        val input = DataInputStream(it.getInputStream())
                        check(input.readUTF() == token)
                        val raw = input.readUTF()
                        check(raw.length <= 8192)
                        publish(listOf(raw))
                        DataOutputStream(it.getOutputStream()).apply {
                            writeBoolean(true)
                            flush()
                        }
                    } catch (_: Exception) {
                        /* An invalid local message must not stop the receiver. */
                    }
                }
            }
        }
    }

    private fun publish(arguments: List<String>) {
        val target = arguments.firstNotNullOfOrNull(CommandLabDeepLinkParser::parse)
        check(opens.tryEmit(DesktopOpenRequest(target, ++sequence)))
    }

    override fun close() {
        server.close()
        runBlocking { job.cancelAndJoin() }
        File(directory, "instance.endpoint").delete()
        lock.release()
        file.close()
    }

    companion object {
        fun acquire(directory: File, arguments: List<String>): DesktopInstance? {
            check(directory.isDirectory || directory.mkdirs()) { "无法创建应用数据目录" }
            val file = RandomAccessFile(File(directory, "instance.lock"), "rw")
            try {
                val lock =
                    try {
                        file.channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                if (lock != null)
                    return try {
                        DesktopInstance(directory, file, lock, arguments)
                    } catch (e: Exception) {
                        lock.release()
                        throw e
                    }
                file.close()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                var failure: Exception? = null
                do {
                    try {
                        val endpoint = File(directory, "instance.endpoint").readLines()
                        val port = endpoint.first().toInt()
                        val token = endpoint[1]
                        Socket().use {
                            it.connect(InetSocketAddress("127.0.0.1", port), 1000)
                            it.soTimeout = 1500
                            DataOutputStream(it.getOutputStream()).apply {
                                writeUTF(token)
                                writeUTF(
                                    arguments
                                        .firstOrNull { CommandLabDeepLinkParser.parse(it) != null }
                                        .orEmpty()
                                        .take(8192)
                                )
                                flush()
                            }
                            check(DataInputStream(it.getInputStream()).readBoolean())
                        }
                        return null
                    } catch (e: Exception) {
                        failure = e
                        Thread.sleep(100)
                    }
                } while (System.nanoTime() < deadline)
                throw IOException("CHelper 已在运行，但无法传递打开请求", failure)
            } catch (e: Exception) {
                file.close()
                throw e
            }
        }
    }
}

internal fun desktopDataDirectory() =
    File(
        System.getProperty("chelper.dataDir")
            ?: System.getenv("CHELPER_DATA_DIR")
            ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "CHelper")
                .path
    )
