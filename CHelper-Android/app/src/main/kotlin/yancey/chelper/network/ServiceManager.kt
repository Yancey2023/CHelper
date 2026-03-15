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

package yancey.chelper.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import yancey.chelper.BuildConfig
import yancey.chelper.network.chelper.service.CHelperService
import yancey.chelper.network.library.interceptor.AuthInterceptor
import yancey.chelper.network.library.interceptor.RateLimitInterceptor
import yancey.chelper.network.library.service.CaptchaService
import yancey.chelper.network.library.service.CommandLabPublicService
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.util.WafHelper
import java.io.File
import java.util.concurrent.TimeUnit

object ServiceManager {
    var CLIENT: OkHttpClient? = null
    var CHELPER_RETROFIT: Retrofit? = null
    var COMMAND_LAB_RETROFIT: Retrofit? = null
    var CHELPER_SERVICE: CHelperService? = null

    var COMMAND_LAB_PUBLIC_SERVICE: CommandLabPublicService? = null
    var COMMAND_LAB_USER_SERVICE: CommandLabUserService? = null

    var CAPTCHA_SERVICE: CaptchaService? = null

    var LAB_BASE_URL = "https://abyssous.site/"

    fun init(context: Context) {
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http_cache"), 10 * 1024 * 1024))
            .addInterceptor(BrotliInterceptor)
            .addInterceptor(RateLimitInterceptor(2))
            .addInterceptor(AuthInterceptor.INSTANCE)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }
        CLIENT = builder.build()
        CHELPER_RETROFIT = Retrofit.Builder()
            .baseUrl("https://www.yanceymc.cn/api/chelper/")
            .client(CLIENT!!)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()
        COMMAND_LAB_RETROFIT = Retrofit.Builder()
            .baseUrl(LAB_BASE_URL)
            .client(CLIENT!!)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()
        CHELPER_SERVICE = CHELPER_RETROFIT!!.create(CHelperService::class.java)
        COMMAND_LAB_PUBLIC_SERVICE =
            COMMAND_LAB_RETROFIT!!.create(CommandLabPublicService::class.java)
        COMMAND_LAB_USER_SERVICE = COMMAND_LAB_RETROFIT!!.create(CommandLabUserService::class.java)
        CAPTCHA_SERVICE = COMMAND_LAB_RETROFIT!!.create(CaptchaService::class.java)

        // 初始化 WAF Helper
        WafHelper.init(context)
    }
}
