@file:OptIn(ExperimentalUuidApi::class)

package yancey.chelper.domain.library.sync

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import yancey.chelper.domain.library.LibraryFunction

fun LibraryFunction.prepareAutoSync(ownerId: Int?): LibraryFunction {
    val targetOwner = ownerId ?: localSyncOwnerId
    val enabled = autoSync == true && targetOwner != null
    val changedAccount = enabled && localSyncOwnerId != null && localSyncOwnerId != targetOwner
    return copy(
        id = if (changedAccount) null else id,
        uuid =
            if (changedAccount) Uuid.random().toString()
            else
                uuid?.takeIf(String::isNotBlank) ?: if (enabled) Uuid.random().toString() else null,
        localUnsynced = localUnsynced || enabled,
        localSyncOwnerId = if (enabled) targetOwner else localSyncOwnerId,
        localSyncRevision = if (enabled) Uuid.random().toString() else null,
        localSyncError = null,
    )
}

fun LibraryFunction.trackLocalChanges(previous: LibraryFunction): LibraryFunction {
    val changed =
        name != previous.name ||
            version != previous.version ||
            note != previous.note ||
            tags != previous.tags ||
            content != previous.content ||
            localIsV2 != previous.localIsV2
    return if (
        changed &&
            autoSync == true &&
            localSyncOwnerId != null &&
            localSyncRevision == previous.localSyncRevision
    )
        prepareAutoSync(null)
    else this
}

/** Editor forms change document fields, never a worker's newer cloud identity or queue revision. */
fun LibraryFunction.withDocumentEdits(edited: LibraryFunction): LibraryFunction {
    val document =
        copy(
            name = edited.name,
            version = edited.version,
            note = edited.note,
            tags = edited.tags,
            content = edited.content,
            localIsV2 = edited.localIsV2,
        )
    val changed =
        name != document.name ||
            version != document.version ||
            note != document.note ||
            tags != document.tags ||
            content != document.content ||
            localIsV2 != document.localIsV2
    return document
        .copy(localUnsynced = localUnsynced || (changed && !uuid.isNullOrBlank()))
        .trackLocalChanges(this)
}

data class SyncedLibraryIdentity(val id: Int?, val uuid: String?)

enum class LibrarySyncOutcome {
    COMPLETE,
    RETRY,
    ACCOUNT_CHANGED,
}

interface LibrarySyncStore {
    suspend fun read(localEntryId: String): LibraryFunction?

    suspend fun complete(
        localEntryId: String,
        ownerId: Int,
        revision: String,
        identity: SyncedLibraryIdentity,
    )

    suspend fun fail(localEntryId: String, revision: String, message: String)
}

fun LibraryFunction.afterQueuedSync(
    ownerId: Int,
    revision: String,
    identity: SyncedLibraryIdentity,
): LibraryFunction {
    if (localSyncOwnerId != ownerId) return this
    val sameRevision = localSyncRevision == revision
    return copy(
        id = identity.id ?: id,
        uuid = identity.uuid ?: uuid,
        localUnsynced = if (sameRevision) false else localUnsynced,
        localSyncRevision = if (sameRevision) null else localSyncRevision,
        localSyncError = if (sameRevision) null else localSyncError,
    )
}

/** The persisted revision is the authority; WorkManager input never contains a stale document. */
class LibrarySyncProcessor(
    private val store: LibrarySyncStore,
    private val currentAccount: () -> Int?,
    private val synchronize: suspend (LibraryFunction, Int) -> SyncedLibraryIdentity,
) {
    suspend fun run(localEntryId: String, ownerId: Int): LibrarySyncOutcome {
        if (currentAccount() != ownerId) return LibrarySyncOutcome.ACCOUNT_CHANGED
        val snapshot = store.read(localEntryId) ?: return LibrarySyncOutcome.COMPLETE
        val revision = snapshot.localSyncRevision ?: return LibrarySyncOutcome.COMPLETE
        if (
            snapshot.autoSync != true ||
                !snapshot.localUnsynced ||
                snapshot.localSyncOwnerId != ownerId
        )
            return LibrarySyncOutcome.COMPLETE
        return try {
            val identity = synchronize(snapshot, ownerId)
            if (currentAccount() != ownerId) return LibrarySyncOutcome.ACCOUNT_CHANGED
            store.complete(localEntryId, ownerId, revision, identity)
            LibrarySyncOutcome.COMPLETE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentAccount() != ownerId) return LibrarySyncOutcome.ACCOUNT_CHANGED
            store.fail(localEntryId, revision, e.message ?: "同步失败")
            LibrarySyncOutcome.RETRY
        }
    }
}
