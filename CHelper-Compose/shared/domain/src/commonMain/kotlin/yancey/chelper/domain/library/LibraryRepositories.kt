package yancey.chelper.domain.library

data class PublicLibraryPage(val libraries: List<LibraryFunction>, val totalPages: Int)

data class LibraryFavoriteResult(val favorited: Boolean, val message: String?)

interface PublicLibraryRepository {
    suspend fun load(page: Int, recommended: Boolean): PublicLibraryPage

    fun canFavorite(): Boolean

    suspend fun toggleFavorite(id: Int): LibraryFavoriteResult

    suspend fun unreadCount(): Int
}

interface LibrarySearchRepository {
    suspend fun publicLibraries(query: String, page: Int): PublicLibraryPage

    suspend fun privateLibraries(query: String): List<LibraryFunction>
}
