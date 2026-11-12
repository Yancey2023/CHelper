package yancey.chelper.data.library.document

import kotlinx.serialization.json.Json
import yancey.chelper.data.library.decodeLocalLibraryImport
import yancey.chelper.domain.library.LibraryAuthor
import yancey.chelper.domain.library.LibraryEntry
import yancey.chelper.domain.library.LibraryFunction

fun LibraryFunction.toLibraryEntry() =
    LibraryEntry(
        localId = localEntryId,
        remoteId = id,
        name = name.orEmpty(),
        body = localBody(),
        note = note.orEmpty(),
        version = version.orEmpty(),
        tags = tags.orEmpty().toList(),
        isV2 = usesLocalMcdV2(),
        author = author?.let { LibraryAuthor(it.name, it.tier, it.id) },
        likeCount = likeCount,
        isFavorited = isFavorited,
    )

/** Legacy on-disk/wire schema is retained to read existing Android and desktop backups. */
fun LibraryEntry.toLibraryFunction() =
    LibraryFunction(
        localEntryId = localId,
        name = name,
        content = body,
        note = note,
        version = version,
        tags = tags,
        localIsV2 = isV2,
        author = author?.let { yancey.chelper.domain.library.AuthorInfo(it.id, it.name, it.tier) },
    )

object McdLibraryDocuments {
    fun toMcd(library: LibraryEntry): String = library.toLibraryFunction().toFullLocalMcd()

    fun decodeBackup(raw: String): List<LibraryEntry> =
        decodeLocalLibraryImport(raw).map {
            it.toLibraryEntry().copy(localId = null, remoteId = null)
        }

    fun encodeBackup(libraries: List<LibraryEntry>): String =
        Json.encodeToString(libraries.map { it.toLibraryFunction() })
}
