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

package yancey.chelper.network.library.data

import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
class LibraryFunction {
    var id: Int? = null // 函数ID
    var uuid: String? = null // 函数UUID
    var name: String? = null // 函数名称
    var content: String? = null // 函数内容
    var author: String? = null // 作者
    var note: String? = null // 说明
    var tags: List<String>? = null // 标签
    var version: String? = null // 版本号

    @Suppress("PropertyName")
    var created_at: String? = null // 创建时间，例：2025-02-03 18:45:43
    var preview: String? = null // 命令预览

    @Suppress("PropertyName")
    var like_count: Int? = null // 点赞总数

    @Suppress("PropertyName")
    var is_liked: Boolean? = null // 当前设备是否已点赞

    @Suppress("PropertyName")
    var user_key: String? = null // 随机生成的密钥

    @Suppress("PropertyName")
    var backup_file: String? = null // 备份文件名
}
