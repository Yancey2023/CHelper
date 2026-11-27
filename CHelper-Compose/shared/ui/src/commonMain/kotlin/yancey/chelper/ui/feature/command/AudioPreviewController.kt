package yancey.chelper.ui.feature.command

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import yancey.chelper.domain.command.SuggestionAudioPlayer
import yancey.chelper.domain.command.SuggestionAudioSession

internal data class AudioPreviewRequest(val index: Int, val url: String)

internal data class AudioPreviewState(
    val request: AudioPreviewRequest,
    val status: Status,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
) {
    enum class Status {
        Loading,
        Playing,
        Paused,
    }

    val canPause: Boolean
        get() = status != Status.Paused
}

internal class AudioPreviewController(
    private val scope: CoroutineScope,
    private val player: SuggestionAudioPlayer,
    private val onError: () -> Unit,
) {
    var state by mutableStateOf<AudioPreviewState?>(null)
        private set

    private var job: Job? = null
    private var session: SuggestionAudioSession? = null
    private var generation = 0

    fun toggle(request: AudioPreviewRequest) {
        val active = state
        if (
            active?.request != request ||
                active.status == AudioPreviewState.Status.Paused && session == null
        ) {
            open(request)
            return
        }
        val current = session
        if (current == null) {
            clear()
            state = active.copy(status = AudioPreviewState.Status.Paused)
            return
        }
        perform {
            if (active.status == AudioPreviewState.Status.Playing) {
                current.pause()
                update(active, current, AudioPreviewState.Status.Paused)
            } else {
                if (current.finished || active.positionMillis >= active.durationMillis)
                    current.seekTo(0L)
                current.play()
                update(active, current, AudioPreviewState.Status.Playing)
            }
        }
    }

    fun seek(request: AudioPreviewRequest, positionMillis: Long) {
        val active = state?.takeIf { it.request == request } ?: return
        val current = session ?: return
        perform {
            val target = positionMillis.coerceIn(0L, active.durationMillis)
            current.seekTo(target)
            state = active.copy(positionMillis = target)
        }
    }

    fun clear() {
        generation++
        session = null
        state = null
        job?.cancel()
    }

    private fun open(request: AudioPreviewRequest) {
        clear()
        val previous = job
        val token = generation
        state = AudioPreviewState(request, AudioPreviewState.Status.Loading)
        job = scope.launch {
            previous?.join()
            try {
                player.open(request.url).use { current ->
                    ensureActive()
                    session = current
                    current.play()
                    update(checkNotNull(state), current, AudioPreviewState.Status.Playing)
                    while (true) {
                        delay(100L)
                        val active = state ?: break
                        if (active.status == AudioPreviewState.Status.Playing) {
                            val status =
                                if (current.finished) AudioPreviewState.Status.Paused
                                else AudioPreviewState.Status.Playing
                            update(active, current, status)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == token) fail()
            } finally {
                if (generation == token) session = null
            }
        }
    }

    private fun update(
        active: AudioPreviewState,
        current: SuggestionAudioSession,
        status: AudioPreviewState.Status,
    ) {
        val duration = current.durationMillis
        state =
            active.copy(
                status = status,
                positionMillis = current.positionMillis.coerceIn(0L, duration),
                durationMillis = duration,
            )
    }

    private inline fun perform(action: () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail()
        }
    }

    private fun fail() {
        clear()
        onError()
    }
}
