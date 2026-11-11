package yancey.chelper.ui.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import yancey.chelper.data.localBody as documentBody
import yancey.chelper.data.toFullLocalMcd as documentMcd
import yancey.chelper.data.usesLocalMcdV2 as documentUsesV2
import yancey.chelper.network.library.data.LibraryFunction

enum class LocalLibrarySort(val label: String) {
    DEFAULT("默认顺序"),
    NEWEST("最近添加"),
    OLDEST("最早添加"),
    NAME_ASC("名称 A-Z"),
    NAME_DESC("名称 Z-A"),
}

data class LocalLibraryEntry(
    val storageIndex: Int,
    val library: LibraryFunction,
) {
    val localEntryId: String
        get() = requireNotNull(library.localEntryId)
}

private val localLibraryJson = Json { ignoreUnknownKeys = true }

fun filterAndSortLocalLibraries(
    libraries: List<LibraryFunction>,
    query: String,
    sort: LocalLibrarySort,
): List<LocalLibraryEntry> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val entries =
        libraries
            .mapIndexed { index, library -> LocalLibraryEntry(index, library) }
            .filter { entry -> terms.all { term -> entry.library.matchesLocalQuery(term) } }

    return when (sort) {
        LocalLibrarySort.DEFAULT -> entries
        LocalLibrarySort.NEWEST -> entries.sortedByDescending { it.storageIndex }
        LocalLibrarySort.OLDEST -> entries.sortedBy { it.storageIndex }
        LocalLibrarySort.NAME_ASC -> entries.sortedWith(localLibraryNameComparator())
        LocalLibrarySort.NAME_DESC -> entries.sortedWith(localLibraryNameComparator().reversed())
    }
}

private fun LibraryFunction.matchesLocalQuery(term: String): Boolean =
    sequenceOf(
            name,
            note,
            version,
            author?.name,
            content,
        )
        .filterNotNull()
        .any { it.contains(term, ignoreCase = true) } ||
        tags.orEmpty().any { it.contains(term, ignoreCase = true) }

private fun localLibraryNameComparator(): Comparator<LocalLibraryEntry> =
    Comparator { first, second ->
        val nameOrder =
            String.CASE_INSENSITIVE_ORDER.compare(
                first.library.name.orEmpty(),
                second.library.name.orEmpty(),
            )
        if (nameOrder != 0) {
            nameOrder
        } else {
            compareValuesBy(first, second, { it.storageIndex }, { it.localEntryId })
        }
    }

fun decodeLocalLibraryImport(raw: String): List<LibraryFunction> {
    val decoded =
        when (val root = localLibraryJson.parseToJsonElement(raw)) {
            is JsonArray -> root.map { localLibraryJson.decodeFromJsonElement<LibraryFunction>(it) }
            is JsonObject -> listOf(localLibraryJson.decodeFromJsonElement<LibraryFunction>(root))
            else -> error("命令库备份必须是 JSON 对象或数组")
        }
    return decoded.map { library ->
        // A backup restores local documents, never an instruction to replay cloud writes.
        library.copy(
            id = null,
            uuid = null,
            autoSync = false,
            hasUnsyncedChanges = null,
            localUnsynced = false,
            localSyncOwnerId = null,
            localSyncRevision = null,
            localSyncError = null,
            localEntryId = null,
            localIsV2 = library.usesLocalMcdV2(),
        )
    }
}

fun LibraryFunction.toLocalDuplicate(): LibraryFunction {
    val isV2 = usesLocalMcdV2()
    return copy(
        id = null,
        uuid = null,
        name = "${name?.takeIf(String::isNotBlank) ?: "未命名"} - 副本",
        createdAt = null,
        preview = null,
        likeCount = null,
        isLiked = null,
        isFavorited = null,
        hasPublicVersion = null,
        isPublish = null,
        isOwner = null,
        chainData = null,
        autoSync = false,
        hasUnsyncedChanges = null,
        localUnsynced = false,
        localSyncOwnerId = null,
        localSyncRevision = null,
        localSyncError = null,
        localEntryId = null,
        localIsV2 = isV2,
    )
}

fun LibraryFunction.toLocalImportedCopy(): LibraryFunction =
    copy(
        id = null,
        uuid = null,
        content = localBody(),
        createdAt = null,
        preview = null,
        likeCount = null,
        isLiked = null,
        isFavorited = null,
        hasPublicVersion = null,
        isPublish = null,
        isOwner = null,
        chainData = null,
        autoSync = false,
        hasUnsyncedChanges = null,
        localUnsynced = false,
        localSyncOwnerId = null,
        localSyncRevision = null,
        localSyncError = null,
        localEntryId = null,
        localIsV2 = usesLocalMcdV2(),
    )

fun LibraryFunction.hasSameLocalContent(other: LibraryFunction): Boolean =
    name.orEmpty().trim() == other.name.orEmpty().trim() &&
        localBody().trim() == other.localBody().trim()

fun LibraryFunction.localBody(): String = documentBody()

fun LibraryFunction.usesLocalMcdV2(): Boolean = documentUsesV2()

fun LibraryFunction.toFullLocalMcd(): String = documentMcd()
