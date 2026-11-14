/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.feature.rawtext

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import yancey.chelper.domain.rawtext.RawtextDocumentSnapshot
import yancey.chelper.domain.rawtext.RawtextEditToken
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.domain.rawtext.RawtextRepository

internal data class RawtextPersistenceSnapshot(
    val copyFormat: String,
    val autoSavePrompted: Boolean,
    val autoSaveEnabled: Boolean,
    val document: RawtextDocumentSnapshot,
) {
    fun encode() =
        RawtextPreferences(
            copyFormat = copyFormat,
            autoSavePrompted = autoSavePrompted,
            autoSaveEnabled = autoSaveEnabled,
            draft = if (autoSaveEnabled) Json.encodeToString(document.segments) else null,
            mockSettings = Json.encodeToString(document.mock),
        )
}

/** Serializes immutable drafts off the UI thread and commits all preferences together. */
internal class RawtextAutosaver(
    private val scope: CoroutineScope,
    private val repository: RawtextRepository,
    private val worker: CoroutineDispatcher,
    private val delayMillis: Long,
    private val onError: (String?) -> Unit,
) {
    private val writes = Mutex()
    private var revision = 0L
    private var job: Job? = null
    private var finished = false
    private var savedToken: RawtextEditToken? = null
    private var activeToken: RawtextEditToken? = null
    private var immediateRequest = false

    fun submit(
        snapshot: RawtextPersistenceSnapshot,
        token: RawtextEditToken,
        immediately: Boolean = false,
    ) {
        if (finished || token.revisions.isEmpty() || token == savedToken) return
        if (token == activeToken && immediateRequest && job?.isActive == true) return
        activeToken = token
        immediateRequest = immediately
        val request = ++revision
        job?.cancel()
        job =
            scope.launch {
                try {
                    if (!immediately) delay(delayMillis)
                    save(snapshot, token, request)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (revision == request) onError("草稿保存失败，请稍后重试")
                }
            }
    }

    suspend fun awaitIdle() {
        job?.join()
    }

    private suspend fun save(
        snapshot: RawtextPersistenceSnapshot,
        token: RawtextEditToken,
        request: Long,
    ) {
        val preferences = withContext(worker) { snapshot.encode() }
        writes.withLock {
            currentCoroutineContext().ensureActive()
            if (revision == request) repository.savePreferences(preferences, token)
        }
        if (revision == request) {
            savedToken = token
            onError(null)
        }
    }

    /** The closing caller can retry after the editor's private scope has been released. */
    suspend fun flush(snapshot: RawtextPersistenceSnapshot, token: RawtextEditToken) {
        job?.cancelAndJoin()
        if (token.revisions.isEmpty() || token == savedToken) return
        try {
            save(snapshot, token, ++revision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onError("草稿保存失败，请稍后重试")
            throw failure
        }
    }

    // The final flush outlives ViewModel cancellation and then releases its private scope.
    fun finish() {
        if (finished) return
        finished = true
        job?.invokeOnCompletion { scope.cancel() } ?: scope.cancel()
    }
}
