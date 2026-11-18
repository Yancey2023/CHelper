package yancey.chelper.domain.library.model

import yancey.chelper.domain.library.LibraryFunction

data class LeaderboardEntry(
    val id: Int? = null,
    val avatarUrl: String? = null,
    val nickname: String? = null,
    val tier: Int? = null,
    val userTitle: String? = null,
    val totalLikes: Int? = null,
    val totalFunctions: Int? = null,
)

data class Leaderboard(val leaderboard: List<LeaderboardEntry>? = null)

data class LibraryUpload(val uuid: String? = null)

data class FavoriteState(
    val isFavorited: Boolean? = null,
    val uuid: String? = null,
)

data class LibraryShare(
    val code: String? = null,
    val shareUrl: String? = null,
    val sharePath: String? = null,
    val libraryId: Int? = null,
    val libraryName: String? = null,
    val sharerNickname: String? = null,
    val importToLocal: Boolean? = null,
    val expiresAt: Long? = null,
    val downloadUrl: String? = null,
)

data class LibraryQuota(
    val used: Int? = null,
    val limit: Int? = null,
)

data class LibraryPage(
    val functions: List<LibraryFunction?>? = null,
    val currentPage: Int? = null,
    val perPage: Int? = null,
    val totalCount: Int? = null,
)

data class LibraryReaction(
    val likeCount: Int? = null,
    val isLiked: Boolean? = null,
    val dailyFirstLikePoints: Double? = null,
    val pointsAwarded: Double? = null,
)

data class LibraryChanges(
    val name: String? = null,
    val content: String? = null,
    val note: String? = null,
    val tags: List<String>? = null,
    val version: String? = null,
)

data class AbuseReport(
    val targetType: String? = null,
    val targetId: String? = null,
    val reason: String? = null,
)
