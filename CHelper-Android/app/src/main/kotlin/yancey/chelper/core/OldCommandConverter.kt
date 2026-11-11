package yancey.chelper.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The legacy JNI converter has process-wide mutable initialization state. */
class OldCommandConverter(context: Context) {
    private val application = context.applicationContext

    suspend fun convert(command: String): String =
        withContext(dispatcher) {
            ensureActive()
            CHelperCore.old2new(application, command)
        }

    private companion object {
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
    }
}
