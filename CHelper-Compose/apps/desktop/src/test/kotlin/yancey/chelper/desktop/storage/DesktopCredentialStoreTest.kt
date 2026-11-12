package yancey.chelper.desktop.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopCredentialStoreTest {
    @Test
    fun credentialsUseTheOsProtectionAndCanBeReadByTheNextProcessInstance() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val directory = Files.createTempDirectory("CHelper-dpapi-").toFile()
        try {
            val file = File(directory, "session.enc")
            val fixture = "test-only credential roundtrip"
            DesktopCredentialStore(file).write(fixture)
            assertFalse(file.readBytes().decodeToString().contains(fixture))
            assertEquals(fixture, DesktopCredentialStore(file).read())
            DesktopCredentialStore(file).clear()
            assertNull(DesktopCredentialStore(file).read())
        } finally {
            directory.deleteRecursively()
        }
    }
}
