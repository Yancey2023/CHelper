package yancey.chelper.data.remote.library.service

import kotlinx.serialization.json.JsonElement
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.service.CommandLabUserService.GuestAuthRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.LoginRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.LoginResponse
import yancey.chelper.data.remote.library.service.CommandLabUserService.RegisterRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService.SendCodeRequest

/** Endpoints that acquire credentials must never trigger automatic credential acquisition. */
interface CommandLabAuthService {
    suspend fun guestLogin(request: GuestAuthRequest): BaseResult<LoginResponse?>

    suspend fun guestRegister(request: GuestAuthRequest): BaseResult<LoginResponse?>

    suspend fun login(request: LoginRequest): BaseResult<LoginResponse?>

    suspend fun sendCode(request: SendCodeRequest): BaseResult<JsonElement?>

    suspend fun register(request: RegisterRequest): BaseResult<JsonElement?>
}
