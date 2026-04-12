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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表示作者信息的数据模型。
 *
 * API 中存在两种格式：
 * - 旧式格式：纯字符串 "作者名"
 * - 新式格式：JSON 对象 `{ "id": 123, "name": "作者名", "tier": 1 }`
 *
 * 此结构体统一承接两种格式，实现对旧版 MCD 数据的向后兼容。
 *
 * @property id 作者的用户 ID
 * @property name 作者的名称
 * @property tier 作者的等级或段位
 */
@Serializable
data class AuthorInfo(
    var id: Int? = null,
    var name: String? = null,
    var tier: Int? = null
)

/**
 * 命令库函数的数据模型，表示单个命令库文件的详细信息。
 *
 * @property id 函数在数据库中的自增 ID
 * @property uuid 函数的全局唯一标识符
 * @property name 函数名称
 * @property content 函数的具体内容或代码
 * @property author 函数作者的信息，使用自定义的 [AuthorSerializer] 进行解析
 * @property note 附加的说明或备注信息
 * @property tags 该函数关联的标签列表
 * @property version 该函数适用的版本信息
 * @property createdAt 函数的创建时间，使用 [LenientStringSerializer] 支持格式兼容
 * @property preview 函数的预览内容，通常是部分截断的代码
 * @property likeCount 获得的点赞总数
 * @property isLiked 当前登录用户是否已点赞该函数
 * @property hasPublicVersion 指示该私有库是否拥有对应的公开版本
 * @property isPublish 指示该函数是否已发布（针对公开/私有状态）
 */
@Serializable
@Suppress("unused")
class LibraryFunction(
    var id: Int? = null,
    var uuid: String? = null,
    var name: String? = null,
    var content: String? = null,
    @Serializable(with = AuthorSerializer::class) var author: AuthorInfo? = null,
    var note: String? = null,
    var tags: List<String>? = null,
    var version: String? = null,
    @Serializable(with = LenientStringSerializer::class) @SerialName("createTime") var createdAt: String? = null,
    var preview: String? = null,
    var likeCount: Int? = null,
    var isLiked: Boolean? = null,
    var hasPublicVersion: Boolean? = null,
    var isPublish: Boolean? = null
) {
    /**
     * 获取作者的展示名称，主要用于向后兼容。
     *
     * @return 作者的名称，如果不存在则返回 null
     */
    val authorName: String?
        get() = author?.name
}

/**
 * 兼容新旧两种 author JSON 格式的序列化器。
 * - JsonObject { id, name, tier } → AuthorInfo(id, name, tier)
 * - 纯字符串 "xxx" → AuthorInfo(name = "xxx")
 * - null → null
 */
object AuthorSerializer : KSerializer<AuthorInfo?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthorInfo") {
        element<Int?>("id")
        element<String?>("name")
        element<Int?>("tier")
    }

    override fun deserialize(decoder: Decoder): AuthorInfo? {
        require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }
        val jsonElement = decoder.decodeJsonElement()
        return when {
            jsonElement is JsonNull -> null
            jsonElement is JsonObject -> {
                AuthorInfo(
                    id = jsonElement["id"]?.jsonPrimitive?.intOrNull,
                    name = jsonElement["name"]?.jsonPrimitive?.content,
                    tier = jsonElement["tier"]?.jsonPrimitive?.intOrNull
                )
            }
            // 旧版 API 直接返回字符串作者名
            jsonElement.jsonPrimitive.isString -> AuthorInfo(name = jsonElement.jsonPrimitive.content)
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: AuthorInfo?) {
        if (value != null) {
            encoder.encodeString(value.name ?: "")
        }
    }
}

/**
 * 宽松的字符串序列化器，允许接收数字或布尔并转换为字符串
 */
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LenientString")

    override fun deserialize(decoder: Decoder): String? {
        require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }
        val jsonElement = decoder.decodeJsonElement()
        return if (jsonElement is JsonNull) {
            null
        } else {
            jsonElement.jsonPrimitive.content
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}
