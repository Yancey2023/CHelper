/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
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
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Query
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.LibraryFunction

/**
 * CommandLab 用户系统 API
 * -by Akanyi
 * 包含注册、登录、用户资料管理等功能
 */
@Suppress("unused")
interface CommandLabUserService {


    // -------------------------------------------------------------
    // Guest System
    // -------------------------------------------------------------

    @Serializable
    class GuestAuthRequest(
        var fingerprint: String? = null,
        @SerialName("auth_code") var authCode: String? = null
    )

    @POST("guest/login")
    suspend fun guestLogin(@Body request: GuestAuthRequest): BaseResult<LoginResponse?>

    @POST("guest/register")
    suspend fun guestRegister(@Body request: GuestAuthRequest): BaseResult<LoginResponse?>

    @POST("guest/migrate")
    suspend fun guestMigrate(@Body request: GuestAuthRequest): BaseResult<Void?>

    // -------------------------------------------------------------
    // Official User System
    // -------------------------------------------------------------

    // 注册相关

    /**
     * 发送邮箱验证码请求体
     */
    @Serializable
    class SendCodeRequest(
        @SerialName("special_code") var specialCode: String? = null,
        var type: Int? = null,  // 0=注册, 1=更新密码, 2=找回密码
        var email: String? = null,
        var phone: String? = null,
        var lang: String? = "zh-CN"
    ) {
        companion object {
            const val TYPE_REGISTER = 0
            const val TYPE_UPDATE_PASSWORD = 1
            const val TYPE_RESET_PASSWORD = 2
        }
    }

    /**
     * 发送邮箱验证码
     * 
     * 需要先完成人机验证获取 special_code
     */
    @POST("register/sendCode")
    suspend fun sendCode(@Body request: SendCodeRequest): BaseResult<Void?>

    /**
     * 注册请求体
     */
    /**
     * 注册请求体
     */
    @Serializable
    class RegisterRequest(
        @SerialName("special_code") var specialCode: String? = null,
        var code: String? = null,
        var email: String? = null,
        var phone: String? = null,
        var nickname: String? = null,
        var password: String? = null,
        @SerialName("android_id") var androidId: String? = null
    )

    /**
     * 提交注册
     */
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): BaseResult<kotlinx.serialization.json.JsonElement?>

    // 登录相关

    /**
     * 登录请求体
     */
    @Serializable
    class LoginRequest(
        var account: String? = null,
        var password: String? = null
    )

    /**
     * 用户信息
     */
    @Serializable
    class User(
        var id: Int? = null,
        var email: String? = null,
        var nickname: String? = null,
        @SerialName("is_guest") var isGuest: Boolean? = null,
        @SerialName("is_admin") var isAdmin: Boolean? = null,
        @SerialName("is_moderator") var isModerator: Boolean? = null,
        @SerialName("is_verified") var isVerified: Boolean? = null,
        @SerialName("created_at") var createdAt: String? = null,
        @SerialName("gravatar_url") var gravatarUrl: String? = null,
    )

    /**
     * 登录响应
     */
    @Serializable
    class LoginResponse(
        @SerialName("user_id") var userId: Int? = null, // Added from upstream
        var token: String? = null,
        var user: User? = null
    )

    /**
     * 正式用户登录
     */
    @POST("register/login")
    suspend fun login(@Body request: LoginRequest): BaseResult<LoginResponse?>


    // -------------------------------------------------------------
    // Avatar
    // -------------------------------------------------------------

    @Serializable
    class UploadAvatarResponse(
        @SerialName("avatar_url") var avatarUrl: String? = null
    )

    @Multipart
    @POST("avatar")
    suspend fun uploadAvatar(
        @Part file: okhttp3.MultipartBody.Part
    ): BaseResult<UploadAvatarResponse?>

    // -------------------------------------------------------------
    // Library Management
    // -------------------------------------------------------------

    @Serializable
    class UploadLibraryRequest(
        var content: String? = null,
        // 上传固定为私有草稿，发布走 /release 接口
        @SerialName("is_publish") var isPublish: Boolean = false
    )

    @Serializable
    class UploadLibraryResponse(
        var uuid: String? = null
    )

    @POST("library/upload")
    suspend fun uploadLibrary(@Body request: UploadLibraryRequest): BaseResult<UploadLibraryResponse?>

    @Serializable
    class UpdateLibraryRequest(
        var name: String? = null,
        var content: String? = null,
        var note: String? = null,
        var tags: List<String>? = null,
        var version: String? = null
    )

    /**
     * 编辑更新命令库
     */
    @PUT("library/{id}")
    suspend fun updateLibrary(
        @Path("id") id: Int,
        @Body request: UpdateLibraryRequest
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    @PUT("users/{id}")
    suspend fun updateProfile(
        @Path("id") id: Int,
        @Body request: yancey.chelper.network.library.data.UpdateProfileRequest
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * Get user's own libraries (My Cloud)
     */
    @GET("library")
    suspend fun getMyLibraries(
        @Query("type") type: Int = 1,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): BaseResult<CommandLabPublicService.GetFunctionsResponse?>

    /**
     * Get private library detail
     */
    @GET("library/detail/{id}")
    suspend fun getPrivateFunction(
        @Path("id") id: Int
    ): BaseResult<LibraryFunction?>

    /**
     * 发布私有库到公开市场（需要人机验证）
     * /publish 是管理员接口，普通用户走 /release
     */
    @POST("library/{id}/release")
    suspend fun releaseToPublic(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 同步私有库到公开库
     * 限制: 3次/小时
     */
    @POST("library/{id}/sync")
    suspend fun syncToPublic(
        @Path("id") id: Int
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 删除私有库
     * 管理员可删除任意库，普通用户仅可删除自己的私有库
     */
    @DELETE("library/{id}")
    suspend fun deleteLibrary(
        @Path("id") id: Int
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    // -------------------------------------------------------------
    // Quota
    // -------------------------------------------------------------

    @Serializable
    class QuotaResponse(
        var used: Int? = null,
        var limit: Int? = null
    )

    /**
     * 查询私有库配额：已用数量和上限
     */
    @GET("library/quota")
    suspend fun getQuota(): BaseResult<QuotaResponse?>
}
