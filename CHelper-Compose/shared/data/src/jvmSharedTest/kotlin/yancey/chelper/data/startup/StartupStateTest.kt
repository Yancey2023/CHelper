package yancey.chelper.data.startup

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.domain.settings.Settings

class StartupStateTest {
    @Test
    fun `first frame waits for settings and background then follows theme changes`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val settings = MutableStateFlow(Settings(themeId = "MODE_NIGHT_YES"))
            val background = CompletableDeferred<Unit>()
            val startup = StartupState(settings, { background.await() }, scope)
            assertNull(startup.settings.value)
            background.complete(Unit)
            assertEquals("MODE_NIGHT_YES", startup.settings.value?.themeId)
            settings.value = Settings(themeId = "MODE_NIGHT_NO")
            assertEquals("MODE_NIGHT_NO", startup.settings.value?.themeId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `settings I O failure still releases the startup gate`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val startup = StartupState(flow { throw IOException("unreadable") }, {}, scope)
            assertEquals(Settings(), startup.settings.value)
        } finally {
            scope.cancel()
        }
    }
}
