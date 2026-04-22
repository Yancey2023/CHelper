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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * 站内信消息体。
 * 后端 message_repository 返回的 dict 直接映射。
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class SiteMessage(
    var id: Int? = null,
    var title: String? = null,
    var content: String? = null,
    @JsonNames("msg_type", "msgType") var msgType: String? = null,
    @JsonNames("sender_id", "senderId") var senderId: Int? = null,
    @JsonNames("created_at", "createdAt") var createdAt: String? = null,
    @JsonNames("is_read", "isRead") var isRead: Boolean? = null,
    @JsonNames("read_at", "readAt") var readAt: String? = null,
    @JsonNames("is_global", "isGlobal") var isGlobal: Boolean? = null,
)

/**
 * 站内信列表分页响应。
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class MessageListResponse(
    var messages: List<SiteMessage>? = null,
    var total: Int? = null,
    var page: Int? = null,
    @JsonNames("page_size", "pageSize") var pageSize: Int? = null,
)

/**
 * 未读计数响应。
 */
@Serializable
class UnreadCountResponse(
    var count: Int? = null,
)
