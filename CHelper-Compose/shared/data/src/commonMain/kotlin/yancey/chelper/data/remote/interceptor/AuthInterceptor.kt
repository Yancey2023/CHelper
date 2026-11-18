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
import io.ktor.http.Url
import yancey.chelper.domain.auth.TokenProvider

internal fun Url.sameOrigin(other: Url) =
    protocol == other.protocol && host == other.host && port == other.port

class AuthInterceptor(private val origin: Url, private val tokens: TokenProvider) {
    suspend fun apply(request: HttpRequestBuilder) {
        if (!request.url.build().sameOrigin(origin)) return
        request.headers["X-Client"] = "chelper-android"
        if (request.headers["User-Agent"] == null) request.headers["User-Agent"] = "CHelper-Android"
        if (
            !isAuthEndpoint(request.url.build().encodedPath) &&
                request.headers["Authorization"].isNullOrEmpty()
        ) {
            tokens.token()?.takeIf(String::isNotBlank)?.let {
                request.headers["Authorization"] = "Bearer $it"
            }
        }
    }

    companion object {
        internal fun isAuthEndpoint(path: String) =
            listOf("/guest/login", "/guest/register", "/login", "/register", "/captcha").any {
                path.contains(it, ignoreCase = true)
            }
    }
}
