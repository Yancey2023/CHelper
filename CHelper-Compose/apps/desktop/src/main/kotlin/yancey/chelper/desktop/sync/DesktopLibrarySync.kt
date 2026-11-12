package yancey.chelper.desktop.sync

import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.library.sync.NetworkLibrarySync
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.domain.library.sync.LibrarySyncOutcome
import yancey.chelper.domain.library.sync.LibrarySyncProcessor
import yancey.chelper.domain.library.sync.LibrarySyncStore
import yancey.chelper.domain.library.sync.SyncedLibraryIdentity

/** WorkManager is Android-only; Windows schedules the original persisted sync processor. */
internal class DesktopLibrarySync(private val local: LocalCommandLabDataStore) : Closeable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val signals = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            combine(LoginUtil.accountId, local.localLibraryFunctions()) { account, entries ->
                    account to
                        entries.filter {
                            it.autoSync == true &&
                                it.localUnsynced &&
                                it.localSyncOwnerId == account &&
                                it.localSyncRevision != null
                        }
                }
                .distinctUntilChanged()
                .collect { signals.trySend(Unit) }
        }
        scope.launch {
            val store =
                object : LibrarySyncStore {
                    override suspend fun read(localEntryId: String) =
                        local.localLibraryFunction(localEntryId).first()

                    override suspend fun complete(
                        localEntryId: String,
                        ownerId: Int,
                        revision: String,
                        identity: SyncedLibraryIdentity,
                    ) {
                        local.completeQueuedSync(
                            localEntryId,
                            ownerId,
                            revision,
                            identity.id,
                            identity.uuid,
                        )
                    }

                    override suspend fun fail(
                        localEntryId: String,
                        revision: String,
                        message: String,
                    ) {
                        local.failQueuedSync(localEntryId, revision, message)
                    }
                }
            val processor =
                LibrarySyncProcessor(
                    store,
                    { LoginUtil.accountId.value },
                    NetworkLibrarySync::synchronize,
                )
            for (signal in signals) {
                val owner = LoginUtil.accountId.value ?: continue
                var retry = false
                local
                    .localLibraryFunctions()
                    .first()
                    .filter {
                        it.autoSync == true &&
                            it.localUnsynced &&
                            it.localSyncOwnerId == owner &&
                            it.localSyncRevision != null
                    }
                    .forEach { entry ->
                        entry.localEntryId?.let {
                            if (processor.run(it, owner) == LibrarySyncOutcome.RETRY) retry = true
                        }
                    }
                if (retry) {
                    delay(30_000)
                    signals.trySend(Unit)
                }
            }
        }
    }

    fun enqueue() {
        signals.trySend(Unit)
    }

    override fun close() {
        job.cancel()
        signals.close()
    }
}
