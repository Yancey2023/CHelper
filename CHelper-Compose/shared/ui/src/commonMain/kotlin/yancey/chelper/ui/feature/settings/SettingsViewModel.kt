package yancey.chelper.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults

class SettingsViewModel(private val repository: AppSettingsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(Settings().withDefaults())
    val state = mutable.asStateFlow()
    private val failures = MutableStateFlow<String?>(null)
    val error = failures.asStateFlow()
    private val edits = Channel<(Settings) -> Settings>(Channel.UNLIMITED)
    private val pending = MutableStateFlow(0)
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        viewModelScope.launch {
            try {
                repository.state.collect { mutable.value = it.withDefaults() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.value = e.message ?: "设置读取失败"
            }
        }
        persistence.launch {
            for (edit in edits) {
                try {
                    repository.update(edit)
                    failures.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.value = e.message ?: "设置保存失败"
                } finally {
                    pending.value--
                }
            }
            persistence.cancel()
        }
    }

    fun update(transform: (Settings) -> Settings) {
        pending.value++
        if (!edits.trySend(transform).isSuccess) pending.value--
    }

    suspend fun awaitSaved() {
        pending.first { it == 0 }
        check(failures.value == null) { failures.value.orEmpty() }
    }

    override fun onCleared() {
        edits.close()
    }
}
