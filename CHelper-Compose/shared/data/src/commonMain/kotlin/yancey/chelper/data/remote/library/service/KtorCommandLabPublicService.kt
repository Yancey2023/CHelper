package yancey.chelper.data.remote.library.service

import io.ktor.client.request.*
import io.ktor.http.*
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.model.LeaderboardData
import yancey.chelper.data.remote.library.model.UserProfileData
import yancey.chelper.data.remote.library.service.CommandLabPublicService.GetFunctionsResponse
import yancey.chelper.data.remote.library.service.CommandLabPublicService.LibraryLikeResponse
import yancey.chelper.domain.library.LibraryFunction

internal class KtorCommandLabPublicService(private val api: RemoteApi) : CommandLabPublicService {
    override suspend fun getFunctions(
        pageNum: Int,
        pageSize: Int,
        keyword: String?,
        type: Int,
        authorId: Int?,
    ): BaseResult<GetFunctionsResponse?> =
        api.request("library", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
            parameter("page_num", pageNum)
            parameter("page_size", pageSize)
            parameter("keyword", keyword)
            parameter("type", type)
            parameter("author_id", authorId)
        }

    override suspend fun getRecommendedLibrary(
        limit: Int,
        pageNum: Int?,
        pageSize: Int?,
    ): BaseResult<GetFunctionsResponse?> =
        api.request("library/recommend", HttpMethod.Get) {
            parameter("limit", limit)
            parameter("page_num", pageNum)
            parameter("page_size", pageSize)
        }

    override suspend fun getFunction(id: Int): BaseResult<LibraryFunction?> =
        api.request("library/detail/${id.toString().encodeURLPathPart()}", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
        }

    override suspend fun like(id: Int): BaseResult<LibraryLikeResponse?> =
        api.request("library/${id.toString().encodeURLPathPart()}/like", HttpMethod.Post) {}

    override suspend fun getLeaderboard(): BaseResult<LeaderboardData?> =
        api.request("api/leaderboard", HttpMethod.Get) {}

    override suspend fun getUserProfile(id: Int): BaseResult<UserProfileData?> =
        api.request("users/public/${id.toString().encodeURLPathPart()}", HttpMethod.Get) {}
}
