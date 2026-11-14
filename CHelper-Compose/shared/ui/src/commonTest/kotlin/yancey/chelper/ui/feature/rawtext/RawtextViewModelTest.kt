package yancey.chelper.ui.feature.rawtext

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import yancey.chelper.domain.rawtext.RawtextPreferences

class RawtextViewModelTest {
    private val savedDraft = """[{"type":"Text","text":"saved draft"}]"""

    @Test
    fun `final save retries in the closing caller after a write failure`() = runBlocking {
        withEditor { vm, repo ->
            val owners = ViewModelStore()
            owners.put("editor", vm)
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("latest"))
            repo.write = { error("disk unavailable") }
            owners.clear()
            assertTrue(runCatching { vm.flushAndAwait() }.isFailure)
            repo.write = { repo.saved.add(it) }
            vm.flushAndAwait()
            vm.flushAndAwait()
            assertTrue(repo.stored.draft.orEmpty().contains("latest"))
            assertNull(vm.draftError)
            assertEquals(1, repo.saved.size)
        }
    }

    @Test
    fun `a failed initial read retries without restoring over edited text`() = runBlocking {
        val repo = RecordingRawtextRepository()
        repo.initial = flow { error("read unavailable") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owners = ViewModelStore()
        val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
        owners.put("editor", vm)
        try {
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("new text"))
            owners.clear()
            assertTrue(runCatching { vm.flushAndAwait() }.isFailure)
            assertTrue(repo.saved.isEmpty())
            repo.initial = flow { emit(repo.stored.copy(draft = savedDraft)) }
            vm.flushAndAwait()
            assertEquals("new text", vm.segments.first().textValue.text)
            assertTrue(repo.stored.draft.orEmpty().contains("new text"))
            assertNull(vm.draftError)
        } finally {
            owners.clear()
            scope.cancel()
        }
    }

    private suspend fun withEditor(
        block: suspend (RawtextViewModel, RecordingRawtextRepository) -> Unit
    ) {
        val repo = RecordingRawtextRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("editor", vm)
            block(vm, repo)
        } finally {
            store.clear()
            scope.cancel()
        }
    }

    @Test
    fun `keystrokes are debounced but flush and copying include newest input`() = runBlocking {
        withEditor { vm, repo ->
            val first = vm.segments.first().id
            vm.updateTextSegment(first, TextFieldValue("a"))
            vm.updateTextSegment(first, TextFieldValue("latest"))
            assertTrue(repo.saved.isEmpty())
            assertTrue(vm.copyJson(false).contains("latest"))
            vm.flushDraft()
            vm.awaitDraftSaved()
            assertEquals(1, repo.saved.size)
            assertTrue(repo.saved.single().draft!!.contains("latest"))
        }
    }

    @Test
    fun `late preferences cannot replace edits or changed options`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val prefs = CompletableDeferred<RawtextPreferences>()
        repo.initial = flow { emit(prefs.await()) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("editor", vm)
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("typed now"))
            vm.updateCopyFormat("compressed")
            vm.mockPlayerP = "New player"
            vm.persistMockSettings()
            prefs.complete(
                RawtextPreferences(
                    copyFormat = "formatted",
                    autoSavePrompted = true,
                    autoSaveEnabled = true,
                    draft = savedDraft,
                    mockSettings = """{"p":"Old player"}""",
                )
            )
            assertEquals("typed now", vm.segments.first().textValue.text)
            assertEquals("compressed", vm.copyFormat)
            assertEquals("New player", vm.mockPlayerP)
            vm.flushDraft()
            vm.awaitDraftSaved()
            assertTrue(repo.saved.last().draft!!.contains("typed now"))
            assertTrue(repo.saved.last().mockSettings!!.contains("New player"))
        } finally {
            store.clear()
            scope.cancel()
        }
    }

    @Test
    fun `old draft schema still restores and toggling off during load prevents restore`() =
        runBlocking {
            for (disable in listOf(false, true)) {
                val repo = RecordingRawtextRepository()
                val prefs = CompletableDeferred<RawtextPreferences>()
                repo.initial = flow { emit(prefs.await()) }
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
                val store = ViewModelStore()
                try {
                    val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
                    store.put("editor", vm)
                    if (disable) vm.setAutoSave(false)
                    prefs.complete(
                        RawtextPreferences(
                            autoSavePrompted = true,
                            autoSaveEnabled = true,
                            draft = savedDraft,
                        )
                    )
                    assertEquals(
                        if (disable) "" else "saved draft",
                        vm.segments.first().textValue.text,
                    )
                    assertEquals(!disable, vm.autoSaveEnabled)
                } finally {
                    store.clear()
                    scope.cancel()
                }
            }
        }

    @Test
    fun `a captured render snapshot does not track later editor mutations`() = runBlocking {
        withEditor { vm, _ ->
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("before"))
            val snapshot = vm.documentSnapshot()
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("after"))
            assertEquals("before", RawtextRenderer(snapshot).render().preview.text)
            assertEquals("after", RawtextRenderer(vm.documentSnapshot()).render().preview.text)
        }
    }

    @Test
    fun `closing before restore completes still saves new edits`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val prefs = CompletableDeferred<RawtextPreferences>()
        repo.initial = flow { emit(prefs.await()) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("editor", vm)
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("typed before close"))
            store.clear()
            scope.cancel()
            prefs.complete(
                RawtextPreferences(
                    autoSavePrompted = true,
                    autoSaveEnabled = true,
                    draft = savedDraft,
                )
            )
            vm.awaitDraftSaved()
            assertTrue(repo.saved.single().draft!!.contains("typed before close"))
        } finally {
            prefs.complete(RawtextPreferences())
            store.clear()
            scope.cancel()
        }
    }

    @Test
    fun `failed restore never overwrites an unread saved draft on close`() = runBlocking {
        val repo = RecordingRawtextRepository()
        repo.initial = flow { throw IllegalStateException("unavailable") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("editor", vm)
            assertNotNull(vm.draftError)
            store.clear()
            assertTrue(repo.saved.isEmpty())
        } finally {
            store.clear()
            scope.cancel()
        }
    }

    @Test
    fun `render finishing before the next snapshot notification cannot publish stale content`() =
        runBlocking {
            val worker = QueuedDispatcher()
            val repo = RecordingRawtextRepository()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val store = ViewModelStore()
            try {
                val vm = RawtextViewModel(repo, scope, worker, 60_000)
                store.put("editor", vm)
                worker.runAll()
                vm.updateTextSegment(vm.segments.first().id, TextFieldValue("obsolete"))
                Snapshot.sendApplyNotifications()
                vm.updateTextSegment(vm.segments.first().id, TextFieldValue("latest"))
                worker.runAll()
                assertFalse(vm.output.value.formattedJson.contains("obsolete"))
                Snapshot.sendApplyNotifications()
                worker.runAll()
                assertTrue(vm.output.value.formattedJson.contains("latest"))
            } finally {
                store.clear()
                worker.runAll()
                scope.cancel()
            }
        }

    @Test
    fun `unchanged older editor cannot overwrite a newer draft on hide or close`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val oldStore = ViewModelStore()
        val newStore = ViewModelStore()
        try {
            val a = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            oldStore.put("a", a)
            a.updateTextSegment(a.segments.first().id, TextFieldValue("old editor"))
            a.flushDraft()
            a.awaitDraftSaved()
            val b = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            newStore.put("b", b)
            b.updateTextSegment(b.segments.first().id, TextFieldValue("latest editor"))
            b.updateCopyFormat("compressed")
            b.mockPlayerP = "New player"
            b.flushDraft()
            b.awaitDraftSaved()
            val latest = repo.stored
            a.flushDraft()
            a.awaitDraftSaved()
            oldStore.clear()
            assertEquals(latest, repo.stored)
            assertTrue(repo.stored.draft!!.contains("latest editor"))
        } finally {
            oldStore.clear()
            newStore.clear()
            scope.cancel()
        }
    }

    @Test
    fun `delayed older edits cannot win over newer edits from another editor`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        val worker = QueuedDispatcher()
        try {
            val a = RawtextViewModel(repo, scope, worker, 60_000)
            val b = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("a", a)
            store.put("b", b)
            worker.runAll()
            a.updateTextSegment(a.segments.first().id, TextFieldValue("older pending edit"))
            a.flushDraft()
            b.updateTextSegment(b.segments.first().id, TextFieldValue("latest edit"))
            b.updateCopyFormat("compressed")
            b.flushDraft()
            b.awaitDraftSaved()
            worker.runAll()
            a.awaitDraftSaved()
            assertTrue(repo.stored.draft!!.contains("latest edit"))
            assertEquals("compressed", repo.stored.copyFormat)
            // A subsequent explicit edit is new intent; unrelated B settings must survive it.
            a.updateTextSegment(a.segments.first().id, TextFieldValue("new explicit edit"))
            a.flushDraft()
            worker.runAll()
            a.awaitDraftSaved()
            assertTrue(repo.stored.draft!!.contains("new explicit edit"))
            assertEquals("compressed", repo.stored.copyFormat)
        } finally {
            store.clear()
            worker.runAll()
            scope.cancel()
        }
    }

    @Test
    fun `clearing then disposing a ViewModel does not cancel its final save`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val worker = QueuedDispatcher()
        val store = ViewModelStore()
        try {
            val vm = RawtextViewModel(repo, scope, worker, 60_000)
            store.put("editor", vm)
            worker.runAll()
            vm.updateTextSegment(vm.segments.first().id, TextFieldValue("last input"))
            store.clear()
            vm.flushDraft()
            worker.runAll()
            vm.awaitDraftSaved()
            assertTrue(repo.stored.draft!!.contains("last input"))
            assertEquals(1, repo.saved.size)
        } finally {
            store.clear()
            worker.runAll()
            scope.cancel()
        }
    }

    @Test
    fun `moving a caret in an old editor does not create a new draft write`() = runBlocking {
        val repo = RecordingRawtextRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ViewModelStore()
        try {
            val a = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("a", a)
            a.updateTextSegment(a.segments.first().id, TextFieldValue("old text"))
            a.flushDraft()
            a.awaitDraftSaved()
            val b = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
            store.put("b", b)
            b.updateTextSegment(b.segments.first().id, TextFieldValue("new text"))
            b.flushDraft()
            b.awaitDraftSaved()
            a.updateTextSegment(a.segments.first().id, TextFieldValue("old text", TextRange(2)))
            a.flushDraft()
            a.awaitDraftSaved()
            assertTrue(repo.stored.draft!!.contains("new text"))
        } finally {
            store.clear()
            scope.cancel()
        }
    }

    @Test
    fun `mock edits returning to initial value still supersede delayed restoration`() =
        runBlocking {
            val repo = RecordingRawtextRepository()
            val preferences = CompletableDeferred<RawtextPreferences>()
            repo.initial = flow { emit(preferences.await()) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val store = ViewModelStore()
            try {
                val vm = RawtextViewModel(repo, scope, Dispatchers.Unconfined, 60_000)
                store.put("vm", vm)
                vm.mockPlayerP = "Changed"
                vm.mockPlayerP = "Steve"
                preferences.complete(
                    RawtextPreferences(
                        autoSaveEnabled = true,
                        mockSettings = """{"p":"From disk"}""",
                    )
                )
                assertEquals("Steve", vm.mockPlayerP)
                vm.flushDraft()
                vm.awaitDraftSaved()
                assertFalse(repo.stored.mockSettings!!.contains("From disk"))
            } finally {
                store.clear()
                scope.cancel()
            }
        }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }
}
