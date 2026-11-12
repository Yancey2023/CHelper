package yancey.chelper.data.startup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.settings.Settings

class StartupReadinessTest {
    @Test
    fun settingsAreOpenedAfterPlatformPreparationSoLegacyMigrationCanFinish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preparation = CompletableDeferred<Unit>()
        var prepared = false
        try {
            val settings =
                kotlinx.coroutines.flow.flow {
                    assertTrue(prepared)
                    emit(Settings(themeId = "MODE_NIGHT_YES"))
                }
            val startup =
                StartupState(settings, {}, scope) {
                    preparation.await()
                    prepared = true
                }
            assertNull(startup.settings.value)
            preparation.complete(Unit)
            assertEquals("MODE_NIGHT_YES", startup.settings.value?.themeId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun firstFrameAlsoWaitsForCredentialRestorationOnBothHosts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val credentials = CompletableDeferred<Unit>()
        try {
            val preferences = MutableStateFlow(Settings(themeId = "MODE_NIGHT_YES"))
            val startup = StartupState(preferences, {}, scope) { credentials.await() }
            assertNull(startup.settings.value)
            preferences.value = Settings(themeId = "MODE_NIGHT_NO")
            credentials.complete(Unit)
            assertEquals("MODE_NIGHT_NO", startup.settings.value?.themeId)
        } finally {
            scope.cancel()
        }
    }
}
