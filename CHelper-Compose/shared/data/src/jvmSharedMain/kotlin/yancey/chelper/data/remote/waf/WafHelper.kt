package yancey.chelper.data.remote.waf

/** Only the browser challenge is platform supplied; all network and auth logic is shared. */
object WafHelper {
    @Volatile private var cookie: () -> String? = { null }
    @Volatile private var refresh: (((Boolean) -> Unit)?) -> Unit = { it?.invoke(false) }
    @Volatile private var agent: () -> String? = { null }

    fun install(
        cookie: () -> String?,
        refresh: (((Boolean) -> Unit)?) -> Unit,
        userAgent: () -> String? = { null },
    ) {
        this.cookie = cookie
        this.refresh = refresh
        this.agent = userAgent
    }

    fun getCookie() = cookie()

    fun getUserAgent() = agent()

    fun refreshCookie(callback: ((Boolean) -> Unit)? = null) = refresh(callback)
}
