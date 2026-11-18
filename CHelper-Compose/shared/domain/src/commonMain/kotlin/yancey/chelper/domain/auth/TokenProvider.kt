package yancey.chelper.domain.auth

fun interface TokenProvider {
    suspend fun token(): String?
}

data class BrowserVerification(val cookie: String, val userAgent: String)

fun interface BrowserVerifier {
    suspend fun verify(): BrowserVerification?
}
