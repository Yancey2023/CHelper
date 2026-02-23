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

package yancey.chelper.network.library.data

/**
 * 人机验证相关的数据模型
 */

/**
 * 请求验证凭证的请求体
 */
@Suppress("unused")
class CaptchaTokenRequest {
    @Suppress("PropertyName")
    var special_code: String? = null
    var action: String? = null
    
    companion object {
        // 预定义的 action 值
        const val ACTION_REGISTER = "注册账号"
        const val ACTION_UPDATE_PASSWORD = "更新密码"
        const val ACTION_DELETE_ACCOUNT = "弃用账号"
    }
}

/**
 * 请求验证凭证的响应
 */
@Suppress("unused")
class CaptchaTokenResponse {
    @Suppress("PropertyName")
    var verification_token: String? = null
    var action: String? = null
    @Suppress("PropertyName")
    var special_code: String? = null
}

/**
 * 验证状态响应
 */
@Suppress("unused")
class CaptchaStatusResponse {
    @Suppress("PropertyName")
    var special_code: String? = null
    var status: String? = null
    var action: String? = null
    
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_CHALLENGING = "challenging"
        const val STATUS_VERIFIED = "verified"
        const val STATUS_FAILED = "failed"
        const val STATUS_USED = "used"
    }
}
