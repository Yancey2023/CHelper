package yancey.chelper.data.library

import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import yancey.chelper.data.remote.library.service.CommandLabPublicService
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

internal class NetworkLibraryContentRepository(
    private val public: CommandLabPublicService,
    private val user: CommandLabUserService,
    private val requests: AccountRequests,
    private val cache: RepositoryCache,
) : LibraryContentRepository {
    override fun invalidate() = cache.invalidate()

    override suspend fun detail(id: Int, private: Boolean) =
        if (private)
            requests.run {
                user.getPrivateFunction(id).outcome { it ?: error("命令库为空") }
            }
        else public.getFunction(id).outcome { it ?: error("命令库为空") }

    override suspend fun mine(pageNum: Int, pageSize: Int, forceRefresh: Boolean) = requests.run {
        if (pageNum == 1)
            cache.read("mine:$pageSize", forceRefresh) {
                user.getMyLibraries(pageNum = pageNum, pageSize = pageSize).outcome {
                    it?.toDomain() ?: error("命令库列表为空")
                }
            }
        else
            user.getMyLibraries(pageNum = pageNum, pageSize = pageSize).outcome {
                it?.toDomain() ?: error("命令库列表为空")
            }
    }

    override suspend fun byAuthor(authorId: Int, pageNum: Int, pageSize: Int) =
        public.getFunctions(pageNum, pageSize, null, authorId = authorId).outcome {
            it?.toDomain() ?: error("命令库列表为空")
        }

    override suspend fun favorites() = requests.run {
        user.getFavoriteLibraries().outcome { it?.toDomain() ?: error("收藏夹为空") }
    }

    override suspend fun quota(forceRefresh: Boolean) = requests.run {
        cache.read("quota", forceRefresh) {
            user.getQuota().outcome { it?.toDomain() ?: error("配额为空") }
        }
    }

    override suspend fun upload(content: String) = requests.run {
        user
            .uploadLibrary(CommandLabUserService.UploadLibraryRequest(content, false))
            .outcome { it?.toDomain() ?: error("上传结果为空") }
            .also { if (it.isSuccess()) cache.invalidate() }
    }

    override suspend fun update(id: Int, changes: LibraryChanges) = requests.run {
        user
            .updateLibrary(
                id,
                CommandLabUserService.UpdateLibraryRequest(
                    changes.name,
                    changes.content,
                    changes.note,
                    changes.tags?.toList(),
                    changes.version,
                ),
            )
            .completion()
            .also { if (it.isSuccess()) cache.invalidate() }
    }

    override suspend fun delete(id: Int) = requests.run {
        user.deleteLibrary(id).completion().also { if (it.isSuccess()) cache.invalidate() }
    }

    override suspend fun publish(id: Int, verification: String) = requests.run {
        user
            .releaseToPublic(id, mapOf("special_code" to verification))
            .outcome {
                Publication(
                    runCatching { it?.jsonObject?.get("public_id")?.jsonPrimitive?.intOrNull }
                        .getOrNull()
                )
            }
            .also { if (it.isSuccess()) cache.invalidate() }
    }

    override suspend fun syncPublic(id: Int) = requests.run {
        user.syncToPublic(id).completion().also { if (it.isSuccess()) cache.invalidate() }
    }

    override suspend fun like(id: Int) =
        public.like(id).outcome { it?.toDomain() ?: error("点赞结果为空") }

    override suspend fun favorite(id: Int) = requests.run {
        user.toggleFavorite(id).outcome { it?.toDomain() ?: error("收藏结果为空") }
    }

    override suspend fun share(id: Int, importToLocal: Boolean) = requests.run {
        user.createLibraryShare(id, importToLocal).outcome { it?.toDomain() ?: error("分享结果为空") }
    }

    override suspend fun report(report: AbuseReport) = requests.run {
        user
            .submitReport(
                CommandLabUserService.ReportRequest(
                    report.targetType,
                    report.targetId,
                    report.reason,
                )
            )
            .completion()
    }

    override suspend fun leaderboard() =
        public.getLeaderboard().outcome { it?.toDomain() ?: error("排行榜为空") }
}
