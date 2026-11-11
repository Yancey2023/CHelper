package yancey.chelper.data

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One process-wide read; null means the UI must wait before drawing its themed first frame. */
class StartupState
internal constructor(
    settingsFlow: Flow<Settings>,
    loadBackground: suspend () -> Unit,
    scope: CoroutineScope,
    awaitCredentials: suspend () -> Unit = {},
) {
    constructor(
        settings: SettingsDataStore,
        background: BackgroundStore,
        scope: CoroutineScope,
        awaitCredentials: suspend () -> Unit = {},
    ) : this(
        settings.settings(),
        { withContext(Dispatchers.IO) { background.load() } },
        scope,
        awaitCredentials,
    )

    private val _settings = MutableStateFlow<Settings?>(null)
    val settings = _settings.asStateFlow()

    init {
        scope.launch {
            val backgroundReady = async { loadBackground() }
            val credentialsReady = async { awaitCredentials() }
            settingsFlow
                .catch { error ->
                    if (error !is IOException) throw error
                    emit(Settings())
                }
                .collect {
                    backgroundReady.await()
                    credentialsReady.await()
                    _settings.value = it
                }
        }
    }
}
