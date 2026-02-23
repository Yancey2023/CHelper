/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
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

package yancey.chelper.network.library.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.service.CommandLabUserService
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 访客用户管理工具
 * 
 * 负责访客的自动注册和登录
 * 
 * 使用方式：
 * 在 Application.onCreate 中调用 init(context)
 * 之后可随时调用 ensureLoggedIn() 确保已登录
 */
object GuestAuthUtil {
    
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"
    
    /**
     * 内置的 ECDSA 私钥（Base64 编码的 PKCS#8 格式）
     */
    private const val PRIVATE_KEY_BASE64 = 
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgqvJx/ysv0wqyM0ft" +
        "S8/x9cs5s00wSxyCKcqmvPTl2Z6hRANCAAQEZUdqvygLrPJwTS4ITkDYz4wnFCm6" +
        "jZLrxfZO2/2jjV/N8qBTBd1iyejpd0rBLxPqNBNIGzVPPGy6nDyRe3f7"
    
    private var privateKey: PrivateKey? = null
    private var cachedFingerprint: String? = null
    
    /** 当前访客用户信息 */
    var guestUser: CommandLabUserService.User? = null
        private set
    
    /** 当前访客令牌 */
    var guestToken: String? = null
        private set
    
    /**
     * 初始化
     * 
     * 在 Application.onCreate 中调用，传入 applicationContext
     */
    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        // 初始化设备指纹
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))
        cachedFingerprint = hashBytes.joinToString("") { "%02x".format(it) }
        
        // 初始化私钥
        initPrivateKey()
    }
    
    /**
     * 初始化私钥
     */
    private fun initPrivateKey() {
        if (privateKey != null) return
        
        try {
            val keyBytes = Base64.decode(PRIVATE_KEY_BASE64, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            privateKey = keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取设备指纹
     */
    fun getFingerprint(): String? = cachedFingerprint
    
    /**
     * 生成 auth_code
     * 
     * 使用 SHA256withECDSA 算法对 fingerprint 进行签名
     */
    private fun generateAuthCode(fingerprint: String): String? {
        return try {
            initPrivateKey()
            val key = privateKey ?: return null
            
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(key)
            signature.update(fingerprint.toByteArray(Charsets.UTF_8))
            val signatureBytes = signature.sign()
            
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 检查是否已登录（包括正式用户和访客）
     */
    fun isLoggedIn(): Boolean {
        return LoginUtil.isLoggedIn || (guestToken != null && guestUser != null)
    }
    
    /**
     * 确保已登录
     * 
     * 优先使用正式用户登录，如果没有则尝试访客登录/注册
     * 
     * @return 是否成功登录
     */
    @Synchronized
    fun ensureLoggedIn(): Boolean {
        // 已经登录了
        if (isLoggedIn()) return true
        
        // 尝试正式用户自动登录
        try {
            if (LoginUtil.token != null) return true
        } catch (_: Exception) {}
        
        // 尝试访客登录或注册
        return tryGuestAuth()
    }
    
    /**
     * 尝试访客认证（先登录，失败则注册）
     */
    private fun tryGuestAuth(): Boolean {
        val fingerprint = cachedFingerprint ?: return false
        
        // 先尝试登录
        if (guestLogin(fingerprint)) {
            return true
        }
        
        // 登录失败，尝试注册
        return guestRegister(fingerprint)
    }
    
    /**
     * 访客登录
     */
    private fun guestLogin(fingerprint: String): Boolean {
        return try {
            val authCode = generateAuthCode(fingerprint) ?: return false
            
            val request = CommandLabUserService.GuestAuthRequest().apply {
                this.fingerprint = fingerprint
                this.auth_code = authCode
            }
            
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE
                ?.guestLogin(request)
                ?.execute()
            
            if (response?.body()?.isSuccess() == true && response.body()?.data != null) {
                val data = response.body()!!.data!!
                guestToken = data.token
                guestUser = data.user
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 访客注册
     */
    private fun guestRegister(fingerprint: String): Boolean {
        return try {
            val authCode = generateAuthCode(fingerprint) ?: return false
            
            val request = CommandLabUserService.GuestAuthRequest().apply {
                this.fingerprint = fingerprint
                this.auth_code = authCode
            }
            
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE
                ?.guestRegister(request)
                ?.execute()
            
            if (response?.body()?.isSuccess() == true && response.body()?.data != null) {
                val data = response.body()!!.data!!
                guestToken = data.token
                guestUser = data.user
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 清除访客登录状态
     */
    fun clearGuestSession() {
        guestToken = null
        guestUser = null
    }
}
