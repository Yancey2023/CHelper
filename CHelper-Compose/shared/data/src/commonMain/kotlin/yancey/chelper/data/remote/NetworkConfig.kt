package yancey.chelper.data.remote

import io.ktor.http.Url

data class NetworkConfig(
    val appBaseUrl: String = "https://www.yanceymc.cn/api/chelper/",
    val labBaseUrl: String = "https://abyssous.site/",
    val debug: Boolean = false,
) {
    init {
        listOf(appBaseUrl, labBaseUrl).forEach { value ->
            val url = Url(value)
            require(url.host.isNotBlank() && url.protocol.name in setOf("http", "https"))
            require(
                url.user.isNullOrBlank() &&
                    url.password.isNullOrBlank() &&
                    url.encodedQuery.isBlank() &&
                    url.fragment.isBlank()
            )
        }
    }
}
