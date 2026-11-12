package yancey.chelper.data.library

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.AuthorInfo
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.LibrarySearchRepository
import yancey.chelper.domain.library.PublicLibraryPage

fun List<LibraryFunction>.matchingLibraries(query: String, label: String): List<LibraryFunction> =
    filter { library ->
            listOf(library.name, library.note).any {
                it?.contains(query, ignoreCase = true) == true
            } || library.tags.orEmpty().any { it.contains(query, ignoreCase = true) }
        }
        .map { it.copy(author = AuthorInfo(name = label)) }

class NetworkLibrarySearchRepository(
    private val currentAccount: () -> Int? = { LoginUtil.accountId.value },
    private val tokenForUser: suspend (Int) -> String? = LoginUtil::tokenForUser,
    private val userService: () -> CommandLabUserService = {
        ServiceManager.COMMAND_LAB_USER_SERVICE
    },
) : LibrarySearchRepository {
    override suspend fun publicLibraries(query: String, page: Int): PublicLibraryPage {
        val response = ServiceManager.COMMAND_LAB_PUBLIC_SERVICE.getFunctions(page, 20, query)
        val data = response.data
        if (!response.isSuccess() || data == null) throw IOException(response.message ?: "加载公开库失败")
        val size = (data.perPage ?: 20).coerceAtLeast(1)
        val total = (data.totalCount ?: 0).coerceAtLeast(0).toLong()
        return PublicLibraryPage(
            data.functions.orEmpty().filterNotNull(),
            ((total + size - 1) / size).toInt().coerceAtLeast(1),
        )
    }

    override suspend fun privateLibraries(query: String): List<LibraryFunction> =
        withContext(Dispatchers.Default) {
            val ownerId = currentAccount() ?: return@withContext emptyList()
            val token = tokenForUser(ownerId)
            if (currentAccount() != ownerId) return@withContext emptyList()
            if (token.isNullOrBlank()) throw IOException("请重新登录后搜索私有库")
            val authorization = "Bearer $token"
            val service = userService()
            val matches = mutableListOf<LibraryFunction>()
            var page = 1
            do {
                if (currentAccount() != ownerId) return@withContext emptyList()
                val response =
                    service.getMyLibraries(
                        pageNum = page,
                        pageSize = 100,
                        authorization = authorization,
                    )
                if (currentAccount() != ownerId) return@withContext emptyList()
                val data = response.data
                if (!response.isSuccess() || data == null)
                    throw IOException(response.message ?: "加载私有库失败")
                val entries = data.functions.orEmpty().filterNotNull()
                matches += entries.matchingLibraries(query, "[我的私有库]")
                val size = (data.perPage ?: 100).coerceAtLeast(1)
                val total = data.totalCount
                val hasMore =
                    if (total != null) page.toLong() * size < total else entries.size >= size
                page++
            } while (hasMore)
            if (currentAccount() == ownerId) matches else emptyList()
        }
}
