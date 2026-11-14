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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import yancey.chelper.data.logging.ClientLog as Log
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.waf.WafHelper

/**
 * 拦截 EdgeOne WAF 质询 (200 OK + HTML + tst_status 脚本) 如果遇到此类拦截响应，则挂起当前请求，唤起 WafHelper 用 WebView 进行 JS
 * 质询认证 携带拿到后的 Cookie 自动重试原请求，对上层透明
 */
class WafInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // Browser verification cookies belong only to the command library origin.
        val origin = ServiceManager.LAB_BASE_URL.toHttpUrl()
        if (
            request.url.host != origin.host ||
                request.url.scheme != origin.scheme ||
                request.url.port != origin.port
        )
            return chain.proceed(request)

        // 如果已有 Cookie，先确保它是最新的
        val existingCookie = WafHelper.getCookie()
        if (!existingCookie.isNullOrEmpty() && request.header("Cookie") == null) {
            request = request.newBuilder().header("Cookie", existingCookie).build()
            WafHelper.getUserAgent()?.let {
                request = request.newBuilder().header("User-Agent", it).build()
            }
        }

        var response = chain.proceed(request)

        // 是否为 WAF 页面
        if (isWafChallenge(response)) {
            Log.w(
                "WafInterceptor",
                "WAF challenge detected on ${request.url}. Starting refresh process...",
            )
            response.close() // 清理被拦截的响应

            // 阻塞等待 WebView 质询结束
            var refreshSuccess = false
            val latch = CountDownLatch(1)
            WafHelper.refreshCookie { success ->
                refreshSuccess = success
                latch.countDown()
            }

            // Includes the browser challenge and bounded cleanup of its temporary Windows profile.
            try {
                latch.await(30, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("浏览器验证被中断", e)
            }

            if (refreshSuccess) {
                val newCookie = WafHelper.getCookie()
                Log.i("WafInterceptor", "WAF refresh successful. Retrying request.")

                if (!newCookie.isNullOrEmpty()) {
                    // 更新现有请求的 Cookie 头重试
                    val retryRequest =
                        request
                            .newBuilder()
                            .header("Cookie", newCookie)
                            .apply { WafHelper.getUserAgent()?.let { header("User-Agent", it) } }
                            .build()
                    return chain.proceed(retryRequest)
                }
            }
            throw IOException("站点浏览器验证失败，请稍后重试")
        }

        return response
    }

    private fun isWafChallenge(response: Response): Boolean {
        // EdgeOne WAF 质询通常返回 200 或 202
        if (response.code != 200 && response.code != 202) return false

        // 返回的内容类型应该是 text/html
        val contentType = response.header("Content-Type") ?: ""
        if (!contentType.contains("text/html", ignoreCase = true)) return false

        val server = response.header("server") ?: ""
        if (!server.contains("TencentEdgeOne", ignoreCase = true)) {}

        // 读取部分 Body 嗅探特征字符串
        try {
            val bodyPreview = response.peekBody(2048).string()
            if (bodyPreview.contains("EO_Bot_Ssid") || bodyPreview.contains("__tst_status=")) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
}
