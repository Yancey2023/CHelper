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

package yancey.chelper.android.benchmark

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yancey.chelper.android.CHelperApplication
import yancey.chelper.data.remote.NetworkConfig
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.domain.library.LibraryFunction

/** Only compiled into isolated profiling variants. No requests leave this in-process transport. */
class BenchmarkApplication : CHelperApplication() {
    private val document by lazy {
        Json.encodeToString(
            LibraryFunction(
                id = 1,
                name = "性能命令库 1",
                version = "1.0",
                note = "固定的 2000 条命令，用于比较同一设备上的渲染性能。",
                content =
                    buildString {
                        appendLine("@name=性能命令库 1")
                        appendLine("@version=1.0")
                        appendLine("---基准命令链---")
                        repeat(2000) {
                            appendLine(">C")
                            appendLine("execute as @a at @s run say benchmark_$it")
                        }
                    },
            )
        )
    }

    override fun createNetwork() =
        NetworkServices(
            engineFactory = {
                MockEngine { request ->
                    val path = request.url.encodedPath
                    val body =
                        when {
                            path.endsWith("announcement.json") ->
                                """{"isEnable":false,"isForce":false}"""
                            path.endsWith("apk-latest.json") ->
                                """{"version_name":"26.2.0","version_code":87}"""
                            path.contains("/guest/") ->
                                """{"status":1,"message":"benchmark anonymous"}"""
                            path.contains("/library/detail/") -> {
                                delay(80)
                                """{"status":0,"data":$document}"""
                            }
                            path.endsWith("/library") || path.endsWith("/library/recommend") -> {
                                val page = request.url.parameters["page_num"]?.toIntOrNull() ?: 1
                                val size = request.url.parameters["page_size"]?.toIntOrNull() ?: 15
                                val items =
                                    ((page - 1) * size until minOf(page * size, 60)).map { index ->
                                        LibraryFunction(
                                            id = index + 1,
                                            name = "性能命令库 ${index + 1}",
                                            note = "固定渲染样本",
                                            version = "1.0",
                                            tags = listOf("基准", "命令"),
                                        )
                                    }
                                val list = Json.encodeToString(items)
                                """{"status":0,"data":{"list":$list,"page_num":$page,"page_size":$size,"total":60}}"""
                            }
                            path.contains("unread") -> """{"status":0,"data":{"unread_count":0}}"""
                            else -> """{"status":0,"data":{}}"""
                        }
                    respond(body, headers = headersOf("Content-Type", "application/json"))
                }
            },
            config =
                NetworkConfig(
                    appBaseUrl = "https://benchmark.invalid/",
                    labBaseUrl = "https://benchmark.invalid/",
                ),
        )
}
