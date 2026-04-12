package yancey.chelper.network.library.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户公开资料的数据模型。
 *
 * @property id 用户的唯一标识符
 * @property nickname 用户的昵称
 * @property avatarUrl 用户的头像 URL
 * @property tier 用户的等级或段位
 * @property signature 个性签名
 * @property homepage 个人主页链接
 * @property totalPublicFunctions 用户公开的命令库函数总数
 * @property totalLikes 用户获得的全部点赞数
 * @property recentFunctions 用户最近发布的命令库函数列表
 * @property email 用户的电子邮箱
 * @property createdAt 账号的注册/创建时间
 * @property tierExpiresAt 用户段位或特殊头衔的过期时间
 */
@Serializable
data class UserProfileData(
    var id: Int? = null,
    var nickname: String? = null,
    @SerialName("avatar_url") var avatarUrl: String? = null,
    var tier: Int? = null,
    var signature: String? = null,
    var homepage: String? = null,
    @SerialName("total_public_functions") var totalPublicFunctions: Int? = null,
    @SerialName("total_likes") var totalLikes: Int? = null,
    @SerialName("recent_functions") var recentFunctions: List<LibraryFunction>? = null,
    var email: String? = null,
    @SerialName("created_at") var createdAt: String? = null,
    @SerialName("tier_expires_at") var tierExpiresAt: String? = null
)

/**
 * 更新用户个人资料的请求模型。
 *
 * @property nickname 新的用户昵称
 * @property avatarUrl 新的头像 URL
 * @property homepage 新的个人主页链接
 * @property signature 新的个性签名
 */
@Serializable
data class UpdateProfileRequest(
    var nickname: String? = null,
    @SerialName("avatar_url") var avatarUrl: String? = null,
    var homepage: String? = null,
    var signature: String? = null
)
