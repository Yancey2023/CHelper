package yancey.chelper.desktop.sync

import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.LibrarySyncOutcome
import yancey.chelper.domain.library.sync.LibrarySyncProcessor
import yancey.chelper.domain.library.sync.LibrarySyncStore
import yancey.chelper.domain.library.sync.SyncedLibraryIdentity

/** WorkManager is Android-only; Windows schedules the original persisted sync processor. */
internal class DesktopLibrarySync(
    private val local: LocalCommandLabDataStore,
    private val network: NetworkServices,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val synchronize: suspend (LibraryFunction, Int) -> SyncedLibraryIdentity =
        network.sync::synchronize,
) : Closeable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val signals = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            combine(network.login.accountId, local.localLibraryFunctions()) { account, entries ->
                    account to
                        entries.filter {
                            it.autoSync == true &&
                                it.localUnsynced &&
                                it.localSyncOwnerId == account &&
                                it.localSyncRevision != null
                        }
                }
                .retryWhen { cause, _ ->
                    if (cause !is IOException) return@retryWhen false
                    Log.w("LibrarySync", "Unable to observe local sync queue", cause)
                    delay(30_000)
                    true
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
                        network.cache.invalidateLibraries()
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
                LibrarySyncProcessor(store, { network.login.accountId.value }, synchronize)
            for (signal in signals) {
                val owner = network.login.accountId.value ?: continue
                var retry = false
                try {
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
                                if (processor.run(it, owner) == LibrarySyncOutcome.RETRY)
                                    retry = true
                            }
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("LibrarySync", "Unable to read or persist pending sync", error)
                    retry = true
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
