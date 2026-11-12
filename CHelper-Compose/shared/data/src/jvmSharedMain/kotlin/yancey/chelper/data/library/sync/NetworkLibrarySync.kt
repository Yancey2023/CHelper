package yancey.chelper.data.library.sync

import java.io.IOException
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.library.document.toFullLocalMcd
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.sync.SyncedLibraryIdentity

object NetworkLibrarySync {
    suspend fun synchronize(library: LibraryFunction, ownerId: Int): SyncedLibraryIdentity {
        val token = LoginUtil.tokenForUser(ownerId) ?: throw IOException("请登录原账号后继续同步")
        val authorization = "Bearer $token"
        val service = ServiceManager.COMMAND_LAB_USER_SERVICE
        var cloudId = library.id
        // Recover an upload that reached the server before the process died locally.
        if (cloudId == null && !library.uuid.isNullOrBlank()) {
            var page = 1
            do {
                val response =
                    service.getMyLibraries(
                        pageNum = page,
                        pageSize = 100,
                        authorization = authorization,
                    )
                val data = response.data
                if (!response.isSuccess() || data == null)
                    throw IOException(response.message ?: "查询云端同步状态失败")
                val entries = data.functions.orEmpty().filterNotNull()
                cloudId = entries.firstOrNull { it.uuid == library.uuid }?.id
                val more =
                    cloudId == null &&
                        if (data.totalCount != null)
                            page.toLong() * (data.perPage ?: 100).coerceAtLeast(1) <
                                data.totalCount!!
                        else entries.size >= 100
                page++
            } while (more)
        }
        val content = library.toFullLocalMcd()
        if (cloudId != null) {
            val response =
                service.updateLibrary(
                    cloudId,
                    CommandLabUserService.UpdateLibraryRequest(
                        name = library.name,
                        content = content,
                        note = library.note,
                        tags = library.tags.orEmpty(),
                        version = library.version?.ifBlank { "1.0.0" } ?: "1.0.0",
                    ),
                    authorization = authorization,
                )
            if (!response.isSuccess()) throw IOException(response.message ?: "更新云端命令库失败")
            return SyncedLibraryIdentity(cloudId, library.uuid)
        }
        val response =
            service.uploadLibrary(
                CommandLabUserService.UploadLibraryRequest(content, false),
                authorization,
            )
        if (!response.isSuccess() || response.data == null)
            throw IOException(response.message ?: "上传云端命令库失败")
        return SyncedLibraryIdentity(
            null,
            response.data?.uuid?.takeIf(String::isNotBlank) ?: library.uuid,
        )
    }
}
