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

package yancey.chelper.network.chelper.data

class Announcement {
    var isEnable: Boolean? = null
    var isForce: Boolean? = null
    var isBigDialog: Boolean? = null
    var isEnableCommandLab: Boolean? = null
    var title: String? = null
    var message: String? = null
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Announcement

        if (isEnable != other.isEnable) return false
        if (isForce != other.isForce) return false
        if (isBigDialog != other.isBigDialog) return false
        if (title != other.title) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isEnable?.hashCode() ?: 0
        result = 31 * result + (isForce?.hashCode() ?: 0)
        result = 31 * result + (isBigDialog?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }


}
