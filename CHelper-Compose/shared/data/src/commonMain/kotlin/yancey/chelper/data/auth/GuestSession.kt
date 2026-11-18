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

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import yancey.chelper.data.remote.library.service.CommandLabAuthService
import yancey.chelper.data.remote.library.service.CommandLabUserService

/**
 * 访客用户管理工具
 *
 * 负责访客的自动注册和登录
 *
 * 使用方式： 在 Application.onCreate 中调用 init(context) 之后可随时调用 ensureLoggedIn() 确保已登录
 */
class GuestSession(private val service: CommandLabAuthService, private val login: LoginSession) {

    /** 内置的 ECDSA 私钥（Base64 编码的 PKCS#8 格式） */
    private val PRIVATE_KEY_BASE64 =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgqvJx/ysv0wqyM0ft" +
            "S8/x9cs5s00wSxyCKcqmvPTl2Z6hRANCAAQEZUdqvygLrPJwTS4ITkDYz4wnFCm6" +
            "jZLrxfZO2/2jjV/N8qBTBd1iyejpd0rBLxPqNBNIGzVPPGy6nDyRe3f7"

    private var privateKey: ECDSA.PrivateKey? = null
    private val authMutex = Mutex()
    private var cachedFingerprint: String? = null

    private val stateLock = SynchronizedObject()
    private var generation = 0L
    @Volatile private var session: CommandLabUserService.LoginResponse? = null
    val guestUser: CommandLabUserService.User?
        get() = session?.user

    val guestToken: String?
        get() = session?.token

    private fun install(data: CommandLabUserService.LoginResponse, version: Long): Boolean =
        synchronized(stateLock) {
            if (generation != version) false
            else {
                session = data
                login.guestChanged(data.user)
                true
            }
        }

    /**
     * 初始化
     *
     * 在 Application.onCreate 中调用，传入 applicationContext
     */
    fun init(deviceId: String) {
        // 初始化设备指纹
        val androidId = deviceId

        cachedFingerprint = androidId.encodeToByteArray().toByteString().sha256().hex()

        // 初始化私钥
        initPrivateKey()
    }

    /** 初始化私钥 */
    private fun initPrivateKey() {
        if (privateKey != null) return

        try {
            val keyBytes = Base64.decode(PRIVATE_KEY_BASE64)
            privateKey =
                CryptographyProvider.Default.get(ECDSA)
                    .privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArrayBlocking(EC.PrivateKey.Format.DER, keyBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 获取设备指纹 */
    fun getFingerprint(): String? = cachedFingerprint

    /**
     * 生成 auth_code
     *
     * 使用 SHA256withECDSA 算法对 fingerprint 进行签名
     */
    fun generateAuthCode(fingerprint: String): String? {
        return try {
            initPrivateKey()
            val key = privateKey ?: return null

            val signatureBytes =
                key.signatureGenerator(SHA256, ECDSA.SignatureFormat.DER)
                    .generateSignatureBlocking(fingerprint.encodeToByteArray())
            Base64.encode(signatureBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 检查是否已登录（包括正式用户和访客） */
    fun isLoggedIn(): Boolean {
        return login.isLoggedIn || (guestToken != null && guestUser != null)
    }

    /**
     * 确保已登录
     *
     * 优先使用正式用户登录，如果没有则尝试访客登录/注册
     *
     * @return 是否成功登录
     */
    suspend fun ensureLoggedIn(): Boolean {
        // 已经登录了
        if (isLoggedIn()) return true

        // 尝试正式用户自动登录
        try {
            if (login.getToken() != null) return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        // 尝试访客登录或注册
        return authMutex.withLock { if (isLoggedIn()) true else tryGuestAuth() }
    }

    /** 尝试访客认证（先登录，失败则注册） */
    private suspend fun tryGuestAuth(): Boolean {
        val fingerprint = cachedFingerprint ?: return false
        val version = synchronized(stateLock) { generation }

        // 先尝试登录
        if (guestLogin(fingerprint, version)) {
            return true
        }

        // 登录失败，尝试注册
        if (synchronized(stateLock) { generation != version }) return false
        return guestRegister(fingerprint, version)
    }

    /** 访客登录 */
    private suspend fun guestLogin(fingerprint: String, version: Long): Boolean {
        return try {
            val authCode = generateAuthCode(fingerprint) ?: return false

            val request =
                CommandLabUserService.GuestAuthRequest().apply {
                    this.fingerprint = fingerprint
                    this.authCode = authCode
                }

            val response = service.guestLogin(request)
            if (response.isSuccess() && response.data != null) {
                val data = response.data!!
                install(data, version)
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 访客注册 */
    private suspend fun guestRegister(fingerprint: String, version: Long): Boolean {
        return try {
            val authCode = generateAuthCode(fingerprint) ?: return false

            val request =
                CommandLabUserService.GuestAuthRequest().apply {
                    this.fingerprint = fingerprint
                    this.authCode = authCode
                }

            val response = service.guestRegister(request)
            if (response.isSuccess() && response.data != null) {
                val data = response.data!!
                install(data, version)
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 清除访客登录状态 */
    fun clearGuestSession() {
        synchronized(stateLock) {
            generation++
            session = null
            login.guestChanged(null)
        }
    }
}
