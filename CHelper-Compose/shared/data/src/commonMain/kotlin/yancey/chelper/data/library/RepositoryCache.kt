package yancey.chelper.data.library

import kotlin.time.TimeSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import yancey.chelper.domain.library.*

/** Generation checks prevent an invalidated in-flight read from repopulating a stale cache. */
internal class RepositoryCache {
    private val lock = SynchronizedObject()
    private var generation = 0L
    private var sequence = 0L

    private data class Entry(val value: Any, val at: TimeSource.Monotonic.ValueTimeMark)

    private val entries = linkedMapOf<String, Entry>()
    private val loading = mutableMapOf<String, Long>()

    fun invalidate() =
        synchronized(lock) {
            generation++
            entries.clear()
            loading.clear()
        }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> read(
        key: String,
        force: Boolean,
        load: suspend () -> RepositoryResult<T>,
    ): RepositoryResult<T> {
        val version =
            synchronized(lock) {
                entries.entries.removeAll { it.value.at.elapsedNow().inWholeSeconds >= 60 }
                val entry = entries[key]
                if (!force && entry != null) return RepositoryResult(entry.value as T)
                loading[key] = ++sequence
                generation to sequence
            }
        try {
            val result = load()
            currentCoroutineContext().ensureActive()
            if (result.isSuccess())
                result.data?.let { value ->
                    synchronized(lock) {
                        if (generation == version.first && loading[key] == version.second) {
                            entries.remove(key)
                            while (entries.size >= 64) entries.remove(entries.keys.first())
                            entries[key] = Entry(value, TimeSource.Monotonic.markNow())
                        }
                    }
                }
            return result
        } finally {
            synchronized(lock) {
                if (loading[key] == version.second) loading.remove(key)
            }
        }
    }
}
