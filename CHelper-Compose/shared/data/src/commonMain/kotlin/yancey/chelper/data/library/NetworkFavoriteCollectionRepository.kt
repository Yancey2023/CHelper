package yancey.chelper.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import yancey.chelper.data.local.library.FavoriteLibraryDataStore
import yancey.chelper.domain.library.FavoriteCollectionRepository
import yancey.chelper.domain.library.LibraryContentRepository

internal class NetworkFavoriteCollectionRepository(
    private val content: LibraryContentRepository,
    private val store: FavoriteLibraryDataStore,
    private val requests: AccountRequests,
) : FavoriteCollectionRepository {
    private val mutex = Mutex()

    override suspend fun load() = mutex.withLock {
        requests.run {
            val response = content.favorites()
            check(response.isSuccess()) { response.message ?: "收藏夹加载失败" }
            var remote = response.data?.functions.orEmpty().filterNotNull()
            val legacy = withContext(Dispatchers.IO) { store.pendingServerMigration() }
            val uuids = remote.mapNotNull { it.uuid }.toSet()
            val ids = remote.mapNotNull { it.id }.toSet()
            var changed = false
            var complete = true
            for (bookmark in legacy) {
                if (bookmark.uuid in uuids || bookmark.id in ids) continue
                val id = bookmark.id
                if (id == null) {
                    complete = false
                    break
                }
                val result = content.favorite(id)
                if (!result.isSuccess() || result.data?.isFavorited != true) {
                    complete = false
                    break
                }
                changed = true
            }
            if (complete) withContext(Dispatchers.IO) { store.markServerMigrationComplete() }
            if (changed) {
                val updated = content.favorites()
                check(updated.isSuccess()) { updated.message ?: "收藏夹加载失败" }
                remote = updated.data?.functions.orEmpty().filterNotNull()
            }
            remote
        }
    }

    override suspend fun remove(id: Int) = content.favorite(id)
}
