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

package yancey.chelper.data.remote.interceptor

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import yancey.chelper.data.auth.GuestAuthUtil
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.remote.ServiceManager

/**
 * 认证拦截器
 *
 * 自动为发往 abyssous.site 的请求添加 Authorization header 优先使用正式用户 token，否则使用访客 token
 */
class AuthInterceptor private constructor() : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val origin = ServiceManager.LAB_BASE_URL.toHttpUrl()
        if (
            request.url.host == origin.host &&
                request.url.scheme == origin.scheme &&
                request.url.port == origin.port
        ) {
            val builder =
                request
                    .newBuilder()
                    // Keep the existing backend wire identifier on both hosts.
                    .header("X-Client", "chelper-android")
                    .header("User-Agent", request.header("User-Agent") ?: "CHelper-Android")

            // WafInterceptor owns verification cookies and their matching browser User-Agent.

            // 添加 Authorization Header
            if (
                !isAuthEndpoint(request.url.encodedPath) &&
                    request.header("Authorization").isNullOrEmpty()
            ) {
                // 获取 token（正式用户优先，否则访客）
                val token = runBlocking { getToken() }

                if (!token.isNullOrEmpty()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
            }

            return chain.proceed(builder.build())
        }

        return chain.proceed(request)
    }

    /** 判断是否是认证相关的 endpoint（登录/注册/验证码等） 这些 endpoint 不需要添加 Authorization header，避免循环调用 */
    private fun isAuthEndpoint(path: String): Boolean {
        val authPaths = listOf("/guest/login", "/guest/register", "/login", "/register", "/captcha")
        return authPaths.any { path.contains(it, ignoreCase = true) }
    }

    /**
     * 获取当前有效的 token
     *
     * 优先级：正式用户 > 访客
     */
    private suspend fun getToken(): String? {
        // 尝试正式用户 token
        try {
            LoginUtil.getToken()?.let {
                return it
            }
        } catch (_: Exception) {}

        // 尝试访客 token
        GuestAuthUtil.guestToken?.let {
            return it
        }

        // 尝试自动访客登录/注册
        if (GuestAuthUtil.ensureLoggedIn()) {
            return GuestAuthUtil.guestToken
        }

        return null
    }

    companion object {
        val INSTANCE: AuthInterceptor = AuthInterceptor()
    }
}
