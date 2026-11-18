package yancey.chelper.ui.app

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.feature.settings.SettingsViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAppStateTest : PresentationTest() {
    @Test
    fun leavingSettingsDoesNotCancelAnAlreadyQueuedWrite() =
        runTest(dispatcher) {
            val stored = MutableStateFlow(Settings())
            val repo =
                object : AppSettingsRepository {
                    override val state = stored

                    override suspend fun update(transform: (Settings) -> Settings) {
                        delay(100)
                        stored.value = transform(stored.value)
                    }
                }
            val model = own(SettingsViewModel(repo))
            model.update { it.copy(themeId = "MODE_NIGHT_YES") }
            clearViewModels()
            model.awaitSaved()
            assertEquals("MODE_NIGHT_YES", stored.value.themeId)
        }

    @Test
    fun windowCloseFlushesAllRegisteredOwnersEvenAfterCompositionDisposes() =
        runTest(dispatcher) {
            val session = AppSession()
            var flushed = false
            val remove =
                session.register("editor") {
                    delay(100)
                    flushed = true
                }
            val close = session.prepareClose()
            remove()
            close()
            assertTrue(flushed)
        }

    @Test
    fun settingsEditsAreMergedInOrderAndCanBeFlushedBeforeExit() =
        runTest(dispatcher) {
            val stored = MutableStateFlow(Settings())
            val repo =
                object : AppSettingsRepository {
                    override val state = stored

                    override suspend fun update(transform: (Settings) -> Settings) {
                        delay(50)
                        stored.value = transform(stored.value)
                    }
                }
            val model = own(SettingsViewModel(repo))
            runCurrent()
            model.update { it.copy(themeId = "MODE_NIGHT_YES") }
            model.update { it.copy(isCrowded = true) }
            model.awaitSaved()
            assertEquals("MODE_NIGHT_YES", stored.value.themeId)
            assertEquals(true, stored.value.isCrowded)
        }
}
