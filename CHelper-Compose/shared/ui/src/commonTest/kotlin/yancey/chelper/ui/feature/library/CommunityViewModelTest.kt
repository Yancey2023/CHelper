package yancey.chelper.ui.feature.library

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.feature.library.account.AccountViewModel
import yancey.chelper.ui.feature.library.browse.PublicLibraryShowViewModel
import yancey.chelper.ui.feature.library.mcd.MCDRenderRow
import yancey.chelper.ui.feature.library.messages.MessageViewModel
import yancey.chelper.ui.feature.library.profile.UserProfileViewModel
import yancey.chelper.ui.feature.library.upload.LibraryUploadViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {
    private class Preferences(override val state: Flow<Settings> = flowOf(Settings())) :
        AppSettingsRepository {
        override suspend fun update(transform: (Settings) -> Settings) = Unit
    }

    private class Accounts : AccountRepository {
        override val state = MutableStateFlow(AccountState(AccountUser(id = 1, nickname = "old")))
        override val accountId = MutableStateFlow<Int?>(1)
        var profileCall: suspend (Int) -> RepositoryResult<UserProfile> = {
            RepositoryResult(UserProfile(id = it))
        }

        override suspend fun login(account: String, password: String) =
            Result.success(requireNotNull(currentUser))

        override fun logout() {
            state.value = AccountState(revision = state.value.revision + 1)
            accountId.value = null
        }

        override suspend fun sendVerification(account: String, verification: String) =
            RepositoryResult(Unit)

        override suspend fun register(details: Registration) = RepositoryResult(Unit)

        override suspend fun profile(id: Int, forceRefresh: Boolean) = profileCall(id)

        override suspend fun updateProfile(
            id: Int,
            changes: ProfileChanges,
        ): RepositoryResult<Unit> {
            state.value = state.value.copy(user = currentUser?.copy(nickname = changes.nickname))
            return RepositoryResult(Unit)
        }

        override suspend fun uploadAvatar(bytes: ByteArray, mimeType: String) =
            RepositoryResult(AvatarUpload("new"))
    }

    private class Content : LibraryContentRepository {
        override fun invalidate() = Unit

        var favoriteCall: suspend (Int) -> RepositoryResult<FavoriteState> = {
            RepositoryResult(FavoriteState(true))
        }
        var detailCall: suspend (Int) -> RepositoryResult<LibraryFunction> = {
            RepositoryResult(LibraryFunction(id = it))
        }
        var uploadCall: suspend (String) -> RepositoryResult<LibraryUpload> = {
            RepositoryResult(LibraryUpload("new"))
        }

        override suspend fun detail(id: Int, private: Boolean) = detailCall(id)

        override suspend fun mine(pageNum: Int, pageSize: Int, forceRefresh: Boolean) =
            RepositoryResult(LibraryPage(emptyList(), pageNum, pageSize, 0))

        override suspend fun byAuthor(authorId: Int, pageNum: Int, pageSize: Int) =
            RepositoryResult(
                LibraryPage(listOf(LibraryFunction(id = authorId)), pageNum, pageSize, 1)
            )

        override suspend fun favorites() = RepositoryResult(LibraryPage())

        override suspend fun quota(forceRefresh: Boolean) = RepositoryResult(LibraryQuota())

        override suspend fun upload(content: String) = uploadCall(content)

        override suspend fun update(id: Int, changes: LibraryChanges) = RepositoryResult(Unit)

        override suspend fun delete(id: Int) = RepositoryResult(Unit)

        override suspend fun publish(id: Int, verification: String) =
            RepositoryResult(Publication())

        override suspend fun syncPublic(id: Int) = RepositoryResult(Unit)

        override suspend fun like(id: Int) = RepositoryResult(LibraryReaction())

        override suspend fun favorite(id: Int) = favoriteCall(id)

        override suspend fun share(id: Int, importToLocal: Boolean) =
            RepositoryResult(LibraryShare())

        override suspend fun report(report: AbuseReport) = RepositoryResult(Unit)

        override suspend fun leaderboard() = RepositoryResult(Leaderboard())
    }

    private class Inbox : MessageRepository {
        var loadCall: suspend (Int) -> RepositoryResult<MessagePage> = { page(it) }

        override suspend fun load(page: Int, pageSize: Int, unreadOnly: Boolean) = loadCall(page)

        override suspend fun unreadCount() = RepositoryResult(UnreadCount())

        override suspend fun markRead(id: Int) = RepositoryResult(Unit)

        override suspend fun delete(id: Int) = RepositoryResult(Unit)

        companion object {
            fun page(id: Int) =
                RepositoryResult(
                    MessagePage(listOf(InboxMessage(id = id)), total = 40, page = id, pageSize = 20)
                )
        }
    }

    private val links = BrowserLinkRepository { Result.success(it) }

    private fun testModels(block: suspend TestScope.(ViewModelStore) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            block(store)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedAppendRetriesTheSameMessagePage() = testModels { store ->
        val calls = mutableListOf<Int>()
        val inbox = Inbox()
        inbox.loadCall = { page ->
            calls += page
            if (calls.size == 2) error("offline") else Inbox.page(page)
        }
        val vm = MessageViewModel(inbox, links, Accounts())
        store.put("vm", vm)
        vm.loadMessages()
        runCurrent()
        vm.loadMore()
        runCurrent()
        assertEquals(listOf(1), vm.messages.map { it.id })
        assertEquals(1, vm.currentPage)
        vm.loadMore()
        runCurrent()
        assertEquals(listOf(1, 2, 2), calls)
        assertEquals(listOf(1, 2), vm.messages.map { it.id })
    }

    @Test
    fun refreshDiscardsAnObsoleteAppendEvenWhenItIgnoresCancellation() = testModels { store ->
        val late = CompletableDeferred<RepositoryResult<MessagePage>>()
        val inbox = Inbox()
        var count = 0
        inbox.loadCall = {
            if (++count == 2) withContext(NonCancellable) { late.await() }
            else Inbox.page(if (count == 1) 1 else 99)
        }
        val vm = MessageViewModel(inbox, links, Accounts())
        store.put("vm", vm)
        vm.loadMessages()
        runCurrent()
        vm.loadMore()
        runCurrent()
        vm.refresh()
        runCurrent()
        late.complete(Inbox.page(2))
        runCurrent()
        assertEquals(listOf(99), vm.messages.map { it.id })
        assertFalse(vm.isLoading)
    }

    @Test
    fun changingProfileTargetsCannotPublishThePreviousProfileOrLibraries() = testModels { store ->
        val late = CompletableDeferred<RepositoryResult<UserProfile>>()
        val accounts = Accounts()
        accounts.profileCall = {
            if (it == 1) withContext(NonCancellable) { late.await() }
            else RepositoryResult(UserProfile(id = it))
        }
        val vm = UserProfileViewModel(accounts, Content())
        store.put("vm", vm)
        vm.loadProfile(1)
        runCurrent()
        vm.loadProfile(2)
        runCurrent()
        late.complete(RepositoryResult(UserProfile(id = 1)))
        runCurrent()
        assertEquals(2, vm.userProfile?.id)
        assertEquals(listOf(2), vm.publicLibraries.map { it.id })
        assertFalse(vm.isLoading)
    }

    @Test
    fun repeatedFavoriteClicksSendOnlyOneMutation() = testModels { store ->
        val result = CompletableDeferred<RepositoryResult<FavoriteState>>()
        val content = Content()
        var calls = 0
        content.favoriteCall = {
            calls++
            result.await()
        }
        val vm = PublicLibraryShowViewModel(Accounts(), content, Preferences())
        store.put("vm", vm)
        vm.toggleFavorite(7)
        vm.toggleFavorite(7)
        runCurrent()
        assertEquals(1, calls)
        result.complete(RepositoryResult(FavoriteState(true)))
        runCurrent()
        assertTrue(vm.isFavorited)
    }

    @Test
    fun detailIsPublishedOnlyAfterRealPreferencesAndDocumentPreparation() = testModels { store ->
        val ready = CompletableDeferred<Unit>()
        val preferences =
            Preferences(
                flow {
                    ready.await()
                    emit(
                        Settings(
                            ambiguousLineDefault = "command",
                            isHideMetadataPreview = true,
                            isEnableMcdHighlight = false,
                        )
                    )
                }
            )
        val content =
            Content().apply {
                detailCall = {
                    RepositoryResult(
                        LibraryFunction(id = it, content = "@name=Example\n含糊文本\nsay hello")
                    )
                }
            }
        val vm =
            PublicLibraryShowViewModel(
                Accounts(),
                content,
                preferences,
                StandardTestDispatcher(testScheduler),
            )
        store.put("vm", vm)
        vm.ensureLoaded(7, false)
        runCurrent()
        assertTrue(vm.isLoading)
        assertNull(vm.library.id)
        assertNull(vm.preview)
        ready.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.isLoading)
        assertEquals(7, vm.library.id)
        val preview = assertNotNull(vm.preview)
        assertFalse(preview.highlight)
        assertFalse(preview.rows.any { it is MCDRenderRow.Meta })
        assertTrue(
            preview.rows.any { (it as? MCDRenderRow.Item)?.item == ChainItem.RawCommand("含糊文本") }
        )
    }

    @Test
    fun changingDetailDuringPreparationCannotPublishTheOldDocument() = testModels { store ->
        val ready = CompletableDeferred<Unit>()
        val preferences =
            Preferences(
                flow {
                    ready.await()
                    emit(Settings())
                }
            )
        val vm =
            PublicLibraryShowViewModel(
                Accounts(),
                Content(),
                preferences,
                StandardTestDispatcher(testScheduler),
            )
        store.put("vm", vm)
        vm.ensureLoaded(1, false)
        runCurrent()
        vm.ensureLoaded(2, false)
        runCurrent()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, vm.library.id)
        assertNotNull(vm.preview)
        assertFalse(vm.isLoading)
    }

    @Test
    fun changingPreviewPreferencesKeepsReadyContentUntilItsReplacementIsPrepared() =
        testModels { store ->
            val preferences = MutableStateFlow(Settings(isHideMetadataPreview = false))
            val content =
                Content().apply {
                    detailCall = {
                        RepositoryResult(
                            LibraryFunction(id = it, content = "@name=Example\nsay hello")
                        )
                    }
                }
            val vm =
                PublicLibraryShowViewModel(
                    Accounts(),
                    content,
                    Preferences(preferences),
                    StandardTestDispatcher(testScheduler),
                )
            store.put("vm", vm)
            vm.ensureLoaded(7, false)
            advanceUntilIdle()
            val first = assertNotNull(vm.preview)
            assertTrue(first.rows.any { it is MCDRenderRow.Meta })
            preferences.value = Settings(isHideMetadataPreview = true)
            assertSame(first, vm.preview)
            assertFalse(vm.isLoading)
            advanceUntilIdle()
            assertFalse(vm.isLoading)
            assertFalse(assertNotNull(vm.preview).rows.any { it is MCDRenderRow.Meta })
            assertEquals(7, vm.library.id)
        }

    @Test
    fun accountUpdatesReplaceSnapshotsAndKeepPreviousValuesUnchanged() = testModels { store ->
        val accounts = Accounts()
        val vm = AccountViewModel(accounts, Content())
        store.put("vm", vm)
        runCurrent()
        val old = requireNotNull(vm.currentUser)
        vm.updateProfile("new", null, null, null) {}
        runCurrent()
        assertEquals("old", old.nickname)
        assertEquals("new", vm.currentUser?.nickname)
        assertNotSame(old, vm.currentUser)
        assertFalse(vm.isUpdatingProfile)
    }

    @Test
    fun logoutClearsAlreadyDisplayedPrivateMessages() = testModels { store ->
        val accounts = Accounts()
        val vm = MessageViewModel(Inbox(), links, accounts)
        store.put("vm", vm)
        vm.loadMessages()
        runCurrent()
        assertTrue(vm.messages.isNotEmpty())
        accounts.logout()
        runCurrent()
        assertTrue(vm.messages.isEmpty())
        assertEquals(0, vm.unreadCount)
    }

    @Test
    fun repeatedUploadClicksUseOneCapturedDraftAndCancellationResetsBusyState() =
        testModels { store ->
            val content = Content()
            val entered = CompletableDeferred<Unit>()
            var uploads = 0
            var body = ""
            content.uploadCall = {
                uploads++
                body = it
                entered.complete(Unit)
                awaitCancellation()
            }
            val vm = LibraryUploadViewModel(content)
            store.put("vm", vm)
            vm.name.setTextAndPlaceCursorAtEnd("first")
            vm.commands.setTextAndPlaceCursorAtEnd("say first")
            var completed = false
            vm.upload(null) { completed = true }
            vm.upload(null) { completed = true }
            runCurrent()
            entered.await()
            vm.name.setTextAndPlaceCursorAtEnd("second")
            assertEquals(1, uploads)
            assertTrue(body.contains("@name=first"))
            assertTrue(vm.isLoading)
            store.clear()
            runCurrent()
            assertFalse(vm.isLoading)
            assertFalse(completed)
        }
}
