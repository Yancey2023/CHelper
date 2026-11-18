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

package yancey.chelper.ui.designsystem.text

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** Retains backend ISO text and formats timestamps using the host's local time zone. */
fun String?.formatUnixTime(
    dateOnly: Boolean = false,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (this.isNullOrEmpty()) return "未知"
    if (contains("-")) return if (dateOnly) take(10) else replace("T", " ").take(19)
    val timestamp = toLongOrNull() ?: return this
    val ms = if (timestamp < 100000000000L) timestamp * 1000 else timestamp
    val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone)
    fun Int.two() = toString().padStart(2, '0')
    val day =
        "${date.year.toString().padStart(4, '0')}-${date.month.number.two()}-${date.day.two()}"
    return if (dateOnly) day
    else "$day ${date.hour.two()}:${date.minute.two()}:${date.second.two()}"
}
