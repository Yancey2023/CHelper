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
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.LibraryFunction

@Suppress("unused")
interface CommandLabPublicService {
    class GetFunctionsResponse {
        @com.google.gson.annotations.SerializedName("list")
        var functions: MutableList<LibraryFunction?>? = null

        @com.google.gson.annotations.SerializedName("pageNum")
        var currentPage: Int? = null

        @com.google.gson.annotations.SerializedName("pageSize")
        var perPage: Int? = null

        @com.google.gson.annotations.SerializedName("total")
        var totalCount: Int? = null
    }

    @GET("library")
    suspend fun getFunctions(
        @Query("page") page: Int,
        @Suppress("LocalVariableName")
        @Query("per_page") per_page: Int,
        @Query("search") search: String?,
        @Query("author") author: String?,
        @Query("tags") tags: String?,
        @Query("sort") sortType: String?,
        @Suppress("LocalVariableName")
        @Query("android_id") android_id: String?
    ): BaseResult<GetFunctionsResponse?>

    @GET("library/detail/{id}")
    suspend fun getFunction(
        @Path("id") id: Int,
        @Suppress("LocalVariableName")
        @Query("android_id") android_id: String?
    ): BaseResult<LibraryFunction?>

    @GET("function/key/{user_key}")
    suspend fun getFunctionByKey(
        @Suppress("LocalVariableName")
        @Path("user_key") user_key: String?
    ): BaseResult<LibraryFunction?>

    class UploadFunctionRequest {
        var content: String? = null
    }

    class UploadFunctionResponse {
        var id: Int? = null
        var uuid: String? = null
        var functions: MutableList<LibraryFunction?>? = null

        @Suppress("PropertyName")
        var backup_file: String? = null
    }

    @POST("upload")
    suspend fun uploadFunction(
        @Body request: UploadFunctionRequest?
    ): BaseResult<UploadFunctionResponse?>

    class UpdateFunctionRequest {
        var content: String? = null

        @Suppress("PropertyName")
        var auth_key: String? = null
    }

    @PUT("function/{id}")
    fun updateFunction(
        @Path("id") id: Int,
        @Body request: UpdateFunctionRequest?
    ): BaseResult<UploadFunctionResponse?>

    class DeleteFunctionRequest {
        @Suppress("PropertyName")
        var auth_key: String? = null
    }

    @DELETE("function/{id}")
    fun deleteFunction(
        @Path("id") id: Int,
        @Body request: DeleteFunctionRequest?
    ): BaseResult<Void?>

    class LikeFunctionRequest {
        @Suppress("PropertyName")
        var android_id: String? = null
    }

    class LibraryLikeResponse {
        var action: String? = null // like或unlike

        @Suppress("PropertyName")
        var like_count: Int? = null // 当前点赞总数
    }

    @POST("function/{id}/like")
    fun like(
        @Path("id") id: Int,
        @Body request: LikeFunctionRequest?
    ): BaseResult<LibraryLikeResponse?>
}
