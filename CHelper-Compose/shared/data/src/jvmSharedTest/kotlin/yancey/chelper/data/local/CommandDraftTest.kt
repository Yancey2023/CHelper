package yancey.chelper.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yancey.chelper.data.local.command.CommandDraftSerializer
import yancey.chelper.data.local.command.LegacyCommandDraftMigration
import yancey.chelper.domain.command.CommandDraft

class CommandDraftTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `malformed legacy UTF does not block saving a new draft`() = runBlocking {
        val file = temporaryFolder.newFile()
        DataOutputStream(file.outputStream()).use {
            it.writeShort(1)
            it.writeByte(255)
        }
        val migration = LegacyCommandDraftMigration(file)
        assertNull(migration.migrate(null))
        migration.cleanUp()
        assertTrue(file.exists())
    }

    @Test
    fun `legacy draft migrates text and selection and is cleaned only after migration`() =
        runBlocking {
            val file = temporaryFolder.newFile()
            DataOutputStream(file.outputStream()).use {
                it.writeUTF("say 中文")
                it.writeInt(6)
                it.writeInt(2)
            }
            val migration = LegacyCommandDraftMigration(file)
            assertTrue(migration.shouldMigrate(null))
            assertEquals(CommandDraft("say 中文", 6, 2), migration.migrate(null))
            assertTrue(file.exists())
            assertFalse(migration.shouldMigrate(CommandDraft("new", 0, 0)))
            migration.cleanUp()
            assertFalse(file.exists())
        }

    @Test
    fun `draft survives closing and recreating its data store`() = runBlocking {
        val file = temporaryFolder.root.resolve("draft.dat")
        val draft = CommandDraft("say 中文".repeat(20000), 500, 10)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(CommandDraftSerializer, scope = firstScope) { file }
        try {
            store.updateData { draft }
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened =
                DataStoreFactory.create(CommandDraftSerializer, scope = secondScope) { file }
            assertEquals(draft, reopened.data.first())
        } finally {
            secondScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `long Unicode commands round trip without the writeUTF size limit`() = runBlocking {
        val text = "say 中文😀".repeat(20000)
        val draft = CommandDraft(text, text.length, 5)
        val output = ByteArrayOutputStream()
        CommandDraftSerializer.writeTo(draft, output)
        assertEquals(draft, CommandDraftSerializer.readFrom(output.toByteArray().inputStream()))
    }

    @Test
    fun `invalid selection is clamped and reversed selection is preserved`() = runBlocking {
        val output = ByteArrayOutputStream()
        CommandDraftSerializer.writeTo(CommandDraft("abc", 500, -1), output)
        assertEquals(
            CommandDraft("abc", 3, 0),
            CommandDraftSerializer.readFrom(output.toByteArray().inputStream()),
        )
    }

    @Test
    fun `truncated or malicious length prefix is treated as corruption`() = runBlocking {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).writeInt(Int.MAX_VALUE)
        try {
            CommandDraftSerializer.readFrom(output.toByteArray().inputStream())
            fail("Expected corruption")
        } catch (_: CorruptionException) {}
    }
}
