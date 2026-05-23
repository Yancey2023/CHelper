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

package yancey.chelper.network.library.util

import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.data.UserProfileData

/**
 * 进程级"我的云端库 / 配额 / 公开资料"内存缓存。
 *
 * 之前每次进"我的库"或者从详情页返回，refreshUserState 都会把
 * getMyLibraries / getQuota / getUserProfile 三个接口一起拉一遍。
 * 用户在 tab 之间来回切就成了无意义的流量。
 *
 * 这里按用户 id 做命中校验 + TTL 过期，短时间回访直接吃缓存；
 * 上传/删除等会改变云端状态的动作走 invalidate* 强制下次重拉。
 * 故意不持久化到磁盘——退出进程后登录态/数据更可能漂移，
 * 重新拉一次比看到错位数据更安全。
 */
object CloudLibraryCache {
    /** 默认 60s TTL：覆盖"切 tab 来回"的高频回访，又能让用户主动刷新（清缓存）后看到最新。 */
    const val DEFAULT_TTL_MS: Long = 60_000L

    @Volatile private var librariesUserId: Int? = null
    @Volatile private var librariesData: List<LibraryFunction>? = null
    @Volatile private var librariesAt: Long = 0L

    @Volatile private var profileUserId: Int? = null
    @Volatile private var profileData: UserProfileData? = null
    @Volatile private var profileAt: Long = 0L

    @Volatile private var quotaUserId: Int? = null
    @Volatile private var quotaUsedValue: Int? = null
    @Volatile private var quotaLimitValue: Int? = null
    @Volatile private var quotaAt: Long = 0L

    data class Quota(val used: Int?, val limit: Int?)

    fun getLibraries(userId: Int, ttlMs: Long = DEFAULT_TTL_MS): List<LibraryFunction>? {
        if (librariesUserId != userId) return null
        if (System.currentTimeMillis() - librariesAt > ttlMs) return null
        return librariesData
    }

    fun setLibraries(userId: Int, list: List<LibraryFunction>) {
        librariesUserId = userId
        librariesData = list.toList()
        librariesAt = System.currentTimeMillis()
    }

    fun getProfile(userId: Int, ttlMs: Long = DEFAULT_TTL_MS): UserProfileData? {
        if (profileUserId != userId) return null
        if (System.currentTimeMillis() - profileAt > ttlMs) return null
        return profileData
    }

    fun setProfile(userId: Int, data: UserProfileData?) {
        profileUserId = userId
        profileData = data
        profileAt = System.currentTimeMillis()
    }

    fun getQuota(userId: Int, ttlMs: Long = DEFAULT_TTL_MS): Quota? {
        if (quotaUserId != userId) return null
        if (System.currentTimeMillis() - quotaAt > ttlMs) return null
        return Quota(quotaUsedValue, quotaLimitValue)
    }

    fun setQuota(userId: Int, used: Int?, limit: Int?) {
        quotaUserId = userId
        quotaUsedValue = used
        quotaLimitValue = limit
        quotaAt = System.currentTimeMillis()
    }

    fun invalidateLibraries() {
        librariesUserId = null
        librariesData = null
        librariesAt = 0L
    }

    fun invalidateAll() {
        invalidateLibraries()
        profileUserId = null
        profileData = null
        profileAt = 0L
        quotaUserId = null
        quotaUsedValue = null
        quotaLimitValue = null
        quotaAt = 0L
    }
}
