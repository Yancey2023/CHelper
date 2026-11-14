package yancey.chelper.ui.feature.settings

import kotlin.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.app.AppSession
import yancey.chelper.ui.app.PresentationTest

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRecoveryTest : PresentationTest() {
    @Test
    fun failedEditsStayOrderedAndRetryAfterLeavingThePage() =
        runTest(dispatcher) {
            val stored = MutableStateFlow(Settings())
            var fail = true
            val repository =
                object : AppSettingsRepository {
                    override val state = stored

                    override suspend fun update(transform: (Settings) -> Settings) {
                        check(!fail) { "disk unavailable" }
                        stored.value = transform(stored.value)
                    }
                }
            val model = own(SettingsViewModel(repository))
            val session = AppSession()
            session.register(repository, model::awaitSaved)
            model.update { it.copy(themeId = "MODE_NIGHT_YES") }
            runCurrent()
            assertNotNull(model.error.value)
            model.update { it.copy(isCrowded = true) }
            runCurrent()
            clearViewModels()
            runCurrent()
            assertTrue(runCatching { session.flush() }.isFailure)
            fail = false
            session.flush()
            session.flush()
            assertEquals("MODE_NIGHT_YES", stored.value.themeId)
            assertEquals(true, stored.value.isCrowded)
            assertNull(model.error.value)
        }

    @Test
    fun retryResubscribesAfterAnInitialReadFailure() =
        runTest(dispatcher) {
            val stored = MutableStateFlow(Settings(themeId = "MODE_NIGHT_YES"))
            var fail = true
            val repository =
                object : AppSettingsRepository {
                    override val state = flow {
                        check(!fail) { "read unavailable" }
                        emitAll(stored)
                    }

                    override suspend fun update(transform: (Settings) -> Settings) {
                        stored.value = transform(stored.value)
                    }
                }
            val model = own(SettingsViewModel(repository))
            runCurrent()
            assertNotNull(model.error.value)
            fail = false
            model.retry()
            runCurrent()
            assertEquals("MODE_NIGHT_YES", model.state.value.themeId)
            assertNull(model.error.value)
            stored.value = stored.value.copy(isCrowded = true)
            runCurrent()
            assertEquals(true, model.state.value.isCrowded)
        }

    @Test
    fun closeCanRecoverAnInitialReadFailureAfterThePageIsCleared() =
        runTest(dispatcher) {
            val stored = Settings(themeId = "MODE_NIGHT_YES")
            var fail = true
            val model =
                own(
                    SettingsViewModel(
                        object : AppSettingsRepository {
                            override val state = flow {
                                check(!fail) { "read unavailable" }
                                emit(stored)
                            }

                            override suspend fun update(transform: (Settings) -> Settings) = Unit
                        }
                    )
                )
            runCurrent()
            clearViewModels()
            runCurrent()
            fail = false
            model.awaitSaved()
            assertEquals("MODE_NIGHT_YES", model.state.value.themeId)
            assertNull(model.error.value)
        }
}
