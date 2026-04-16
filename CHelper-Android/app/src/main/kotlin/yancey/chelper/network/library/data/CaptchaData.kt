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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 请求验证凭证的请求体。
 *
 * @property specialCode 验证请求的唯一标识码
 * @property action 执行的操作类型
 */
@Suppress("unused")
@Serializable
class CaptchaTokenRequest(
    @SerialName("special_code") var specialCode: String? = null,
    var action: String? = null
) {
    /**
     * 伴生对象，包含预定义的 action 常量值。
     */
    companion object {
        /** 注册账号操作 */
        const val ACTION_REGISTER = "注册账号"

        /** 更新密码操作 */
        const val ACTION_UPDATE_PASSWORD = "更新密码"

        /** 弃用账号操作 */
        const val ACTION_DELETE_ACCOUNT = "弃用账号"
    }
}

/**
 * 请求验证凭证的响应。
 *
 * @property verificationToken 验证凭证的 token
 * @property action 执行的操作类型
 * @property specialCode 验证请求的唯一标识码
 */
@Serializable
class CaptchaTokenResponse(
    @SerialName("verification_token") var verificationToken: String? = null,
    var action: String? = null,
    @SerialName("special_code") var specialCode: String? = null
)

/**
 * 验证状态响应。
 *
 * @property specialCode 验证请求的唯一标识码
 * @property status 当前的验证状态
 * @property action 执行的操作类型
 */
@Suppress("unused")
@Serializable
class CaptchaStatusResponse(
    @SerialName("special_code") var specialCode: String? = null,
    var status: String? = null,
    var action: String? = null
) {
    /**
     * 伴生对象，包含预定义的验证状态常量值。
     */
    companion object {
        /** 等待验证 */
        const val STATUS_PENDING = "pending"

        /** 正在进行挑战 */
        const val STATUS_CHALLENGING = "challenging"

        /** 验证通过 */
        const val STATUS_VERIFIED = "verified"

        /** 验证失败 */
        const val STATUS_FAILED = "failed"

        /** 已使用 */
        const val STATUS_USED = "used"
    }
}