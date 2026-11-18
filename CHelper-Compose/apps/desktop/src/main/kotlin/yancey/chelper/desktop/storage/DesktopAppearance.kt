package yancey.chelper.desktop.storage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.Closeable
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import yancey.chelper.data.background.BackgroundRequestState

/** Native file picking and image decoding stay in the desktop host. */
class DesktopAppearance(
    directory: File,
    private val decode: suspend (ByteArray) -> ImageBitmap = {
        Image.makeFromEncoded(it).toComposeImageBitmap()
    },
) : Closeable {
    private val file = File(directory, "background.image")
    private val state = BackgroundRequestState<ImageBitmap?>(null)
    val background = state.values
    private val job = SupervisorJob()
    private val operations = CoroutineScope(job + Dispatchers.IO.limitedParallelism(1))
    private val requests = Mutex()
    private val writes = Mutex()
    private val pending = mutableSetOf<Deferred<Unit>>()

    private suspend fun operation(action: suspend () -> Unit) {
        val task = operations.async(start = CoroutineStart.LAZY) { writes.withLock { action() } }
        synchronized(pending) { pending += task }
        task.invokeOnCompletion { synchronized(pending) { pending -= task } }
        task.start()
        task.await()
    }

    suspend fun load() {
        val revision = requests.withLock { state.beginLoad() } ?: return
        operation {
            if (file.exists()) {
                val image = decode(file.readBytes())
                requests.withLock { state.publish(revision, image) }
            }
        }
    }

    suspend fun choose() {
        val selected =
            withContext(Dispatchers.Swing) {
                val chooser =
                    JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "webp")
                    }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    chooser.selectedFile
                else null
            } ?: return
        replace(selected)
    }

    internal suspend fun replace(selected: File) {
        val revision = requests.withLock { state.begin() }
        operation {
            if (!state.isCurrent(revision)) return@operation
            require(selected.length() <= 32L * 1024 * 1024) { "背景图片不能超过 32 MB" }
            val bytes = selected.readBytes()
            val image = decode(bytes)
            requests.withLock {
                if (!state.isCurrent(revision)) return@operation
                atomicWrite(file, bytes)
                state.publish(revision, image)
            }
        }
    }

    suspend fun restore() {
        val revision = requests.withLock { state.begin() }
        operation {
            requests.withLock {
                if (!state.isCurrent(revision)) return@operation
                check(!file.exists() || file.delete()) { "背景图片删除失败" }
                state.publish(revision, null)
            }
        }
    }

    suspend fun awaitSaved() {
        synchronized(pending) { pending.toList() }.awaitAll()
    }

    override fun close() = runBlocking {
        try {
            awaitSaved()
        } finally {
            job.cancelAndJoin()
        }
    }
}
