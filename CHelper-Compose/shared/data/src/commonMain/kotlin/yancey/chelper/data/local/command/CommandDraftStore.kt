package yancey.chelper.data.local.command

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.first
import okio.BufferedSink
import okio.BufferedSource
import okio.EOFException
import okio.FileSystem
import okio.IOException
import okio.Path
import yancey.chelper.data.local.isFile
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftEdits
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandDraftWrite

/** The existing big-endian length/UTF-8/selection format, including the null sentinel. */
object CommandDraftSerializer : OkioSerializer<CommandDraft?> {
    override val defaultValue: CommandDraft? = null

    override suspend fun readFrom(source: BufferedSource): CommandDraft? =
        try {
            val size = source.readInt()
            if (size == -1) null
            else {
                if (size < 0) throw IOException("Invalid draft length")
                val bytes = source.readByteArray()
                if (size > bytes.size || bytes.size - size != 8)
                    throw IOException("Truncated draft")
                val text = bytes.decodeToString(0, size)
                fun intAt(offset: Int): Int =
                    (0..3).fold(0) { n, i -> (n shl 8) or (bytes[offset + i].toInt() and 255) }
                CommandDraft(text, intAt(size), intAt(size + 4)).clamped()
            }
        } catch (e: IOException) {
            throw CorruptionException("Unable to read command draft", e)
        }

    override suspend fun writeTo(t: CommandDraft?, sink: BufferedSink) {
        if (t == null) sink.writeInt(-1)
        else {
            val bytes = t.text.encodeToByteArray()
            sink.writeInt(bytes.size)
            sink.write(bytes)
            sink.writeInt(t.start)
            sink.writeInt(t.end)
        }
    }
}

/** Java's historical modified UTF-8 encodes UTF-16 code units, including NUL and surrogates. */
private class LegacyUtfFormatException(message: String) : IOException(message)

internal fun BufferedSource.readLegacyUtf(): String {
    val bytes = readByteArray(readShort().toInt().and(65535).toLong())
    val chars = StringBuilder()
    var i = 0
    fun continuation(): Int {
        if (i >= bytes.size) throw LegacyUtfFormatException("Truncated modified UTF-8")
        val b = bytes[i++].toInt() and 255
        if (b and 192 != 128) throw LegacyUtfFormatException("Invalid modified UTF-8")
        return b and 63
    }
    while (i < bytes.size) {
        val b = bytes[i++].toInt() and 255
        val code =
            when {
                b and 128 == 0 -> b
                b and 224 == 192 -> ((b and 31) shl 6) or continuation()
                b and 240 == 224 -> ((b and 15) shl 12) or (continuation() shl 6) or continuation()
                else -> throw LegacyUtfFormatException("Invalid modified UTF-8")
            }
        chars.append(code.toChar())
    }
    return chars.toString()
}

class LegacyCommandDraftMigration(
    private val legacy: Path,
    private val destination: Path? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : DataMigration<CommandDraft?> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: CommandDraft?) =
        currentData == null &&
            fileSystem.isFile(legacy) &&
            destination?.let(fileSystem::exists) != true

    override suspend fun migrate(currentData: CommandDraft?): CommandDraft? =
        try {
            fileSystem.read(legacy) {
                CommandDraft(readLegacyUtf(), readInt(), readInt()).clamped().also {
                    imported = true
                }
            }
        } catch (_: EOFException) {
            currentData
        } catch (_: LegacyUtfFormatException) {
            currentData
        }

    override suspend fun cleanUp() {
        if (imported) fileSystem.delete(legacy, mustExist = false)
    }
}

class CommandDraftStore(private val store: DataStore<CommandDraft?>) : CommandDraftRepository {
    override val edits = CommandDraftEdits()

    override suspend fun read(): CommandDraft? = store.data.first()

    override suspend fun save(write: CommandDraftWrite) {
        store.updateData { current -> edits.merge(current, write) }
    }
}
