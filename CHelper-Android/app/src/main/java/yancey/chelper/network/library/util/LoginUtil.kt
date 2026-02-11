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

package yancey.chelper.network.library.util

import yancey.chelper.android.common.util.FileUtil
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.User
import yancey.chelper.network.library.service.CommandLabUserService.LoginRequest
import java.io.File
import java.io.IOException
import java.util.function.Consumer

object LoginUtil {
    private var file: File? = null
    var user: User? = null

    fun init(file: File, onError: Consumer<Throwable?>) {
        LoginUtil.file = file
        if (file.exists()) {
            try {
                user =
                    ServiceManager.GSON!!.fromJson(FileUtil.readString(file), User::class.java)
            } catch (throwable: Throwable) {
                onError.accept(throwable)
            }
        }
    }

    @get:Throws(IOException::class)
    val token: String?
        /**
         * 获取jwt令牌
         */
        get() {
            if (user == null) {
                return null
            }
            if (user!!.token != null && user!!.lastLoginTimestamp != null && System.currentTimeMillis() - user!!.lastLoginTimestamp!! < 60000) {
                return user!!.token
            }
            val request = LoginRequest()
            request.account = user!!.phoneNumber
            request.password = user!!.password
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE!!.login(request)!!.execute()
            if (response.body() != null && response.body()!!.status == "success") {
                user!!.lastLoginTimestamp = System.currentTimeMillis()
                FileUtil.writeString(file, ServiceManager.GSON!!.toJson(user))
                return user!!.token
            } else {
                return null
            }
        }
}
