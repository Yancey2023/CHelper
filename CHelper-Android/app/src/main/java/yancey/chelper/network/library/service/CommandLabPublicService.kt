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

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import yancey.chelper.network.library.data.BaseResult
import yancey.chelper.network.library.data.LibraryFunction
import com.google.gson.annotations.SerializedName

@Suppress("unused")
interface CommandLabPublicService {
    class GetFunctionsResponse {
        @SerializedName("list")
        var functions: MutableList<LibraryFunction?>? = null

        @SerializedName("pageNum")
        var currentPage: Int? = null

        @SerializedName("pageSize")
        var perPage: Int? = null

        @SerializedName("total")
        var totalCount: Int? = null
    }

    @GET("library")
    suspend fun getFunctions(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int,
        @Query("keyword") keyword: String?,
        @Query("type") type: Int = 0
    ): BaseResult<GetFunctionsResponse?>

    @GET("library/detail/{id}")
    suspend fun getFunction(
        @Path("id") id: Int
    ): BaseResult<LibraryFunction?>

    class LibraryLikeResponse {
        var likeCount: Int? = null
        var isLiked: Boolean? = null
    }

    @POST("library/{id}/like")
    suspend fun like(
        @Path("id") id: Int
    ): BaseResult<LibraryLikeResponse?>
}
