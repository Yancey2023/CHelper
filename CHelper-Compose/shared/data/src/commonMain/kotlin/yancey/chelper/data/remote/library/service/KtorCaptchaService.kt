package yancey.chelper.data.remote.library.service

import io.ktor.client.request.*
import io.ktor.http.*
import yancey.chelper.data.remote.RemoteApi
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.model.CaptchaStatusResponse
import yancey.chelper.data.remote.library.model.CaptchaTokenRequest
import yancey.chelper.data.remote.library.model.CaptchaTokenResponse

internal class KtorCaptchaService(private val api: RemoteApi) : CaptchaService {
    override suspend fun requestToken(
        request: CaptchaTokenRequest
    ): BaseResult<CaptchaTokenResponse?> =
        api.request("captcha", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    override suspend fun getStatus(specialCode: String): BaseResult<CaptchaStatusResponse?> =
        api.request("captcha/status", HttpMethod.Get) { parameter("special_code", specialCode) }
}
