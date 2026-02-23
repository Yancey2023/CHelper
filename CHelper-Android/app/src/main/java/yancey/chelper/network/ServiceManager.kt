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
import com.google.gson.Gson
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import yancey.chelper.BuildConfig
import yancey.chelper.android.common.util.Settings
import yancey.chelper.android.common.util.MonitorUtil
import yancey.chelper.network.chelper.service.CHelperService
import yancey.chelper.network.library.interceptor.AuthInterceptor
import yancey.chelper.network.library.service.CaptchaService
import yancey.chelper.network.library.service.CommandLabPublicService
import yancey.chelper.network.library.service.CommandLabUserService
import yancey.chelper.network.library.util.WafHelper
import java.io.File
import java.util.concurrent.TimeUnit

object ServiceManager {
    @JvmField
    var GSON: Gson? = null
    var CLIENT: OkHttpClient? = null
    var CHELPER_RETROFIT: Retrofit? = null
    var COMMAND_LAB_RETROFIT: Retrofit? = null
    var CHELPER_SERVICE: CHelperService? = null

    @JvmField
    var COMMAND_LAB_PUBLIC_SERVICE: CommandLabPublicService? = null
    var COMMAND_LAB_USER_SERVICE: CommandLabUserService? = null
    
    @JvmField
    var CAPTCHA_SERVICE: CaptchaService? = null

    @JvmField
    var LAB_BASE_URL = "https://abyssous.site/"

    @JvmStatic
    fun init(context: Context) {
        GSON = Gson()
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http_cache"), 10 * 1024 * 1024))
            .addInterceptor(BrotliInterceptor)
            .addInterceptor(yancey.chelper.network.library.interceptor.RateLimitInterceptor())
            .addInterceptor(AuthInterceptor.INSTANCE)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }
        MonitorUtil.monitHttp(builder)
        CLIENT = builder.build()
        CHELPER_RETROFIT = Retrofit.Builder()
            .baseUrl("https://www.yanceymc.cn/api/chelper/")
            .client(CLIENT!!)
            .addConverterFactory(GsonConverterFactory.create(GSON!!))
            .build()
        COMMAND_LAB_RETROFIT = Retrofit.Builder()
            .baseUrl(Settings.INSTANCE.apiUrl?.takeIf { it.isNotEmpty() } ?: LAB_BASE_URL)
            .client(CLIENT!!)
            .addConverterFactory(GsonConverterFactory.create(GSON!!))
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
