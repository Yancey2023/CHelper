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

package yancey.chelper.network.library.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import yancey.chelper.network.library.util.WafHelper
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 拦截 EdgeOne WAF 质询 (200 OK + HTML + tst_status 脚本)
 * 如果遇到此类拦截响应，则挂起当前请求，唤起 WafHelper 用 WebView 进行 JS 质询认证，
 * 携带拿到后的 Cookie 自动重试原请求，对上层透明。
 */
class WafInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        // 1. 如果已有 Cookie，先确保它是最新的（AuthInterceptor 中也会加，但这里也可以加保底）
        val existingCookie = WafHelper.getCookie()
        if (!existingCookie.isNullOrEmpty() && request.header("Cookie") == null) {
            request = request.newBuilder().header("Cookie", existingCookie).build()
        }

        var response = chain.proceed(request)
        
        // 2. 检查是否为 WAF 页面
        if (isWafChallenge(response)) {
            Log.w("WafInterceptor", "WAF challenge detected on ${request.url}. Starting refresh process...")
            response.close() // 清理被拦截的响应

            // 阻塞等待 WebView 质询结束
            var refreshSuccess = false
            val latch = CountDownLatch(1)
            WafHelper.refreshCookie { success ->
                refreshSuccess = success
                latch.countDown()
            }
            
            // 最多等待 15 秒（WebView 加载和执行 JS 可能比较慢）
            latch.await(15, TimeUnit.SECONDS)
            
            if (refreshSuccess) {
                val newCookie = WafHelper.getCookie()
                Log.i("WafInterceptor", "WAF refresh successful. Retrying with new cookie: $newCookie")
                
                if (!newCookie.isNullOrEmpty()) {
                    // 更新现有请求的 Cookie 头重试
                    val retryRequest = request.newBuilder()
                        .header("Cookie", newCookie)
                        .build()
                    return chain.proceed(retryRequest)
                }
            } else {
                Log.e("WafInterceptor", "WAF refresh failed or timed out.")
                throw IOException("WAF protection bypassed failed. Please try again later.")
            }
        }
        
        return response
    }

    private fun isWafChallenge(response: Response): Boolean {
        // EdgeOne WAF 质询通常返回 200 或 202
        if (response.code != 200 && response.code != 202) return false
        
        // 返回的内容类型应该是 text/html
        val contentType = response.header("Content-Type") ?: ""
        if (!contentType.contains("text/html", ignoreCase = true)) return false
        
        // EdgeOne 通常会带以下 Header 之一
        val server = response.header("server") ?: ""
        if (!server.contains("TencentEdgeOne", ignoreCase = true)) {
            // 如果不仅看 Server，还可以直接嗅探 body
        }
        
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
