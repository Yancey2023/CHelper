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
import yancey.chelper.network.library.data.BaseResult

@Suppress("unused")
interface CommandLabUserService {
    class SendVerifyCodeRequest {
        @Suppress("PropertyName")
        var special_code: String? = null
        var type: Int? = null
        var email: String? = null
        var phone: String? = null
        var lang: String? = null
    }

    @POST("register/sendCode")
    fun sendVerifyCode(
        @Body request: SendVerifyCodeRequest?
    ): Call<BaseResult<Void?>?>?

    class RegisterRequest {
        var email: String? = null
    }

    class RegisterResponse {
        var message: String? = null
    }

    @POST("user/register")
    fun register(
        @Body request: RegisterRequest?
    ): Call<BaseResult<RegisterResponse?>?>?

    class VerifyRegistrationRequest {
        var email: String? = null
        var code: String? = null
        var password: String? = null

        @Suppress("PropertyName")
        var android_id: String? = null
    }

    class VerifyRegistrationResponse {
        @Suppress("PropertyName")
        var user_id: Int? = null
        var token: String? = null
    }

    @POST("user/verify")
    fun verifyRegistration(
        @Body request: VerifyRegistrationRequest?
    ): Call<BaseResult<VerifyRegistrationResponse?>?>?

    class LoginRequest {
        @JvmField
        var account: String? = null

        @JvmField
        var password: String? = null
    }

    class User {
        var id: Int? = null
        var email: String? = null
        var nickname: String? = null

        @Suppress("PropertyName")
        var is_admin: Boolean? = null

        @Suppress("PropertyName")
        var is_moderator: Boolean? = null
    }

    class LoginResponse {
        @Suppress("PropertyName")
        var user_id: Int? = null
        var token: String? = null
        var user: User? = null
    }

    @POST("user/login")
    fun login(
        @Body request: LoginRequest?
    ): Call<BaseResult<LoginResponse?>?>?

    @get:GET("web/user_info")
    val userInfo: Call<BaseResult<User?>?>?

    class CheckLoginResponse {
        @Suppress("PropertyName")
        var logged_in: Boolean? = null
        var user: User? = null
    }

    @GET("web/check_login")
    fun checkLogin(): Call<BaseResult<CheckLoginResponse?>?>?

    class SendResetCodeRequest {
        var email: String? = null
    }

    class SendResetCodeResponse {
        var message: String? = null
    }

    @POST("user/send_reset_code")
    fun sendResetCode(
        @Body request: SendResetCodeRequest?
    ): Call<BaseResult<SendResetCodeResponse?>?>?

    class ResetPasswordRequest {
        var email: String? = null
        var code: String? = null

        @Suppress("PropertyName")
        var new_password: String? = null
    }

    class ResetPasswordResponse {
        var message: String? = null
    }

    @POST("user/reset_password")
    fun resetPassword(
        @Body request: ResetPasswordRequest?
    ): Call<BaseResult<ResetPasswordResponse?>?>?

    class UpdateSettingRequest {
        var nickname: String? = null

        @Suppress("PropertyName")
        var old_password: String? = null

        @Suppress("PropertyName")
        var new_password: String? = null
    }

    class UpdateSettingResponse {
        var message: String? = null
    }

    @POST("web/update_settings")
    fun updateSetting(
        @Body request: UpdateSettingRequest?
    ): Call<BaseResult<UpdateSettingResponse?>?>?

    class LogoutResponse {
        var message: String? = null
    }

    @POST("user/logout")
    fun logout(): Call<BaseResult<LogoutResponse?>?>?

    class VerifySensitiveRequest {
        var operation: String? = null
        var code: String? = null
    }

    class VerifySensitiveResponse {
        var verified: Boolean? = null
    }

    @POST("user/verify_sensitive")
    fun verifySensitive(
        @Body request: VerifySensitiveRequest?
    ): Call<BaseResult<VerifySensitiveResponse?>?>?
}
