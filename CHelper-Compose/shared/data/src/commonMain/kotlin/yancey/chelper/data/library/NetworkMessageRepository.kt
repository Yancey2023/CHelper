package yancey.chelper.data.library

import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

internal class NetworkMessageRepository(
    private val user: CommandLabUserService,
    private val requests: AccountRequests,
) : MessageRepository {
    override suspend fun load(page: Int, pageSize: Int, unreadOnly: Boolean) = requests.run {
        user.getMessages(page, pageSize, unreadOnly).outcome { it?.toDomain() ?: error("站内信为空") }
    }

    override suspend fun unreadCount() = requests.run {
        user.getUnreadCount().outcome { it?.toDomain() ?: UnreadCount() }
    }

    override suspend fun markRead(id: Int) = requests.run { user.markMessageRead(id).completion() }

    override suspend fun delete(id: Int) = requests.run { user.deleteMessage(id).completion() }
}
