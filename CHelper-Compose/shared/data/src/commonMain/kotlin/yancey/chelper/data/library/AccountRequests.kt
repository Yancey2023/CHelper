package yancey.chelper.data.library

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.auth.LoginSession

/** Pins automatic authentication to the account that started a private operation. */
internal class AccountCredentials(val token: String, val owner: Int, val revision: Long) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AccountCredentials>
}

internal class AccountRequests(private val login: LoginSession) {
    suspend fun <T> run(block: suspend () -> T): T {
        val before = login.state.value
        val owner = before.user?.id ?: error("请先登录正式账号")
        check(before.user?.isGuest != true) { "请先登录正式账号" }
        val pinned = currentCoroutineContext()[AccountCredentials]
        if (pinned != null && (pinned.owner != owner || pinned.revision != before.revision))
            throw CancellationException("账号已切换，请重新操作")
        val token = pinned?.token ?: login.tokenForUser(owner) ?: error("登录已失效，请重新登录")
        fun checkOwner() {
            val current = login.state.value
            if (current.user?.id != owner || current.revision != before.revision)
                throw CancellationException("账号已切换，请重新操作")
        }
        checkOwner()
        return coroutineScope {
            val request = async {
                withContext(AccountCredentials(token, owner, before.revision)) { block() }
            }
            val observer =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    login.state.collect { current ->
                        if (current.user?.id != owner || current.revision != before.revision)
                            request.cancel(CancellationException("账号已切换，请重新操作"))
                    }
                }
            try {
                request.await().also { checkOwner() }
            } finally {
                observer.cancel()
            }
        }
    }
}
