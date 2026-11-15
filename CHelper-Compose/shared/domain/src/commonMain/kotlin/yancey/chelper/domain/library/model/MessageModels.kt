package yancey.chelper.domain.library.model

data class InboxMessage(
    val id: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val msgType: String? = null,
    val senderId: Int? = null,
    val createdAt: String? = null,
    val isRead: Boolean? = null,
    val readAt: String? = null,
    val isGlobal: Boolean? = null,
)

data class MessagePage(
    val messages: List<InboxMessage>? = null,
    val total: Int? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
)

data class UnreadCount(val count: Int? = null)
