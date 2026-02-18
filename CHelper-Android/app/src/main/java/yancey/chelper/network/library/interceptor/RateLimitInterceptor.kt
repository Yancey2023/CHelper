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
import okhttp3.Response
import yancey.chelper.android.common.util.Settings
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求速率限制拦截器
 * 
 * 限制对 abyssous.site 的请求频率，防止触发 WAF 或过载
 * 采用令牌桶算法
 */
class RateLimitInterceptor : Interceptor {

    private val lastRequestTime = AtomicLong(0)
    
    // 简单的漏桶/令牌桶实现
    // 这里简化为：确保两次请求间隔不小于 1000 / limit 毫秒
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        if (request.url.host == "abyssous.site") {
            val limit = Settings.INSTANCE.requestRateLimit ?: 5
            if (limit > 0) {
                val minInterval = 1000L / limit
                synchronized(this) {
                    val now = System.currentTimeMillis()
                    val last = lastRequestTime.get()
                    val nextAllowed = last + minInterval
                    
                    if (now < nextAllowed) {
                        val waitTime = nextAllowed - now
                        if (waitTime > 0) {
                            try {
                                Log.d("RateLimit", "Throttling request by ${waitTime}ms")
                                Thread.sleep(waitTime)
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw IOException("Request interrupted during rate limiting", e)
                            }
                        }
                    }
                    lastRequestTime.set(System.currentTimeMillis())
                }
            }
        }
        
        return chain.proceed(request)
    }
}
