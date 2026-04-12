package yancey.chelper.network.library.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 排行榜用户的数据模型。
 *
 * @property id 用户的唯一标识符
 * @property avatarUrl 用户的头像 URL
 * @property nickname 用户的昵称
 * @property tier 用户的等级或段位
 * @property totalLikes 用户获得的总点赞数
 * @property totalFunctions 用户发布的命令库函数总数
 */
@Serializable
data class LeaderboardUser(
    var id: Int? = null,
    @SerialName("avatar_url") var avatarUrl: String? = null,
    var nickname: String? = null,
    var tier: Int? = null,
    @SerialName("user_title") var userTitle: String? = null,
    @SerialName("total_likes") var totalLikes: Int? = null,
    @SerialName("total_functions") var totalFunctions: Int? = null
)

/**
 * 排行榜数据的响应模型。
 *
 * @property leaderboard 包含排行榜用户的列表
 */
@Serializable
data class LeaderboardData(
    var leaderboard: List<LeaderboardUser>? = null
)
