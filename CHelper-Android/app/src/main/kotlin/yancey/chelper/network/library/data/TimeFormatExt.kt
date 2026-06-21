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

package yancey.chelper.network.library.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 格式化后端返回的时间字符串
 * 如果是形如 "2024-04-12T12:00:00" 的 ISO 字符串，进行截取
 * 如果是 Unix 时间戳（秒或毫秒），则转换为本地时间格式
 */
fun String?.formatUnixTime(dateOnly: Boolean = false): String {
    if (this.isNullOrEmpty()) return "未知"
    if (this.contains("-")) {
        return if (dateOnly) this.take(10) else this.replace("T", " ").take(19)
    }
    val ms = this.toLongOrNull() ?: return this
    val actualMs = if (ms < 100000000000L) ms * 1000 else ms
    val pattern = if (dateOnly) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm:ss"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(actualMs))
}
