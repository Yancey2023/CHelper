package yancey.chelper.domain.library

import kotlinx.coroutines.flow.StateFlow
import yancey.chelper.domain.library.model.*

/** A business outcome, independent of HTTP status codes and wire envelopes. */
data class RepositoryResult<out T>(
    val data: T?,
    val message: String? = null,
    val succeeded: Boolean = true,
) {
    fun isSuccess() = succeeded
}

data class AccountState(
    val user: AccountUser? = null,
    val guest: AccountUser? = null,
    val revision: Long = 0,
)

data class Registration(
    val account: String,
    val password: String,
    val nickname: String,
    val code: String,
    val verification: String,
)

data class Publication(val publicId: Int? = null)

interface AccountRepository {
    val state: StateFlow<AccountState>
    val accountId: StateFlow<Int?>
    val currentUser: AccountUser?
        get() = state.value.user

    val guestUser: AccountUser?
        get() = state.value.guest

    val isLoggedIn: Boolean
        get() = currentUser != null

    suspend fun login(account: String, password: String): Result<AccountUser>

    fun logout()

    suspend fun sendVerification(account: String, verification: String): RepositoryResult<Unit>

    suspend fun register(details: Registration): RepositoryResult<Unit>

    suspend fun profile(id: Int, forceRefresh: Boolean = false): RepositoryResult<UserProfile>

    suspend fun updateProfile(id: Int, changes: ProfileChanges): RepositoryResult<Unit>

    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): RepositoryResult<AvatarUpload>
}

interface LibraryContentRepository {
    fun invalidate()

    suspend fun detail(id: Int, private: Boolean = false): RepositoryResult<LibraryFunction>

    suspend fun mine(
        pageNum: Int = 1,
        pageSize: Int = 100,
        forceRefresh: Boolean = false,
    ): RepositoryResult<LibraryPage>

    suspend fun byAuthor(
        authorId: Int,
        pageNum: Int,
        pageSize: Int = 20,
    ): RepositoryResult<LibraryPage>

    suspend fun favorites(): RepositoryResult<LibraryPage>

    suspend fun quota(forceRefresh: Boolean = false): RepositoryResult<LibraryQuota>

    suspend fun upload(content: String): RepositoryResult<LibraryUpload>

    suspend fun update(id: Int, changes: LibraryChanges): RepositoryResult<Unit>

    suspend fun delete(id: Int): RepositoryResult<Unit>

    suspend fun publish(id: Int, verification: String): RepositoryResult<Publication>

    suspend fun syncPublic(id: Int): RepositoryResult<Unit>

    suspend fun like(id: Int): RepositoryResult<LibraryReaction>

    suspend fun favorite(id: Int): RepositoryResult<FavoriteState>

    suspend fun share(id: Int, importToLocal: Boolean = false): RepositoryResult<LibraryShare>

    suspend fun report(report: AbuseReport): RepositoryResult<Unit>

    suspend fun leaderboard(): RepositoryResult<Leaderboard>
}

data class ActivityDashboard(
    val config: RepositoryResult<ActivityConfig>,
    val summary: RepositoryResult<ActivitySummary>,
    val ledger: RepositoryResult<ActivityLedgerPage>,
    val redemptions: RepositoryResult<RewardRedemptions>,
    val tiers: RepositoryResult<TierDetails>,
)

interface ActivityRepository {
    suspend fun dashboard(): ActivityDashboard

    suspend fun ledger(cursor: Int?): RepositoryResult<ActivityLedgerPage>

    suspend fun redeem(
        productId: String,
        fulfillmentInfo: String?,
    ): RepositoryResult<RedemptionReceipt>
}

interface MessageRepository {
    suspend fun load(
        page: Int,
        pageSize: Int = 20,
        unreadOnly: Boolean = false,
    ): RepositoryResult<MessagePage>

    suspend fun unreadCount(): RepositoryResult<UnreadCount>

    suspend fun markRead(id: Int): RepositoryResult<Unit>

    suspend fun delete(id: Int): RepositoryResult<Unit>
}

interface FavoriteCollectionRepository {
    suspend fun load(): List<LibraryFunction>

    suspend fun remove(id: Int): RepositoryResult<FavoriteState>
}

fun interface BrowserLinkRepository {
    suspend fun resolveBrowserUrl(url: String): Result<String>
}
