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
 * CommandLab 用户系统 API 接口。
 *
 * 包含注册、登录、用户资料管理以及用户个人命令库管理等相关功能。
 */
@Suppress("unused")
interface CommandLabUserService {

    // -------------------------------------------------------------
    // Guest System
    // -------------------------------------------------------------

    /**
     * 访客系统认证请求的数据模型。
     *
     * @property fingerprint 设备指纹标识
     * @property authCode 用于验证的授权码
     */
    @Serializable
    class GuestAuthRequest(
        var fingerprint: String? = null,
        @SerialName("auth_code") var authCode: String? = null
    )

    /**
     * 访客登录。
     *
     * @param request 包含设备信息的认证请求
     * @return 返回包含访客 Token 及简要用户信息的登录响应
     */
    @POST("guest/login")
    suspend fun guestLogin(@Body request: GuestAuthRequest): BaseResult<LoginResponse?>

    /**
     * 访客注册。
     *
     * @param request 包含设备信息的认证请求
     * @return 返回包含新访客 Token 及简要用户信息的登录响应
     */
    @POST("guest/register")
    suspend fun guestRegister(@Body request: GuestAuthRequest): BaseResult<LoginResponse?>

    /**
     * 访客数据迁移至正式账号。
     *
     * @param request 包含认证信息的请求数据
     * @return 无返回数据的成功响应
     */
    @POST("guest/migrate")
    suspend fun guestMigrate(@Body request: GuestAuthRequest): BaseResult<Void?>

    // -------------------------------------------------------------
    // Official User System
    // -------------------------------------------------------------

    // 注册相关

    /**
     * 发送验证码的请求体数据模型。
     *
     * @property specialCode 进行人机验证后获取的唯一标识码
     * @property type 验证码类型：0=注册, 1=更新密码, 2=找回密码
     * @property email 接收验证码的邮箱地址
     * @property phone 接收验证码的手机号码
     * @property lang 语言偏好设置，默认为 "zh-CN"
     */
    @Serializable
    class SendCodeRequest(
        @SerialName("special_code") var specialCode: String? = null,
        var type: Int? = null,
        var email: String? = null,
        var phone: String? = null,
        var lang: String? = "zh-CN"
    ) {
        companion object {
            /** 注册类型验证码 */
            const val TYPE_REGISTER = 0
            /** 更新密码类型验证码 */
            const val TYPE_UPDATE_PASSWORD = 1
            /** 重置密码类型验证码 */
            const val TYPE_RESET_PASSWORD = 2
        }
    }

    /**
     * 发送验证码。
     * 
     * 调用此接口前需要先完成人机验证以获取 special_code。
     *
     * @param request 包含目标邮箱/手机以及验证会话信息的请求体
     * @return 无返回数据的成功响应
     */
    @POST("register/sendCode")
    suspend fun sendCode(@Body request: SendCodeRequest): BaseResult<Void?>

    /**
     * 注册账号的请求体数据模型。
     *
     * @property specialCode 完成人机验证后获取的特殊标识码
     * @property code 收到的验证码
     * @property email 注册邮箱
     * @property phone 注册手机号
     * @property nickname 设定的用户昵称
     * @property password 设定的账号密码
     * @property androidId 设备的 Android ID
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
     * 提交注册请求。
     *
     * @param request 包含验证码和用户注册信息的请求体
     * @return 包含系统分配信息等注册结果的 JSON 响应
     */
    @POST("register")
    suspend fun register(@Body request: RegisterRequest): BaseResult<kotlinx.serialization.json.JsonElement?>

    // 登录相关

    /**
     * 正式账号登录的请求体数据模型。
     *
     * @property account 登录账号（邮箱或手机号等）
     * @property password 登录密码
     */
    @Serializable
    class LoginRequest(
        var account: String? = null,
        var password: String? = null
    )

    /**
     * 用户详细信息的数据模型。
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
     * 登录成功的响应数据模型。
     *
     * @property userId 登录用户的 ID
     * @property token 用户的鉴权 Token
     * @property user 包含用户详细信息的对象
     */
    @Serializable
    class LoginResponse(
        @SerialName("user_id") var userId: Int? = null,
        var token: String? = null,
        var user: User? = null
    )

    /**
     * 执行正式用户登录。
     *
     * @param request 包含账号密码信息的请求体
     * @return 包含 Token 和用户信息的登录成功响应
     */
    @POST("register/login")
    suspend fun login(@Body request: LoginRequest): BaseResult<LoginResponse?>


    // -------------------------------------------------------------
    // Avatar
    // -------------------------------------------------------------

    /**
     * 上传头像的响应数据模型。
     *
     * @property avatarUrl 上传成功后获取的图片 URL
     */
    @Serializable
    class UploadAvatarResponse(
        @SerialName("avatar_url") var avatarUrl: String? = null
    )

    /**
     * 上传用户头像文件。
     *
     * @param file 包含图片数据的 Multipart 请求段
     * @return 包含上传后图片地址的响应结果
     */
    @Multipart
    @POST("avatar")
    suspend fun uploadAvatar(
        @Part file: okhttp3.MultipartBody.Part
    ): BaseResult<UploadAvatarResponse?>

    // -------------------------------------------------------------
    // Library Management
    // -------------------------------------------------------------

    /**
     * 上传新建命令库的请求体数据模型。
     *
     * @property content 命令库的具体内容/代码
     * @property isPublish 标识是否直接发布（目前上传固定为私有草稿，发布走单独接口）
     */
    @Serializable
    class UploadLibraryRequest(
        var content: String? = null,
        @SerialName("is_publish") var isPublish: Boolean = false
    )

    /**
     * 上传新建命令库的响应数据模型。
     *
     * @property uuid 新建命令库分配的唯一 UUID
     */
    @Serializable
    class UploadLibraryResponse(
        var uuid: String? = null
    )

    /**
     * 上传新建命令库（保存至个人云端草稿）。
     *
     * @param request 包含代码内容的请求体
     * @return 返回包含新分配 UUID 的响应结果
     */
    @POST("library/upload")
    suspend fun uploadLibrary(@Body request: UploadLibraryRequest): BaseResult<UploadLibraryResponse?>

    /**
     * 编辑更新命令库的请求体数据模型。
     *
     * @property name 命令库名称
     * @property content 命令库代码内容
     * @property note 相关说明或备注信息
     * @property tags 关联的标签列表
     * @property version 适用的版本信息
     */
    @Serializable
    class UpdateLibraryRequest(
        var name: String? = null,
        var content: String? = null,
        var note: String? = null,
        var tags: List<String>? = null,
        var version: String? = null
    )

    /**
     * 编辑更新现有的命令库。
     *
     * @param id 命令库的自增 ID
     * @param request 包含要更新属性的请求体
     * @return 指示操作结果的响应
     */
    @PUT("library/{id}")
    suspend fun updateLibrary(
        @Path("id") id: Int,
        @Body request: UpdateLibraryRequest
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 更新用户个人公开资料。
     *
     * @param id 用户自身的 ID
     * @param request 包含昵称、主页等资料更新项的请求体
     * @return 指示操作结果的响应
     */
    @PUT("users/{id}")
    suspend fun updateProfile(
        @Path("id") id: Int,
        @Body request: yancey.chelper.network.library.data.UpdateProfileRequest
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 获取用户自己的私有命令库列表（我的云端）。
     *
     * @param type 类型，默认值为 1 表示私有库
     * @param pageNum 查询的页码
     * @param pageSize 每页条数上限
     * @return 包含命令库分页数据的响应结果
     */
    @GET("library")
    suspend fun getMyLibraries(
        @Query("type") type: Int = 1,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): BaseResult<CommandLabPublicService.GetFunctionsResponse?>

    /**
     * 获取用户自己私有命令库的详细内容。
     *
     * @param id 私有命令库的 ID
     * @return 包含库详情数据的响应结果
     */
    @GET("library/detail/{id}")
    suspend fun getPrivateFunction(
        @Path("id") id: Int
    ): BaseResult<LibraryFunction?>

    /**
     * 将私有库发布至公开市场。
     *
     * 需要先完成人机验证以获得授权凭证。
     *
     * @param id 目标命令库的 ID
     * @param body 包含验证凭证（如 special_code）的请求体
     * @return 指示发布结果的响应
     */
    @POST("library/{id}/release")
    suspend fun releaseToPublic(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 同步用户的私有库内容至已发布的公开库。
     *
     * 注意：该接口可能有调用频率限制（如 3次/小时）。
     *
     * @param id 目标命令库的 ID
     * @return 指示同步结果的响应
     */
    @POST("library/{id}/sync")
    suspend fun syncToPublic(
        @Path("id") id: Int
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    /**
     * 删除私有库。
     *
     * 普通用户仅可删除自己的私有库，管理员可删除任意库。
     *
     * @param id 目标命令库的 ID
     * @return 指示删除结果的响应
     */
    @DELETE("library/{id}")
    suspend fun deleteLibrary(
        @Path("id") id: Int
    ): BaseResult<kotlinx.serialization.json.JsonElement?>

    // -------------------------------------------------------------
    // Quota
    // -------------------------------------------------------------

    /**
     * 用户存储配额的响应数据模型。
     *
     * @property used 已使用的私有库数量
     * @property limit 用户私有库的存储数量上限
     */
    @Serializable
    class QuotaResponse(
        var used: Int? = null,
        var limit: Int? = null
    )

    /**
     * 查询当前用户的私有库配额信息（已用数量和上限）。
     *
     * @return 包含存储配额数据的响应结果
     */
    @GET("library/quota")
    suspend fun getQuota(): BaseResult<QuotaResponse?>
}
