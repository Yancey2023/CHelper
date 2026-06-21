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
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 表示作者信息的数据模型
 * 
 * @property id 作者的用户 ID
 * @property name 作者的名称
 * @property tier 作者的等级或段位
 */
@Serializable
data class AuthorInfo(
    var id: Int? = null,
    var name: String? = null,
    var tier: Int? = null,
    @SerialName("user_title") var userTitle: String? = null
)

/**
 * 命令库函数的数据模型，表示单个命令库文件的详细信息
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
class LibraryFunction(
    var id: Int? = null,
    var uuid: String? = null,
    var name: String? = null,
    var content: String? = null,
    @Serializable(with = AuthorSerializer::class) var author: AuthorInfo? = null,
    var note: String? = null,
    var tags: List<String>? = null,
    var version: String? = null,
    @Serializable(with = LenientStringSerializer::class) @SerialName("create_time") var createdAt: String? = null,
    var preview: String? = null,
    @SerialName("like_count") var likeCount: Int? = null,
    @SerialName("is_liked") var isLiked: Boolean? = null,
    @SerialName("has_public_version") var hasPublicVersion: Boolean? = null,
    @SerialName("is_publish") var isPublish: Boolean? = null,
    @SerialName("is_owner") var isOwner: Boolean? = null,
    @SerialName("chain_data") var chainData: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("auto_sync") var autoSync: Boolean? = false,
    /**
     * 后端下发的"私有库相对公开版本是否未同步"标记。
     * - 仅在私有库列表/详情接口里出现，公开库不带。
     * - 后端为权威：私有库被 update 置 true；release/sync 通过后置 false。
     */
    @SerialName("has_unsynced_changes") var hasUnsyncedChanges: Boolean? = null,
    /**
     * 纯本地字段：本地草稿相对云端私有版本是否存在未同步的改动。
     * - 仅在"本地库"场景有意义（即已上传过云端、拿到 uuid 的本地副本）。
     * - 本地编辑保存时置 true；上传/同步到云端成功后置 false。
     * - 后端 JSON 不会返回这个字段，反序列化默认 false，不会影响远端数据。
     * - 用一个不会和后端冲突的 snake_case 名字，避免未来撞键。
     */
    @SerialName("local_unsynced") var localUnsynced: Boolean = false
) {
    /**
     * 获取作者的展示名称，主要用于向后兼容
     *
     * @return 作者的名称，如果不存在则返回 null
     */
    val authorName: String?
        get() = author?.name
}

/**
 * 兼容新旧两种 author JSON 格式的序列化器
 * JsonObject { id, name, tier, user_title } → AuthorInfo(id, name, tier, userTitle)
 * 纯字符串 "xxx" → AuthorInfo(name = "xxx")
 * null → null
 *
 * 序列化阶段始终按对象写出，避免 #36 那种把 AuthorInfo 退化成 name 字符串
 * 导致 round-trip 丢字段、上传/编辑流程数据残缺的情况。
 */
object AuthorSerializer : KSerializer<AuthorInfo?> {
    private val delegate = AuthorInfo.serializer().nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): AuthorInfo? {
        require(decoder is JsonDecoder) { "This serializer can only be used with JSON" }
        val jsonElement = decoder.decodeJsonElement()
        return when {
            jsonElement is JsonNull -> null
            jsonElement is JsonObject -> {
                AuthorInfo(
                    id = jsonElement["id"]?.jsonPrimitive?.intOrNull,
                    name = jsonElement["name"]?.jsonPrimitive?.content,
                    tier = jsonElement["tier"]?.jsonPrimitive?.intOrNull,
                    userTitle = jsonElement["user_title"]?.jsonPrimitive?.contentOrNull
                )
            }
            // 旧版 API 直接返回字符串作者名
            jsonElement.jsonPrimitive.isString -> AuthorInfo(name = jsonElement.jsonPrimitive.content)
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: AuthorInfo?) {
        delegate.serialize(encoder, value)
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

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}
