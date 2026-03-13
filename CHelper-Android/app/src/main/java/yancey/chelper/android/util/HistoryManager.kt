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

package yancey.chelper.android.util

import android.content.Context
import java.io.File

class HistoryManager private constructor(private val file: File) {
    private var historyList = ArrayDeque<String>()

    init {
        if (file.exists()) {
            try {
                val content = file.readBytes().decodeToString()
                historyList.addAll(content.split("\n"))
            } catch (_: Exception) {

            }
        }
    }

    /**
     * 添加新内容到历史记录
     * 
     * @param content 内容
     */
    fun add(content: String) {
        if (content.isEmpty()) {
            return
        }
        historyList.remove(content)
        if (historyList.size >= MAX_SIZE) {
            historyList.removeLast()
        }
        historyList.addFirst(content)

    }

    val all: List<String>
        /**
         * 获取全部历史记录
         * 
         * @return 历史记录列表
         */
        get() = historyList.toList()

    /**
     * 获取当前历史记录数量
     * 
     * @return 记录数量
     */
    fun size(): Int {
        return historyList.size
    }

    fun save() {
        file.outputStream().write(historyList.joinToString("\n").toByteArray())
    }

    companion object {
        private const val MAX_SIZE = 1000
        private var INSTANCE: HistoryManager? = null

        fun getInstance(context: Context): HistoryManager {
            if (INSTANCE == null) {
                INSTANCE = HistoryManager(context.dataDir.resolve("history.txt"))
            }
            return INSTANCE!!
        }
    }
}