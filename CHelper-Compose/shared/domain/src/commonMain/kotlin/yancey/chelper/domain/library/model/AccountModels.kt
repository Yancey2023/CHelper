package yancey.chelper.domain.library.model

import yancey.chelper.domain.library.LibraryFunction

data class UserProfile(
    val id: Int? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val tier: Int? = null,
    val signature: String? = null,
    val homepage: String? = null,
    val totalPublicFunctions: Int? = null,
    val totalLikes: Int? = null,
    val recentFunctions: List<LibraryFunction>? = null,
    val userTitle: String? = null,
    val email: String? = null,
    val createdAt: String? = null,
    val tierExpiresAt: String? = null,
)

data class ProfileChanges(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val homepage: String? = null,
    val signature: String? = null,
)

data class AccountUser(
    val id: Int? = null,
    val email: String? = null,
    val nickname: String? = null,
    val isGuest: Boolean? = null,
    val isAdmin: Boolean? = null,
    val isModerator: Boolean? = null,
    val isVerified: Boolean? = null,
    val createdAt: String? = null,
    val gravatarUrl: String? = null,
    val tier: Int? = null,
)

data class AvatarUpload(val avatarUrl: String? = null)
