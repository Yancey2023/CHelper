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

package yancey.chelper.network.library.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.LibraryFunction

/**
 * 命令库公开 API 接口，负责与命令库相关的非敏感操作，如获取命令库列表、详情、点赞以及查看用户公开资料和排行榜等。
 */
@Suppress("unused")
interface CommandLabPublicService {

    /**
     * 获取命令库列表的响应数据结构。
     *
     * @property functions 命令库函数的列表
     * @property currentPage 当前请求的页码
     * @property perPage 每页返回的条目数
     * @property totalCount 符合条件的总条目数
     */
    @Serializable
    class GetFunctionsResponse(
        @SerialName("list") var functions: MutableList<LibraryFunction?>? = null,
        @SerialName("pageNum") var currentPage: Int? = null,
        @SerialName("pageSize") var perPage: Int? = null,
        @SerialName("total") var totalCount: Int? = null,
    )

    /**
     * 获取公开的命令库函数列表。
     *
     * @param pageNum 请求的页码
     * @param pageSize 每页包含的条目数
     * @param keyword 可选的搜索关键词
     * @param type 列表类型（默认0表示全部公开库，1表示用户的私有库）
     * @return 包含命令库分页数据的响应结果
     */
    @GET("library")
    suspend fun getFunctions(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int,
        @Query("keyword") keyword: String?,
        @Query("type") type: Int = 0
    ): BaseResult<GetFunctionsResponse?>

    /**
     * 获取指定命令库函数的详细信息。
     *
     * @param id 命令库函数的唯一标识 ID
     * @return 包含该命令库函数详情的响应结果
     */
    @GET("library/detail/{id}")
    suspend fun getFunction(
        @Path("id") id: Int
    ): BaseResult<LibraryFunction?>

    /**
     * 点赞操作的响应数据结构。
     *
     * @property likeCount 点赞后的最新总点赞数
     * @property isLiked 当前用户是否已点赞
     */
    @Serializable
    class LibraryLikeResponse {
        var likeCount: Int? = null
        var isLiked: Boolean? = null
    }

    /**
     * 对指定的命令库函数进行点赞或取消点赞操作。
     *
     * @param id 命令库函数的唯一标识 ID
     * @return 包含最新点赞状态和点赞总数的响应结果
     */
    @POST("library/{id}/like")
    suspend fun like(
        @Path("id") id: Int
    ): BaseResult<LibraryLikeResponse?>

    /**
     * 获取命令库排行榜数据。
     *
     * @return 包含排行榜用户列表的响应结果
     */
    @GET("api/leaderboard")
    suspend fun getLeaderboard(): BaseResult<yancey.chelper.network.library.data.LeaderboardData?>

    /**
     * 获取指定用户的公开资料信息。
     *
     * @param id 用户的唯一标识 ID
     * @return 包含该用户公开资料的响应结果
     */
    @GET("users/public/{id}")
    suspend fun getUserProfile(
        @Path("id") id: Int
    ): BaseResult<yancey.chelper.network.library.data.UserProfileData?>
}
