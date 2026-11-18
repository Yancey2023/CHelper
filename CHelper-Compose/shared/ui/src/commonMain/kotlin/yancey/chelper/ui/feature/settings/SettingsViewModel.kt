package yancey.chelper.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults

class SettingsViewModel(private val repository: AppSettingsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(Settings().withDefaults())
    val state = mutable.asStateFlow()
    private val failures = MutableStateFlow<String?>(null)
    val error = failures.asStateFlow()
    private val edits = ArrayDeque<(Settings) -> Settings>()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val writes = Mutex()
    private var readFailure: String? = null
    private var writeFailure: String? = null
    private var reader: Job? = null
    private var closed = false
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        observe()
        persistence.launch {
            for (signal in signals) {
                try {
                    savePending()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the failed edit and all later edits in order for a caller retry.
                }
            }
            persistence.cancel()
        }
    }

    fun update(transform: (Settings) -> Settings) {
        if (closed) return
        edits.addLast(transform)
        signals.trySend(Unit)
    }

    fun retry() {
        if (closed) return
        if (reader?.isActive != true) observe()
        signals.trySend(Unit)
    }

    private fun observe() {
        reader =
            viewModelScope.launch {
                try {
                    repository.state.collect {
                        mutable.value = it.withDefaults()
                        readFailure = null
                        publishFailure()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    readFailure = e.message ?: "设置读取失败"
                    publishFailure()
                }
            }
    }

    private fun publishFailure() {
        failures.value = writeFailure ?: readFailure
    }

    private suspend fun savePending() =
        writes.withLock {
            try {
                while (edits.isNotEmpty()) {
                    repository.update(edits.first())
                    edits.removeFirst()
                }
                writeFailure = null
                publishFailure()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                writeFailure = e.message ?: "设置保存失败"
                publishFailure()
                throw e
            }
        }

    suspend fun awaitSaved() {
        savePending()
        if (readFailure != null) {
            mutable.value = repository.state.first().withDefaults()
            readFailure = null
            publishFailure()
            if (!closed) retry()
        }
    }

    override fun onCleared() {
        closed = true
        signals.close()
    }
}
