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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
@Suppress("unused")
class LibraryFunction {
    var id: Int? = null // 函数ID
    var uuid: String? = null // 函数UUID
    var name: String? = null // 函数名称
    var content: String? = null // 函数内容

    @Serializable(with = AuthorSerializer::class)
    var author: String? = null // 作者

    var note: String? = null // 说明
    var tags: List<String>? = null // 标签
    var version: String? = null // 版本号

    @SerialName("created_at")
    var createdAt: String? = null // 创建时间，例：2025-02-03 18:45:43
    var preview: String? = null // 命令预览

    @SerialName("like_count")
    var likeCount: Int? = null // 点赞总数

    @SerialName("is_liked")
    var isLiked: Boolean? = null // 当前设备是否已点赞

    var hasPublicVersion: Boolean? = null // 是否已有公开版本（仅私有库返回）

    var isPublish: Boolean? = null // 当前是否为公开状态（仅私有库返回）
}

object AuthorSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("author", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }
        val jsonElement = decoder.decodeJsonElement()
        return when {
            jsonElement is JsonObject -> jsonElement["name"]?.jsonPrimitive?.content
            jsonElement is JsonNull -> null
            jsonElement.jsonPrimitive.isString -> jsonElement.jsonPrimitive.content
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        }
    }
}
