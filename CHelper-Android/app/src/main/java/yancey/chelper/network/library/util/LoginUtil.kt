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

package yancey.chelper.network.library.util

import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.service.CommandLabUserService.LoginRequest
import java.io.File
import java.io.IOException
import java.util.function.Consumer

/**
 * 登录工具类
 * 
 * 管理用户登录状态和 JWT 令牌
 */
object LoginUtil {
    private var file: File? = null

    /** 当前登录用户信息 */
    var currentUser: CommandLabUserService.User? = null
        private set

    /** 当前登录令牌 */
    var currentToken: String? = null
        private set

    /** 上次登录时间戳 */
    private var lastLoginTimestamp: Long? = null

    /** 保存的登录凭据 */
    private var savedMail: String? = null
    private var savedPassword: String? = null

    /**
     * 本地持久化的用户数据
     */
    private class SavedUserData {
        var mail: String? = null
        var password: String? = null
        var token: String? = null
        var lastLoginTimestamp: Long? = null
        var user: CommandLabUserService.User? = null
    }

    /**
     * 初始化登录工具，从本地文件恢复登录状态
     */
    fun init(file: File, onError: Consumer<Throwable?>) {
        this.file = file
        if (file.exists()) {
            try {
                val savedData = ServiceManager.GSON!!.fromJson(
                    FileUtil.readString(file),
                    SavedUserData::class.java
                )
                savedMail = savedData.mail
                savedPassword = savedData.password
                currentToken = savedData.token
                lastLoginTimestamp = savedData.lastLoginTimestamp
                currentUser = savedData.user
            } catch (throwable: Throwable) {
                onError.accept(throwable)
            }
        }
    }

    /**
     * 检查是否已登录
     */
    val isLoggedIn: Boolean
        get() = currentToken != null && currentUser != null

    /**
     * 获取 JWT 令牌
     * 
     * 如果令牌过期（超过 60 秒），会自动重新登录获取新令牌
     */
    @get:Throws(IOException::class)
    val token: String?
        get() {
            if (savedMail == null || savedPassword == null) {
                return null
            }

            // 令牌在 60 秒内有效，直接返回
            if (currentToken != null && lastLoginTimestamp != null
                && System.currentTimeMillis() - lastLoginTimestamp!! < 60000
            ) {
                return currentToken
            }

            // 重新登录获取新令牌
            val request = LoginRequest().apply {
                account = savedMail
                password = savedPassword
            }
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE?.login(request)?.execute()

            if (response?.body()?.isSuccess() == true && response.body()?.data != null) {
                val data = response.body()!!.data!!
                currentToken = data.token
                currentUser = data.user
                lastLoginTimestamp = System.currentTimeMillis()
                saveToFile()
                return currentToken
            }

            return null
        }

    /**
     * 执行登录
     */
    fun login(mail: String, password: String): Result<CommandLabUserService.LoginResponse> {
        val request = LoginRequest().apply {
            this.account = mail
            this.password = password
        }

        return try {
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE?.login(request)?.execute()

            if (response?.body()?.isSuccess() == true && response.body()?.data != null) {
                val data = response.body()!!.data!!
                savedMail = mail
                savedPassword = password
                currentToken = data.token
                currentUser = data.user
                lastLoginTimestamp = System.currentTimeMillis()
                saveToFile()
                Result.success(data)
            } else {
                Result.failure(Exception(response?.body()?.message ?: "登录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 登出
     */
    fun logout() {
        savedMail = null
        savedPassword = null
        currentToken = null
        currentUser = null
        lastLoginTimestamp = null
        file?.delete()
    }

    /**
     * 保存登录状态到本地文件
     */
    private fun saveToFile() {
        val savedData = SavedUserData().apply {
            mail = savedMail
            password = savedPassword
            token = currentToken
            lastLoginTimestamp = this@LoginUtil.lastLoginTimestamp
            user = currentUser
        }
        file?.let {
            FileUtil.writeString(it, ServiceManager.GSON!!.toJson(savedData))
        }
    }
}
