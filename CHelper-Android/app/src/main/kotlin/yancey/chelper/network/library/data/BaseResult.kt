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
 * 网络请求的基础返回结果包装类。
 *
 * @param T 数据实体的类型
 * @property status 状态码（0 通常表示成功）
 * @property data 返回的具体数据
 * @property errorType 错误类型标识（当请求失败时存在）
 * @property message 返回的提示信息或错误描述
 */
@Serializable
class BaseResult<T>(
    var status: Int? = null,
    var data: T? = null,
    @SerialName("error_type") var errorType: String? = null,
    var message: String? = null
) {
    /**
     * 判断当前请求是否成功。
     *
     * @return 如果状态码为 0 则返回 true，否则返回 false
     */
    fun isSuccess() = status == 0
}
