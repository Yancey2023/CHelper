package yancey.chelper.data.logging

/** Logging works on both JVM hosts, without an Android dependency in shared networking. */
object ClientLog {
    fun d(tag: String, message: String) {
        java.util.logging.Logger.getLogger(tag).fine(message)
    }

    fun i(tag: String, message: String) {
        java.util.logging.Logger.getLogger(tag).info(message)
    }

    fun w(tag: String, message: String, cause: Throwable? = null) {
        System.err.println("$tag: $message")
        cause?.printStackTrace()
    }

    fun e(tag: String, message: String, cause: Throwable? = null) = w(tag, message, cause)
}
