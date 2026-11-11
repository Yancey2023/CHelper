package yancey.chelper.data

import java.io.IOException
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import yancey.chelper.network.library.util.LoginUtil

internal data class PublicLibraryPage(
    val libraries: List<LibraryFunction>,
    val totalPages: Int,
)

internal data class LibraryFavoriteResult(val favorited: Boolean, val message: String?)

internal interface PublicLibraryRepository {
    suspend fun load(page: Int, recommended: Boolean): PublicLibraryPage

    fun canFavorite(): Boolean

    suspend fun toggleFavorite(id: Int): LibraryFavoriteResult

    suspend fun unreadCount(): Int
}

/** Retrofit suspend calls are main-safe; UI code only depends on this data-layer contract. */
internal class NetworkPublicLibraryRepository : PublicLibraryRepository {
    override suspend fun load(page: Int, recommended: Boolean): PublicLibraryPage {
        val service = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE
        val response =
            if (recommended) service.getRecommendedLibrary(limit = 15)
            else service.getFunctions(pageNum = page, pageSize = 20, keyword = null)
        val data = response.data
        if (!response.isSuccess() || data == null) throw IOException(response.message ?: "加载失败")
        val total = (data.totalCount ?: 0).coerceAtLeast(0).toLong()
        val size = (data.perPage ?: 20).coerceAtLeast(1)
        return PublicLibraryPage(
            data.functions?.filterNotNull()?.toList() ?: emptyList(),
            ((total + size - 1) / size).toInt().coerceAtLeast(1),
        )
    }

    override fun canFavorite() = LoginUtil.isLoggedIn && LoginUtil.currentUser?.isGuest != true

    override suspend fun toggleFavorite(id: Int): LibraryFavoriteResult {
        val response = ServiceManager.COMMAND_LAB_USER_SERVICE.toggleFavorite(id)
        val data = response.data
        if (!response.isSuccess() || data == null) throw IOException(response.message ?: "收藏操作失败")
        return LibraryFavoriteResult(data.isFavorited == true, response.message)
    }

    override suspend fun unreadCount(): Int {
        val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getUnreadCount()
        if (!response.isSuccess()) throw IOException(response.message ?: "获取站内信失败")
        return response.data?.count ?: 0
    }
}
