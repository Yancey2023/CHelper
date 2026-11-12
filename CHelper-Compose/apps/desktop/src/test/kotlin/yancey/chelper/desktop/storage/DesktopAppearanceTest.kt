package yancey.chelper.desktop.storage

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class DesktopAppearanceTest {
    @Test
    fun restoreSupersedesAnOlderImageThatIsStillDecoding() = runBlocking {
        val root = Files.createTempDirectory("CHelper-background-order-").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val appearance =
            DesktopAppearance(root) {
                started.complete(Unit)
                release.await()
                ImageBitmap(1, 1)
            }
        try {
            val source = File(root, "selected.bin").apply { writeText("image") }
            val first = async { appearance.replace(source) }
            withTimeout(2000) { started.await() }
            val restore = async(start = CoroutineStart.UNDISPATCHED) { appearance.restore() }
            release.complete(Unit)
            withTimeout(2000) {
                first.await()
                restore.await()
            }
            assertNull(appearance.background.first())
            assertFalse(File(root, "background.image").exists())
        } finally {
            release.complete(Unit)
            appearance.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun closingWaitsForASelectedBackgroundEvenAfterItsScreenScopeWasCancelled() = runBlocking {
        val root = Files.createTempDirectory("CHelper-background-close-").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val appearance =
            DesktopAppearance(root) {
                started.complete(Unit)
                release.await()
                ImageBitmap(1, 1)
            }
        try {
            val source = File(root, "selected.bin").apply { writeText("image") }
            val editor = launch { appearance.replace(source) }
            withTimeout(2000) { started.await() }
            editor.cancelAndJoin()
            val flushing = async(start = CoroutineStart.UNDISPATCHED) { appearance.awaitSaved() }
            assertFalse(flushing.isCompleted)
            release.complete(Unit)
            withTimeout(2000) { flushing.await() }
            assertEquals("image", File(root, "background.image").readText())
            assertNotNull(appearance.background.first())
        } finally {
            release.complete(Unit)
            appearance.close()
            root.deleteRecursively()
        }
    }
}
