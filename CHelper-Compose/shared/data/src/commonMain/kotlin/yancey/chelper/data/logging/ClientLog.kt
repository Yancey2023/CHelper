package yancey.chelper.data.logging

/** Hosts may install structured logging; no platform logging type leaks into common code. */
object ClientLog {
    var sink: (level: String, tag: String, message: String, cause: Throwable?) -> Unit =
        { level, tag, message, cause ->
            println("$level/$tag: $message")
            cause?.printStackTrace()
        }

    fun d(tag: String, message: String) = sink("D", tag, message, null)

    fun i(tag: String, message: String) = sink("I", tag, message, null)

    fun w(tag: String, message: String, cause: Throwable? = null) = sink("W", tag, message, cause)

    fun e(tag: String, message: String, cause: Throwable? = null) = sink("E", tag, message, cause)
}
