/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.data.auth

import java.io.IOException
import java.util.function.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.data.remote.library.service.CommandLabUserService.LoginRequest

/**
 * 登录工具类
 *
 * 管理用户登录状态和 JWT 令牌
 */
object LoginUtil {
    private var storage: yancey.chelper.domain.auth.CredentialStore? = null
    private val stateLock = Any()
    private val refreshMutex = Mutex()
    private var generation = 0L
    private val _accountId = MutableStateFlow<Int?>(null)
    val accountId = _accountId.asStateFlow()

    /** 当前登录用户信息 */
    @Volatile
    var currentUser: CommandLabUserService.User? = null
        private set

    /** 当前登录令牌 */
    @Volatile
    var currentToken: String? = null
        private set

    /** 上次登录时间戳 */
    private var lastLoginTimestamp: Long? = null

    /** 保存的登录凭据 */
    private var savedMail: String? = null
    private var savedPassword: String? = null

    /** 本地持久化的用户数据 */
    @Serializable
    private class SavedUserData(
        var mail: String? = null,
        var password: String? = null,
        var token: String? = null,
        var lastLoginTimestamp: Long? = null,
        var user: CommandLabUserService.User? = null,
    )

    /** 初始化登录工具，从本地文件恢复登录状态 */
    fun init(
        credentialStore: yancey.chelper.domain.auth.CredentialStore,
        onError: Consumer<Throwable?>,
    ) {
        try {
            storage = credentialStore
            storage?.read()?.let { content ->
                val savedData = Json.decodeFromString<SavedUserData>(content)
                synchronized(stateLock) {
                    savedMail = savedData.mail
                    savedPassword = savedData.password
                    currentToken = savedData.token
                    lastLoginTimestamp = savedData.lastLoginTimestamp
                    currentUser = savedData.user
                    _accountId.value = currentUser?.takeUnless { it.isGuest == true }?.id
                }
            }
        } catch (e: Exception) {
            onError.accept(e)
        }
    }

    /** 检查是否已登录 */
    val isLoggedIn: Boolean
        get() = currentToken != null && currentUser != null

    /**
     * 获取 JWT 令牌
     *
     * 如果令牌过期（超过 600 秒），会自动重新登录获取新令牌
     */
    @Throws(IOException::class)
    suspend fun getToken(): String? =
        refreshMutex.withLock {
            val snapshot =
                synchronized(stateLock) {
                    if (
                        currentToken != null &&
                            lastLoginTimestamp != null &&
                            System.currentTimeMillis() - lastLoginTimestamp!! < 600000
                    )
                        return@withLock currentToken
                    val mail = savedMail ?: return@withLock null
                    val password = savedPassword ?: return@withLock null
                    Triple(generation, mail, password)
                }
            val response =
                ServiceManager.COMMAND_LAB_USER_SERVICE.login(
                    LoginRequest(account = snapshot.second, password = snapshot.third)
                )
            val data = response.data
            if (!response.isSuccess() || data == null) return@withLock null
            withContext(Dispatchers.IO) {
                synchronized(stateLock) {
                    if (generation != snapshot.first) return@synchronized null
                    installSession(snapshot.second, snapshot.third, data)
                    currentToken
                }
            }
        }

    suspend fun tokenForUser(ownerId: Int): String? {
        if (synchronized(stateLock) { currentUser?.id != ownerId }) return null
        val token = getToken()
        return synchronized(stateLock) {
            token?.takeIf { currentUser?.id == ownerId && currentToken == it }
        }
    }

    /** 执行登录 */
    suspend fun login(mail: String, password: String): Result<CommandLabUserService.LoginResponse> {
        val version = synchronized(stateLock) { ++generation }
        return try {
            val response =
                ServiceManager.COMMAND_LAB_USER_SERVICE.login(
                    LoginRequest(account = mail, password = password)
                )
            val data = response.data
            if (response.isSuccess() && data != null) {
                withContext(Dispatchers.IO) {
                    synchronized(stateLock) {
                        check(generation == version) { "登录请求已失效" }
                        installSession(mail, password, data)
                    }
                }
                Result.success(data)
            } else Result.failure(Exception(response.message ?: "登录失败"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 登出 */
    fun logout() =
        synchronized(stateLock) {
            generation++
            savedMail = null
            savedPassword = null
            currentToken = null
            currentUser = null
            _accountId.value = null
            lastLoginTimestamp = null
            storage?.clear()
        }

    /** 保存登录状态到本地文件 */
    @Throws(IOException::class)
    private fun installSession(
        mail: String,
        password: String,
        response: CommandLabUserService.LoginResponse,
    ) {
        val timestamp = System.currentTimeMillis()
        val savedData = SavedUserData(mail, password, response.token, timestamp, response.user)
        storage?.write(Json.encodeToString(savedData))
        savedMail = mail
        savedPassword = password
        currentToken = response.token
        currentUser = response.user
        lastLoginTimestamp = timestamp
        _accountId.value = currentUser?.takeUnless { it.isGuest == true }?.id
    }
}
