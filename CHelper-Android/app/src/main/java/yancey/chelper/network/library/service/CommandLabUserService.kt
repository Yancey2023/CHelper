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

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
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

    class GuestAuthRequest {
        var fingerprint: String? = null
        @Suppress("PropertyName")
        var auth_code: String? = null
    }

    @POST("guest/login")
    fun guestLogin(@Body request: GuestAuthRequest): Call<BaseResult<LoginResponse?>>

    @POST("guest/register")
    fun guestRegister(@Body request: GuestAuthRequest): Call<BaseResult<LoginResponse?>>

    @POST("guest/migrate")
    fun guestMigrate(@Body request: GuestAuthRequest): Call<BaseResult<Void?>>

    // -------------------------------------------------------------
    // Official User System
    // -------------------------------------------------------------

    // 注册相关
    
    /**
     * 发送邮箱验证码请求体
     */
    class SendCodeRequest {
        @Suppress("PropertyName")
        var special_code: String? = null
        var type: Int? = null  // 0=注册, 1=更新密码, 2=找回密码
        var email: String? = null
        var phone: String? = null
        var lang: String? = "zh-CN"
        
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
    fun sendCode(@Body request: SendCodeRequest): Call<BaseResult<Void?>>
    
    /**
     * 注册请求体
     */
    /**
     * 注册请求体
     */
    class RegisterRequest {
        @Suppress("PropertyName")
        var special_code: String? = null
        var code: String? = null
        var email: String? = null
        var phone: String? = null
        var nickname: String? = null
        var password: String? = null
        @Suppress("PropertyName")
        var android_id: String? = null
    }
    
    /**
     * 提交注册
     */
    @POST("register")
    fun register(@Body request: RegisterRequest): Call<BaseResult<Void?>>
    
    // 登录相关
    
    /**
     * 登录请求体
     */
    class LoginRequest {
        @JvmField
        var account: String? = null
        @JvmField
        var password: String? = null
    }
    
    /**
     * 用户信息
     */
    class User {
        var id: Int? = null
        var email: String? = null
        var nickname: String? = null

        @Suppress("PropertyName")
        var is_guest: Boolean? = null
        @Suppress("PropertyName")
        var is_admin: Boolean? = null
        @Suppress("PropertyName")
        var is_moderator: Boolean? = null
        @Suppress("PropertyName")
        var gravatar_url: String? = null
    }
    
    /**
     * 登录响应
     */
    class LoginResponse {
        @Suppress("PropertyName")
        var user_id: Int? = null // Added from upstream
        var token: String? = null
        var user: User? = null
    }
    
    /**
     * 正式用户登录
     */
    @POST("register/login")
    fun login(@Body request: LoginRequest): Call<BaseResult<LoginResponse?>>

    

    // -------------------------------------------------------------
    // Library Management
    // -------------------------------------------------------------

    class UploadLibraryRequest {
        @Suppress("PropertyName")
        var special_code: String? = null
        var content: String? = null
        @Suppress("PropertyName")
        var is_publish: Boolean? = true
    }

    class UploadLibraryResponse {
        var uuid: String? = null
    }

    @POST("library/upload")
    fun uploadLibrary(@Body request: UploadLibraryRequest): Call<BaseResult<UploadLibraryResponse?>>

    /**
     * Get user's own libraries (My Cloud)
     */
    @GET("library")
    fun getMyLibraries(
        @Query("type") type: Int = 1,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): Call<BaseResult<CommandLabPublicService.GetFunctionsResponse?>>

    /**
     * Get private library detail
     */
    @GET("library/detail/{id}")
    suspend fun getPrivateFunction(
        @Path("id") id: Int
    ): BaseResult<LibraryFunction?>

    /**
     * 切换发布状态（公开/私有）
     */
    @POST("library/{id}/publish")
    suspend fun togglePublish(
        @Path("id") id: Int
    ): BaseResult<Void?>

    /**
     * 同步私有库到公开库
     * 限制: 3次/小时
     */
    @POST("library/{id}/sync")
    suspend fun syncToPublic(
        @Path("id") id: Int
    ): BaseResult<Void?>

    /**
     * 编辑私有库内容
     */
    @PUT("library/{id}")
    suspend fun editLibrary(
        @Path("id") id: Int,
        @Body request: UploadLibraryRequest
    ): BaseResult<Void?>
}
