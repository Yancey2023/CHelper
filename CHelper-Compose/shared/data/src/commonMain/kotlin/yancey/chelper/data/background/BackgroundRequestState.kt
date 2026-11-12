package yancey.chelper.data.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A request and its published value are compared atomically, including while restoring defaults.
 */
class BackgroundRequestState<T>(initial: T) {
    private data class State<T>(val revision: Long, val value: T)

    private val state = MutableStateFlow(State(0, initial))
    val value
        get() = state.value.value

    val values = state.map { it.value }.distinctUntilChanged()

    fun begin(): Long {
        while (true) {
            val before = state.value
            if (state.compareAndSet(before, before.copy(revision = before.revision + 1)))
                return before.revision + 1
        }
    }

    fun beginLoad(): Long? {
        while (true) {
            val before = state.value
            if (before.revision != 0L) return null
            if (state.compareAndSet(before, before.copy(revision = 1))) return 1L
        }
    }

    fun isCurrent(revision: Long) = state.value.revision == revision

    fun publish(revision: Long, value: T): Boolean {
        while (true) {
            val before = state.value
            if (before.revision != revision) return false
            if (state.compareAndSet(before, State(revision, value))) return true
        }
    }
}

fun backgroundSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(width > 0 && height > 0 && maxEdge > 0)
    var sample = 1
    while (
        width.toLong() > maxEdge.toLong() * sample || height.toLong() > maxEdge.toLong() * sample
    ) {
        if (sample > Int.MAX_VALUE / 2) return Int.MAX_VALUE
        sample *= 2
    }
    return sample
}
