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

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.CaptchaStatusResponse
import yancey.chelper.network.library.data.CaptchaTokenRequest
import yancey.chelper.network.library.data.CaptchaTokenResponse

/**
 * 人机验证 API 接口。
 * 
 * 用于在敏感操作（如注册、修改密码等）前验证用户是否为真人。
 */
interface CaptchaService {

    /**
     * 请求验证凭证。
     * 
     * 验证流程：
     * 1. 客户端生成 `special_code`（UUID）。
     * 2. 调用此接口获取 `verification_token`。
     * 3. 客户端使用 `verification_token` 加载验证页面。
     * 4. 验证成功后，使用 `special_code` 进行后续业务请求。
     * 
     * @param request 包含验证会话唯一标识符和操作类型的请求体数据
     * @return 包含 `verification_token` 等信息的验证响应，如果请求失败可能为空
     */
    @POST("captcha")
    suspend fun requestToken(@Body request: CaptchaTokenRequest): BaseResult<CaptchaTokenResponse?>

    /**
     * 查询验证状态。
     * 
     * 用于轮询检查用户是否在网页端完成了人机验证。
     * 
     * @param specialCode 验证会话的唯一标识符
     * @return 当前验证状态响应（包含 pending, challenging, verified, failed, used 等状态），如果请求失败可能为空
     */
    @GET("captcha/status")
    suspend fun getStatus(
        @Query("special_code") specialCode: String
    ): BaseResult<CaptchaStatusResponse?>
}
