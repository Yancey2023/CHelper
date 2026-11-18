package yancey.chelper.android.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundStoreInstrumentedTest {
    @get:Rule
    val folder = TemporaryFolder(ApplicationProvider.getApplicationContext<Context>().cacheDir)

    private fun imageBytes(): ByteArray {
        val image = Bitmap.createBitmap(4096, 2048, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { out ->
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        } finally {
            image.recycle()
        }
    }

    @Test
    fun oversizedImageIsSampledUsingTwoFreshStreams() {
        val bytes = imageBytes()
        val store = BackgroundStore(File(folder.root, "background.png"), 512)
        var opened = 0
        val bitmap =
            store.decode {
                opened++
                bytes.inputStream()
            }
        try {
            assertEquals(2, opened)
            assertEquals(512, bitmap.width)
            assertEquals(256, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun replacingPersistsSampledImageAndRestoreSurvivesReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = folder.newFile("source.png").apply { writeBytes(imageBytes()) }
        val file = File(folder.root, "background.png")
        val store = BackgroundStore(file, 512)
        store.replace(context.contentResolver, Uri.fromFile(source))
        assertEquals(512, store.backgroundBitmap!!.width)
        val reloaded = BackgroundStore(file, 512)
        reloaded.load()
        assertEquals(512, reloaded.backgroundBitmap!!.width)
        store.restore()
        assertNull(store.backgroundBitmap)
        assertFalse(file.exists())
        val restored = BackgroundStore(file, 512)
        restored.load()
        assertNull(restored.backgroundBitmap)
    }

    @Test
    fun invalidReplacementRetainsTheCurrentBackground() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = folder.newFile("source.png").apply { writeBytes(imageBytes()) }
        val file = File(folder.root, "background.png")
        val store = BackgroundStore(file, 512)
        store.replace(context.contentResolver, Uri.fromFile(source))
        val before = store.backgroundBitmap
        val bytes = file.readBytes()
        source.writeText("invalid image")
        try {
            store.replace(context.contentResolver, Uri.fromFile(source))
            fail("Invalid image must fail")
        } catch (_: IOException) {}
        assertSame(before, store.backgroundBitmap)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun restoreWinsEvenIfTheImageWaiterWasCancelled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = folder.newFile("source.png").apply { writeBytes(imageBytes()) }
        val file = File(folder.root, "background.png")
        val store = BackgroundStore(file, 512)
        val change =
            launch(start = CoroutineStart.UNDISPATCHED) {
                store.replace(context.contentResolver, Uri.fromFile(source))
            }
        change.cancelAndJoin()
        store.restore()
        assertNull(store.backgroundBitmap)
        assertFalse(file.exists())
        val reloaded = BackgroundStore(file, 512)
        reloaded.load()
        assertNull(reloaded.backgroundBitmap)
    }

    @Test
    fun backupOnlyBackgroundIsRecoveredBeforeExistenceIsChecked() = runBlocking {
        val file = File(folder.root, "background.png")
        val backup = File(file.path + ".bak").apply { writeBytes(imageBytes()) }
        assertFalse(file.exists())
        val store = BackgroundStore(file, 512)
        store.load()
        assertEquals(512, store.backgroundBitmap!!.width)
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }
}
