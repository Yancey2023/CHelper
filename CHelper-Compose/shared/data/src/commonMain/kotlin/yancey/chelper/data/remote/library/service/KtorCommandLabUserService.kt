package yancey.chelper.data.remote.library.service

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonElement
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.UploadFile
import yancey.chelper.data.remote.library.model.ActivityConfig
import yancey.chelper.data.remote.library.model.ActivityLedgerPage
import yancey.chelper.data.remote.library.model.ActivitySummary
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.model.MessageListResponse
import yancey.chelper.data.remote.library.model.RedeemActivityRequest
import yancey.chelper.data.remote.library.model.RedeemActivityResponse
import yancey.chelper.data.remote.library.model.RewardRedemptions
import yancey.chelper.data.remote.library.model.TierDetails
import yancey.chelper.data.remote.library.model.UnreadCountResponse
import yancey.chelper.data.remote.library.model.UpdateProfileRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.FavoriteLibraryResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.GuestAuthRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.LibraryShareResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.LoginRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.LoginResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.QuotaResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.RegisterRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.ReportRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.SendCodeRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.UpdateLibraryRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.UploadAvatarResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.UploadLibraryRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.UploadLibraryResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.WebSsoAuthorizeRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.WebSsoAuthorizeResponse
import yancey.chelper.domain.library.LibraryFunction

internal class KtorCommandLabUserService(private val api: RemoteApi) : CommandLabUserService {
    override suspend fun guestLogin(request: GuestAuthRequest): BaseResult<LoginResponse?> =
        api.request("guest/login", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun guestRegister(request: GuestAuthRequest): BaseResult<LoginResponse?> =
        api.request("guest/register", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun guestMigrate(request: GuestAuthRequest): BaseResult<JsonElement?> =
        api.request("guest/migrate", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun sendCode(request: SendCodeRequest): BaseResult<JsonElement?> =
        api.request("register/sendCode", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun register(request: RegisterRequest): BaseResult<JsonElement?> =
        api.request("register", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun login(request: LoginRequest): BaseResult<LoginResponse?> =
        api.request("register/login", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun authorizeWebSso(
        request: WebSsoAuthorizeRequest
    ): BaseResult<WebSsoAuthorizeResponse?> =
        api.request("auth/chelper/web-sso/authorize", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun uploadAvatar(file: UploadFile): BaseResult<UploadAvatarResponse?> =
        api.request("avatar", HttpMethod.Post) { setBody(file.multipart()) }

    override suspend fun uploadLibrary(
        request: UploadLibraryRequest,
        authorization: String?,
    ): BaseResult<UploadLibraryResponse?> =
        api.request("library/upload", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
            authorization?.let { header("Authorization", it) }
        }

    override suspend fun updateLibrary(
        id: Int,
        request: UpdateLibraryRequest,
        authorization: String?,
    ): BaseResult<JsonElement?> =
        api.request("library/${id.toString().encodeURLPathPart()}", HttpMethod.Put) {
            contentType(ContentType.Application.Json)
            setBody(request)
            authorization?.let { header("Authorization", it) }
        }

    override suspend fun updateProfile(
        id: Int,
        request: UpdateProfileRequest,
    ): BaseResult<JsonElement?> =
        api.request("users/${id.toString().encodeURLPathPart()}", HttpMethod.Put) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun getMyLibraries(
        type: Int,
        pageNum: Int,
        pageSize: Int,
        authorization: String?,
    ): BaseResult<CommandLabPublicService.GetFunctionsResponse?> =
        api.request("library", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
            parameter("type", type)
            parameter("page_num", pageNum)
            parameter("page_size", pageSize)
            authorization?.let { header("Authorization", it) }
        }

    override suspend fun getFavoriteLibraries(
        type: Int,
        pageNum: Int,
        pageSize: Int,
    ): BaseResult<CommandLabPublicService.GetFunctionsResponse?> =
        api.request("library", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
            parameter("type", type)
            parameter("page_num", pageNum)
            parameter("page_size", pageSize)
        }

    override suspend fun getPrivateFunction(id: Int): BaseResult<LibraryFunction?> =
        api.request("library/detail/${id.toString().encodeURLPathPart()}", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
        }

    override suspend fun releaseToPublic(
        id: Int,
        body: Map<String, String>,
    ): BaseResult<JsonElement?> =
        api.request("library/${id.toString().encodeURLPathPart()}/release", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    override suspend fun syncToPublic(id: Int): BaseResult<JsonElement?> =
        api.request("library/${id.toString().encodeURLPathPart()}/sync", HttpMethod.Post) {}

    override suspend fun deleteLibrary(id: Int): BaseResult<JsonElement?> =
        api.request("library/${id.toString().encodeURLPathPart()}", HttpMethod.Delete) {}

    override suspend fun toggleFavorite(id: Int): BaseResult<FavoriteLibraryResponse?> =
        api.request("library/${id.toString().encodeURLPathPart()}/favorite", HttpMethod.Post) {}

    override suspend fun createLibraryShare(
        id: Int,
        importToLocal: Boolean,
    ): BaseResult<LibraryShareResponse?> =
        api.request("library/${id.toString().encodeURLPathPart()}/share", HttpMethod.Post) {
            parameter("import_to_local", importToLocal)
        }

    override suspend fun getQuota(): BaseResult<QuotaResponse?> =
        api.request("library/quota", HttpMethod.Get) {}

    override suspend fun getActivityConfig(): BaseResult<ActivityConfig?> =
        api.request("activity/config", HttpMethod.Get) {}

    override suspend fun getMyActivity(): BaseResult<ActivitySummary?> =
        api.request("activity/me", HttpMethod.Get) {}

    override suspend fun getActivityLedger(
        cursor: Int?,
        limit: Int,
    ): BaseResult<ActivityLedgerPage?> =
        api.request("activity/ledger", HttpMethod.Get) {
            parameter("cursor", cursor)
            parameter("limit", limit)
        }

    override suspend fun getActivityRedemptions(): BaseResult<RewardRedemptions?> =
        api.request("activity/redemptions", HttpMethod.Get) {}

    override suspend fun redeemActivityProduct(
        productId: String,
        request: RedeemActivityRequest,
    ): BaseResult<RedeemActivityResponse?> =
        api.request(
            "activity/redeem/${productId.toString().encodeURLPathPart()}",
            HttpMethod.Post,
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun getMyTierDetails(): BaseResult<TierDetails?> =
        api.request("activity/tiers", HttpMethod.Get) {}

    override suspend fun getMessages(
        page: Int,
        pageSize: Int,
        unreadOnly: Boolean,
    ): BaseResult<MessageListResponse?> =
        api.request("message/", HttpMethod.Get) {
            header("Cache-Control", "no-cache")
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("unread_only", unreadOnly)
        }

    override suspend fun getUnreadCount(): BaseResult<UnreadCountResponse?> =
        api.request("message/unread-count", HttpMethod.Get) {}

    override suspend fun markMessageRead(id: Int): BaseResult<JsonElement?> =
        api.request("message/${id.toString().encodeURLPathPart()}/read", HttpMethod.Put) {}

    override suspend fun deleteMessage(id: Int): BaseResult<JsonElement?> =
        api.request("message/${id.toString().encodeURLPathPart()}", HttpMethod.Delete) {}

    override suspend fun submitReport(request: ReportRequest): BaseResult<JsonElement?> =
        api.request("report", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
}
