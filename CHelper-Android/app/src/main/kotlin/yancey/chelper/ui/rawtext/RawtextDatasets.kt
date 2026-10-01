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

package yancey.chelper.ui.rawtext

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import yancey.chelper.core.MainPackProvider

/**
 * 基岩版数据集：选择器/翻译补全统一走内核（合成主包表），此对象仅保留预览需要的
 * 翻译识别符中文表（text/translate.json，随启用段惰性加载）。
 */
object RawtextDatasets {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var translateMap: Map<String, String> = emptyMap()
        private set

    @Volatile
    private var translateLoaded = false

    @Volatile
    private var translateLoadedSegment: String? = null

    /** 从主包段延迟加载翻译识别符（text/translate.json）；段切换后自动重载 */
    suspend fun loadTranslateFromMainPack(context: Context, segment: String) {
        val parts = segment.split('/')
        if (parts.size != 2) {
            return
        }
        withContext(Dispatchers.IO) {
            if (translateLoaded && translateLoadedSegment == segment) {
                return@withContext
            }
            val pack = MainPackProvider.get(context) ?: throw IllegalStateException("main pack unavailable")
            val bytes = pack.readFile(parts[0], parts[1], "text/translate.json") ?: return@withContext
            val obj = json.parseToJsonElement(bytes.decodeToString()).jsonObject
            translateMap = obj.entries.map { (k, v) -> k to ((v as? JsonPrimitive)?.content ?: k) }.toMap()
            translateLoaded = true
            translateLoadedSegment = segment
        }
    }
}
