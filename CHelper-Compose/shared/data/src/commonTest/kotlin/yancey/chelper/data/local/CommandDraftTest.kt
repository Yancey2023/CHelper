package yancey.chelper.data.local

import androidx.datastore.core.CorruptionException
import kotlin.test.*
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import yancey.chelper.data.local.command.CommandDraftSerializer
import yancey.chelper.data.local.command.LegacyCommandDraftMigration
import yancey.chelper.domain.command.CommandDraft

class CommandDraftTest {
    private val fs = FakeFileSystem()

    @Test
    fun `malformed legacy UTF does not block saving a new draft`() = runTest {
        val file = "/legacy.dat".toPath()
        fs.write(file) {
            writeShort(1)
            writeByte(255)
        }
        val migration = LegacyCommandDraftMigration(file, fileSystem = fs)
        assertNull(migration.migrate(null))
        migration.cleanUp()
        assertTrue(fs.exists(file))
    }

    @Test
    fun `legacy draft migrates text and selection and is cleaned only after migration`() = runTest {
        val file = "/legacy.dat".toPath()
        fs.write(file) {
            writeShort(10)
            writeUtf8("say 中文")
            writeInt(6)
            writeInt(2)
        }
        val migration = LegacyCommandDraftMigration(file, fileSystem = fs)
        assertTrue(migration.shouldMigrate(null))
        assertEquals(CommandDraft("say 中文", 6, 2), migration.migrate(null))
        assertTrue(fs.exists(file))
        assertFalse(migration.shouldMigrate(CommandDraft("new", 0, 0)))
        migration.cleanUp()
        assertFalse(fs.exists(file))
    }

    @Test
    fun `draft survives closing and recreating its data store`() = runTest {
        val file = "/draft.dat".toPath()
        val draft = CommandDraft("say 中文".repeat(20000), 500, 10)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = createDataStore(file, CommandDraftSerializer, firstScope, fileSystem = fs)
        try {
            store.updateData { draft }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened =
                createDataStore(file, CommandDraftSerializer, secondScope, fileSystem = fs)
            assertEquals(draft, reopened.data.first())
        } finally {
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `long Unicode commands round trip without the writeUTF size limit`() = runTest {
        val text = "say 中文😀".repeat(20000)
        val draft = CommandDraft(text, text.length, 5)
        val output = Buffer()
        CommandDraftSerializer.writeTo(draft, output)
        assertEquals(draft, CommandDraftSerializer.readFrom(output))
    }

    @Test
    fun `invalid selection is clamped and reversed selection is preserved`() = runTest {
        val output = Buffer()
        CommandDraftSerializer.writeTo(CommandDraft("abc", 500, -1), output)
        assertEquals(CommandDraft("abc", 3, 0), CommandDraftSerializer.readFrom(output))
    }

    @Test
    fun `truncated or malicious length prefix is treated as corruption`() = runTest {
        val output = Buffer()
        output.writeInt(Int.MAX_VALUE)
        try {
            CommandDraftSerializer.readFrom(output)
            fail("Expected corruption")
        } catch (_: CorruptionException) {}
    }

    @Test
    fun legacyModifiedUtfPreservesNulAndEmoji() = runTest {
        // Java writeUTF("a\u0000😀") followed by two big-endian selection offsets.
        val file = "/nul-emoji.dat".toPath()
        fs.write(file) {
            write(byteArrayOf(0, 9, 97, -64, -128, -19, -96, -67, -19, -72, -128))
            writeInt(4)
            writeInt(1)
        }
        assertEquals(
            CommandDraft("a\u0000😀", 4, 1),
            LegacyCommandDraftMigration(file, fileSystem = fs).migrate(null),
        )
    }
}
