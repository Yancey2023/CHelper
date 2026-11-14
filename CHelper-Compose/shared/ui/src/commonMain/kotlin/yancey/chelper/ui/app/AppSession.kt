package yancey.chelper.ui.app

/** Hosts invoke this before clearing ViewModels or releasing platform resources. */
class AppSession {
    private val flushes = linkedMapOf<Any, suspend () -> Unit>()

    fun register(owner: Any, flush: suspend () -> Unit): () -> Unit {
        flushes[owner] = flush
        return { flushes.remove(owner) }
    }

    fun prepareClose(): suspend () -> Unit {
        val pending = flushes.values.toList()
        return { pending.forEach { it() } }
    }

    suspend fun flush() {
        prepareClose().invoke()
    }

    suspend fun flush(owner: Any) {
        flushes[owner]?.invoke()
    }
}
