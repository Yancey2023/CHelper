package yancey.chelper.data.local.command

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UTFDataFormatException
import kotlinx.coroutines.flow.first
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftEdits
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandDraftWrite

/** Length-prefixed UTF-8, without DataOutputStream.writeUTF's 65535-byte limit. */
object CommandDraftSerializer : Serializer<CommandDraft?> {
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

class LegacyCommandDraftMigration(private val legacy: File, private val destination: File? = null) :
    DataMigration<CommandDraft?> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: CommandDraft?) =
        currentData == null && legacy.isFile && destination?.exists() != true

    override suspend fun migrate(currentData: CommandDraft?): CommandDraft? =
        try {
            DataInputStream(legacy.inputStream().buffered()).use {
                CommandDraft(it.readUTF(), it.readInt(), it.readInt()).clamped().also {
                    imported = true
                }
            }
        } catch (_: EOFException) {
            currentData
        } catch (_: UTFDataFormatException) {
            currentData
        }

    override suspend fun cleanUp() {
        if (!imported) return
        if (legacy.exists() && !legacy.delete()) throw IOException("Unable to remove legacy draft")
    }
}

class CommandDraftStore(private val store: DataStore<CommandDraft?>) : CommandDraftRepository {
    override val edits = CommandDraftEdits()

    override suspend fun read(): CommandDraft? = store.data.first()

    override suspend fun save(write: CommandDraftWrite) {
        store.updateData { current -> edits.merge(current, write) }
    }
}
