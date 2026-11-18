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

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.peek
import okio.IOException
import yancey.chelper.data.remote.waf.BrowserVerificationState

class WafInterceptor(private val origin: Url, private val browser: BrowserVerificationState) {

    private data class BrowserHeaders(
        val cookie: String,
        val agent: String?,
        val previousAgent: String?,
    )

    private val browserHeaders = AttributeKey<BrowserHeaders>("CHelperBrowserHeaders")

    private fun attach(request: HttpRequestBuilder, cookie: String) {
        val prior = request.attributes.getOrNull(browserHeaders)
        val previous = if (prior == null) request.headers["User-Agent"] else prior.previousAgent
        val agent = browser.current?.userAgent
        request.headers["Cookie"] = cookie
        agent?.let { request.headers["User-Agent"] = it }
        request.attributes.put(browserHeaders, BrowserHeaders(cookie, agent, previous))
    }

    fun prepare(request: HttpRequestBuilder) {
        if (!request.url.build().sameOrigin(origin)) {
            // Redirects copy request headers. Never carry our browser credentials to another
            // origin.
            request.attributes.getOrNull(browserHeaders)?.let { owned ->
                if (request.headers["Cookie"] == owned.cookie) request.headers.remove("Cookie")
                if (owned.agent != null && request.headers["User-Agent"] == owned.agent) {
                    request.headers.remove("User-Agent")
                    owned.previousAgent?.let { request.headers["User-Agent"] = it }
                }
                request.attributes.remove(browserHeaders)
            }
            return
        }
        if (request.headers["Cookie"] == null)
            browser.current?.cookie?.takeIf { it.isNotEmpty() }?.let { attach(request, it) }
    }

    suspend fun isChallenge(response: HttpResponse): Boolean {
        if (!response.call.request.url.sameOrigin(origin)) return false
        if (
            response.status.value !in listOf(200, 202) ||
                response.headers["Content-Type"]?.contains("text/html", true) != true
        )
            return false
        val channel = response.bodyAsChannel()
        channel.awaitContent(2048)
        val count = minOf(2048, channel.availableForRead)
        if (count == 0) return false
        val preview = channel.peek(count)?.toByteArray()?.decodeToString().orEmpty()
        return preview.contains("EO_Bot_Ssid") || preview.contains("__tst_status=")
    }

    suspend fun refresh(request: HttpRequestBuilder) {
        val verified =
            browser.refresh(request.headers["Cookie"]) ?: throw IOException("站点浏览器验证失败，请稍后重试")
        attach(request, verified.cookie)
    }
}
