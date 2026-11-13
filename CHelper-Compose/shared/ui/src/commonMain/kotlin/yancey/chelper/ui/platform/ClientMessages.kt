package yancey.chelper.ui.platform

import kotlin.concurrent.Volatile
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Shared feedback entry; Android installs Toaster, other hosts use shared Foundation feedback. */
object ClientMessages {
    @Volatile private var platformPresenter: ((String) -> Unit)? = null

    /**
     * Install once at application startup, before feature code can publish messages. The presenter
     * must accept calls from any thread and must not retain an Activity or composition. Passing
     * null selects the default transient Compose host delivery.
     */
    fun setPlatformPresenter(presenter: ((String) -> Unit)?) {
        platformPresenter = presenter
    }

    private val events =
        MutableSharedFlow<String>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val messages = events.asSharedFlow()

    fun show(value: Any?) {
        val text = value?.toString().orEmpty()
        if (text.isBlank()) return
        val presenter = platformPresenter
        if (presenter != null) presenter(text) else events.tryEmit(text)
    }
}
