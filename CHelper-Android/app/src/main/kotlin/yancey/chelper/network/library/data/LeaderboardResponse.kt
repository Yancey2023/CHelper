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
