package yancey.chelper.domain.library

import kotlinx.serialization.Serializable

/** Editable document projection. Cloud credentials and sync bookkeeping never enter UI state. */
@Serializable
data class LibraryEntry(
    val localId: String? = null,
    val remoteId: Int? = null,
    val name: String = "",
    val body: String = "",
    val note: String = "",
    val version: String = "",
    val tags: List<String> = emptyList(),
    val isV2: Boolean = true,
    val author: LibraryAuthor? = null,
    val likeCount: Int? = null,
    val isFavorited: Boolean? = null,
)

@Serializable data class LibraryAuthor(val name: String?, val tier: Int?, val id: Int? = null)

val LibraryEntry.authorName: String
    get() = author?.name ?: "匿名"
