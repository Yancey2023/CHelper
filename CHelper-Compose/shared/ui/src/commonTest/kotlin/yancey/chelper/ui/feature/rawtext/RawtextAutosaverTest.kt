package yancey.chelper.ui.feature.rawtext

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import yancey.chelper.domain.rawtext.RawtextDocumentSnapshot
import yancey.chelper.domain.rawtext.RawtextEditToken
import yancey.chelper.domain.rawtext.RawtextEditTracker
import yancey.chelper.domain.rawtext.RawtextField
import yancey.chelper.domain.rawtext.RawtextMockSnapshot
import yancey.chelper.domain.rawtext.RawtextPreferences
import yancey.chelper.domain.rawtext.RawtextRepository
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot

internal class RecordingRawtextRepository : RawtextRepository {
    override val edits = RawtextEditTracker()
    var stored = RawtextPreferences(autoSavePrompted = true, autoSaveEnabled = true)
    var initial: Flow<RawtextPreferences> = flow { emit(stored) }
    val saved = mutableListOf<RawtextPreferences>()
    var write: suspend (RawtextPreferences) -> Unit = { saved.add(it) }
    private val writes = Mutex()

    override fun preferences() = initial.onEach { stored = it }

    override suspend fun savePreferences(preferences: RawtextPreferences, token: RawtextEditToken) {
        writes.withLock {
            val merged = edits.merge(stored, preferences, token)
            if (merged != stored) {
                write(merged)
                stored = merged
            }
        }
    }
}

class RawtextAutosaverTest {
    private fun snapshot(text: String, enabled: Boolean = true) =
        RawtextPersistenceSnapshot(
            "compressed",
            true,
            enabled,
            RawtextDocumentSnapshot(
                listOf(RawtextSegmentSnapshot("Text", text)),
                RawtextMockSnapshot(p = "Alex"),
            ),
        )

    private fun RawtextAutosaver.submitDraft(
        repo: RecordingRawtextRepository,
        snapshot: RawtextPersistenceSnapshot,
        immediately: Boolean = false,
    ): RawtextEditToken {
        val token = repo.edits.claim(*RawtextField.entries.toTypedArray())
        submit(snapshot, token, immediately)
        return token
    }

    @Test
    fun `burst is coalesced and final flush persists all preferences together`(): Unit = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = RecordingRawtextRepository()
        try {
            val saver =
                RawtextAutosaver(scope, repo, Dispatchers.Unconfined, 60_000) {
                    if (it != null) fail(it)
                }
            saver.submitDraft(repo, snapshot("a"))
            saver.submitDraft(repo, snapshot("ab"))
            saver.submitDraft(repo, snapshot("abc"))
            assertTrue(repo.saved.isEmpty())
            saver.submitDraft(repo, snapshot("abc"), immediately = true)
            saver.awaitIdle()
            assertEquals(1, repo.saved.size)
            assertEquals(snapshot("abc").encode(), repo.saved.single())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `late non-cancellable write is serialized before the latest write`(): Unit = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = RecordingRawtextRepository()
        val release = CompletableDeferred<Unit>()
        var first = true
        repo.write = { prefs ->
            if (first) {
                first = false
                withContext(NonCancellable) { release.await() }
            }
            repo.saved.add(prefs)
        }
        try {
            val saver = RawtextAutosaver(scope, repo, Dispatchers.Unconfined, 0) {}
            saver.submitDraft(repo, snapshot("old"), true)
            saver.submitDraft(repo, snapshot("latest"), true)
            assertTrue(repo.saved.isEmpty())
            release.complete(Unit)
            saver.awaitIdle()
            assertEquals(listOf(snapshot("old").encode(), snapshot("latest").encode()), repo.saved)
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun `disabling autosave clears the old draft atomically`(): Unit = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = RecordingRawtextRepository()
        try {
            val saver = RawtextAutosaver(scope, repo, Dispatchers.Unconfined, 60_000) {}
            saver.submitDraft(repo, snapshot("old"))
            saver.submitDraft(repo, snapshot("current", enabled = false), true)
            saver.awaitIdle()
            assertFalse(repo.saved.single().autoSaveEnabled)
            assertNull(repo.saved.single().draft)
            assertNotNull(repo.saved.single().mockSettings)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failure is visible and a later retry clears it`(): Unit = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = RecordingRawtextRepository()
        var error: String? = null
        try {
            val saver = RawtextAutosaver(scope, repo, Dispatchers.Unconfined, 0) { error = it }
            repo.write = { throw IllegalStateException("disk unavailable") }
            saver.submitDraft(repo, snapshot("draft"), true)
            saver.awaitIdle()
            assertNotNull(error)
            repo.write = { repo.saved.add(it) }
            saver.submitDraft(repo, snapshot("draft"), true)
            saver.awaitIdle()
            assertNull(error)
            assertEquals(1, repo.saved.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `finish seals pending save against late disposal flushes`(): Unit = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = RecordingRawtextRepository()
        val release = CompletableDeferred<Unit>()
        repo.write = { preferences ->
            release.await()
            repo.saved.add(preferences)
        }
        try {
            val saver = RawtextAutosaver(scope, repo, Dispatchers.Unconfined, 60_000) {}
            val token = saver.submitDraft(repo, snapshot("latest"), true)
            saver.finish()
            saver.submit(snapshot("latest"), token, true)
            saver.finish()
            release.complete(Unit)
            saver.awaitIdle()
            assertEquals(listOf(snapshot("latest").encode()), repo.saved)
        } finally {
            release.complete(Unit)
            scope.cancel()
        }
    }
}
