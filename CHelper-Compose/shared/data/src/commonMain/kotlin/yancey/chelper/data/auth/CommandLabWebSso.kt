package yancey.chelper.data.auth

import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import yancey.chelper.data.remote.library.service.CommandLabUserService

class CommandLabWebSso(
    private val login: LoginSession,
    private val service: CommandLabUserService,
) {
    companion object {
        private val invitePaths =
            setOf("/invite", "/invite/title", "/invite/quota", "/invite/points")

        /** 只把受支持的 CommandLab 邀请链接转换为后端 authorize 接口需要的站内 next。 */
        fun inviteNext(url: String): String? {
            if (url.any(Char::isWhitespace) || Regex("%(?![0-9a-fA-F]{2})").containsMatchIn(url))
                return null
            val uri = runCatching { Url(url) }.getOrNull() ?: return null
            if (uri.protocol.name.lowercase() !in setOf("http", "https")) return null
            if (uri.host.lowercase() !in setOf("abyssous.site", "www.abyssous.site")) return null

            val path = uri.encodedPath
            if (path !in invitePaths) return null

            val query = uri.encodedQuery.takeIf { it.isNotBlank() } ?: return null
            val hasInviteCode =
                query.split('&').any { parameter ->
                    parameter.substringBefore('=') == "code" &&
                        parameter.substringAfter('=', missingDelimiterValue = "").isNotBlank()
                }
            if (!hasInviteCode) return null

            return "$path?$query"
        }
    }

    /** 普通链接原样返回；邀请链接先换取一次性 Web SSO 地址。 */
    suspend fun resolveBrowserUrl(url: String): Result<String> {
        val next = inviteNext(url) ?: return Result.success(url)
        if (!login.isLoggedIn || login.currentUser?.isGuest == true) {
            return Result.failure(IllegalStateException("请先登录正式账号后打开邀请链接"))
        }

        return try {
            val response =
                service.authorizeWebSso(CommandLabUserService.WebSsoAuthorizeRequest(next = next))
            if (!response.isSuccess()) {
                Result.failure(IllegalStateException(response.message ?: "网页登录授权失败"))
            } else {
                response.data?.webUrl?.takeIf { it.isNotBlank() }?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("网页登录地址为空"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
