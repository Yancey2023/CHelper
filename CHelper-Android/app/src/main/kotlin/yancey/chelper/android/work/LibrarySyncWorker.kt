package yancey.chelper.android.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import yancey.chelper.android.startupState
import yancey.chelper.data.LibrarySyncOutcome
import yancey.chelper.data.LibrarySyncProcessor
import yancey.chelper.data.LibrarySyncStore
import yancey.chelper.data.LocalCommandLabDataStore
import yancey.chelper.data.NetworkLibrarySync
import yancey.chelper.data.SyncedLibraryIdentity
import yancey.chelper.network.library.util.CloudLibraryCache
import yancey.chelper.network.library.util.LoginUtil

class LibrarySyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        try {
            synchronize()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LibrarySync", "Unable to read or persist pending sync", e)
            Result.retry()
        }

    private suspend fun synchronize(): Result {
        applicationContext.startupState.settings.first { it != null }
        val entryId = inputData.getString("entry") ?: return Result.failure()
        val ownerId = inputData.getInt("owner", -1).takeIf { it > 0 } ?: return Result.failure()
        val local = LocalCommandLabDataStore(applicationContext)
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
                    CloudLibraryCache.invalidateLibraries()
                }

                override suspend fun fail(localEntryId: String, revision: String, message: String) =
                    local.failQueuedSync(localEntryId, revision, message)
            }
        return when (
            LibrarySyncProcessor(
                    store,
                    { LoginUtil.accountId.value },
                    NetworkLibrarySync::synchronize,
                )
                .run(entryId, ownerId)
        ) {
            LibrarySyncOutcome.COMPLETE,
            LibrarySyncOutcome.ACCOUNT_CHANGED -> Result.success()
            LibrarySyncOutcome.RETRY -> Result.retry()
        }
    }
}

object LibrarySyncScheduler {
    private val enqueueMutex = Mutex()
    private val enqueuedRevisions = mutableMapOf<String, String>()

    suspend fun enqueue(context: Context, ownerId: Int, entryId: String, revision: String) =
        withContext(Dispatchers.IO) {
            enqueueMutex.withLock {
                val name = "library-sync:$ownerId:$entryId"
                if (enqueuedRevisions[name] == revision) return@withLock
                val work =
                    OneTimeWorkRequestBuilder<LibrarySyncWorker>()
                        .setInputData(workDataOf("owner" to ownerId, "entry" to entryId))
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()
                // Append rather than cancelling an in-flight upload; each worker reads the latest
                // revision.
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
                    .result
                    .get()
                enqueuedRevisions[name] = revision
            }
            Unit
        }

    fun observe(context: Context, scope: CoroutineScope) {
        val application = context.applicationContext
        scope.launch {
            application.startupState.settings.first { it != null }
            LoginUtil.accountId.collectLatest { ownerId ->
                enqueueMutex.withLock { enqueuedRevisions.clear() }
                if (ownerId == null) return@collectLatest
                LocalCommandLabDataStore(application)
                    .localLibraryFunctions()
                    .retryWhen { cause, _ ->
                        if (cause !is IOException) return@retryWhen false
                        Log.w("LibrarySync", "Unable to read local sync queue", cause)
                        delay(5000)
                        true
                    }
                    .map { libraries ->
                        libraries
                            .filter {
                                it.autoSync == true &&
                                    it.localUnsynced &&
                                    it.localSyncOwnerId == ownerId &&
                                    it.localSyncRevision != null
                            }
                            .mapNotNull { library ->
                                val id = library.localEntryId
                                val revision = library.localSyncRevision
                                if (id == null || revision == null) null else id to revision
                            }
                            .toSet()
                    }
                    .distinctUntilChanged()
                    .collect { pending ->
                        pending.forEach { (entryId, revision) ->
                            while (LoginUtil.accountId.value == ownerId) {
                                try {
                                    enqueue(application, ownerId, entryId, revision)
                                    break
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w("LibrarySync", "Unable to schedule pending sync", e)
                                    delay(5000)
                                }
                            }
                        }
                    }
            }
        }
    }
}
