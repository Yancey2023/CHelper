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

import io.ktor.http.Url
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize admission, never hold a lock while sending or obtaining credentials. */
class RateLimitInterceptor(
    val limit: Int,
    private val millis: () -> Long = run {
        val origin = TimeSource.Monotonic.markNow();
        { origin.elapsedNow().inWholeMilliseconds }
    },
) {
    private val mutex = Mutex()
    private var last: Long? = null

    suspend fun await(url: Url) {
        if (limit <= 0 || url.host != "abyssous.site") return
        mutex.withLock {
            last?.let { delay((it + 1000L / limit - millis()).coerceAtLeast(0)) }
            last = millis()
        }
    }
}
