package yancey.chelper.data.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okio.IOException
import yancey.chelper.domain.settings.Settings

/** One process-wide read; null means the UI must wait before drawing its themed first frame. */
class StartupState(
    settingsFlow: Flow<Settings>,
    loadBackground: suspend () -> Unit,
    scope: CoroutineScope,
    awaitCredentials: suspend () -> Unit = {},
) {
    private val _settings = MutableStateFlow<Settings?>(null)
    val settings = _settings.asStateFlow()

    init {
        scope.launch {
            val backgroundReady = async { loadBackground() }
            val credentialsReady = async { awaitCredentials() }
            // Platform preparation can migrate settings; finish it before opening the store.
            credentialsReady.await()
            settingsFlow
                .catch { error ->
                    if (error !is IOException) throw error
                    emit(Settings())
                }
                .collect {
                    backgroundReady.await()
                    _settings.value = it
                }
        }
    }
}
