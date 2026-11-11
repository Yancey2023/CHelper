package yancey.chelper.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedCredentialStoreInstrumentedTest {
    @Test
    fun legacyCredentialsMigrateToKeystoreProtectedNoBackupStorage() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val root =
            File(application.cacheDir, "credential-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated =
            object : ContextWrapper(application) {
                override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }

                override fun getDataDir() = File(root, "data").apply { mkdirs() }
            }
        val store = EncryptedCredentialStore(isolated, "chelper.test.${UUID.randomUUID()}")
        try {
            val legacy = File(isolated.dataDir, "library/user.json")
            legacy.parentFile!!.mkdirs()
            val sample = "{\"password\":\"test-only-password\",\"token\":\"test-only-token\"}"
            legacy.writeText(sample)
            assertEquals(sample, store.read())
            assertFalse(legacy.exists())
            val encrypted = File(isolated.noBackupFilesDir, "session.enc")
            assertTrue(encrypted.exists())
            assertFalse(encrypted.readText().contains("test-only-password"))
            assertEquals(sample, store.read())
            // Emulate interruption after AtomicFile moved the base file to its backup.
            assertTrue(encrypted.renameTo(File(encrypted.path + ".bak")))
            assertFalse(encrypted.exists())
            assertEquals(sample, store.read())
            assertTrue(encrypted.exists())
            store.clear()
            assertNull(store.read())
        } finally {
            store.clear()
            root.deleteRecursively()
        }
    }
}
