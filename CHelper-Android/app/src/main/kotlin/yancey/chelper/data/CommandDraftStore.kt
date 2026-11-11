package yancey.chelper.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UTFDataFormatException
import kotlinx.coroutines.flow.first

internal data class CommandDraft(val text: String, val start: Int, val end: Int) {
    fun clamped() = copy(start = start.coerceIn(0, text.length), end = end.coerceIn(0, text.length))
}

/** Length-prefixed UTF-8, without DataOutputStream.writeUTF's 65535-byte limit. */
internal object CommandDraftSerializer : Serializer<CommandDraft?> {
    override val defaultValue: CommandDraft? = null

    override suspend fun readFrom(input: InputStream): CommandDraft? =
        try {
            val data = DataInputStream(input)
            val size = data.readInt()
            if (size == -1) null
            else {
                if (size < 0) throw IOException("Invalid draft length")
                // Read the actual file before allocating based on an untrusted length prefix.
                val bytes = data.readBytes()
                if (size > bytes.size || bytes.size - size != 8)
                    throw IOException("Truncated draft")
                val selection = DataInputStream(bytes.inputStream(size, 8))
                CommandDraft(
                        bytes.decodeToString(0, size),
                        selection.readInt(),
                        selection.readInt(),
                    )
                    .clamped()
            }
        } catch (e: IOException) {
            throw CorruptionException("Unable to read command draft", e)
        }

    override suspend fun writeTo(t: CommandDraft?, output: OutputStream) {
        val data = DataOutputStream(output)
        if (t == null) {
            data.writeInt(-1)
        } else {
            val bytes = t.text.encodeToByteArray()
            data.writeInt(bytes.size)
            data.write(bytes)
            data.writeInt(t.start)
            data.writeInt(t.end)
        }
    }
}

private val Context.commandDraftDataStore by
    dataStore(
        fileName = "command_draft.dat",
        serializer = CommandDraftSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { null },
        produceMigrations = { context ->
            listOf(LegacyCommandDraftMigration(context.filesDir.resolve("cache/lastInput.dat")))
        },
    )

internal class LegacyCommandDraftMigration(private val legacy: File) :
    DataMigration<CommandDraft?> {
    override suspend fun shouldMigrate(currentData: CommandDraft?) =
        currentData == null && legacy.exists()

    override suspend fun migrate(currentData: CommandDraft?): CommandDraft? =
        try {
            DataInputStream(legacy.inputStream().buffered()).use {
                CommandDraft(it.readUTF(), it.readInt(), it.readInt()).clamped()
            }
        } catch (_: EOFException) {
            currentData
        } catch (_: UTFDataFormatException) {
            currentData
        }

    override suspend fun cleanUp() {
        if (legacy.exists() && !legacy.delete()) throw IOException("Unable to remove legacy draft")
    }
}

internal data class CommandDraftWrite(
    val draft: CommandDraft,
    val revision: Long,
    val replaceText: Boolean,
)

/** Lifecycle events never acquire a new version; only text edits can supersede another editor. */
internal class CommandDraftEdits {
    private var revision = 0L

    @Synchronized fun claimText(): Long = ++revision

    @Synchronized fun currentRevision(): Long = revision

    @Synchronized
    fun merge(current: CommandDraft?, write: CommandDraftWrite): CommandDraft? {
        if (write.revision != revision) return current
        if (!write.replaceText && current?.text != write.draft.text) return current
        return write.draft.clamped()
    }
}

private val commandDraftEdits = CommandDraftEdits()

internal interface CommandDraftRepository {
    val edits: CommandDraftEdits

    suspend fun read(): CommandDraft?

    suspend fun save(write: CommandDraftWrite)
}

internal class CommandDraftStore(context: Context) : CommandDraftRepository {
    override val edits
        get() = commandDraftEdits

    private val store = context.applicationContext.commandDraftDataStore

    override suspend fun read(): CommandDraft? = store.data.first()

    override suspend fun save(write: CommandDraftWrite) {
        store.updateData { current -> edits.merge(current, write) }
    }
}
