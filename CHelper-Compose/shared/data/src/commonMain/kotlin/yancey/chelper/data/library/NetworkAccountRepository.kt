package yancey.chelper.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.data.auth.GuestSession
import yancey.chelper.data.auth.LoginSession
import yancey.chelper.data.remote.UploadFile
import yancey.chelper.data.remote.library.model.UpdateProfileRequest
import yancey.chelper.data.remote.library.service.CommandLabAuthService
import yancey.chelper.data.remote.library.service.CommandLabPublicService
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

internal class NetworkAccountRepository(
    private val auth: CommandLabAuthService,
    private val user: CommandLabUserService,
    private val public: CommandLabPublicService,
    private val login: LoginSession,
    private val guest: GuestSession,
    private val cache: RepositoryCache,
    private val requests: AccountRequests,
) : AccountRepository {
    override val state = login.state
    override val accountId = login.accountId

    override suspend fun login(account: String, password: String): Result<AccountUser> {
        val response = login.login(account, password)
        if (response.isFailure) return Result.failure(requireNotNull(response.exceptionOrNull()))
        val authenticated = response.getOrThrow()
        val authenticatedUser = authenticated.user?.toDomain()
        val token = authenticated.token
        if (authenticatedUser?.id == null || token.isNullOrBlank())
            return Result.failure(IllegalStateException("登录响应缺少用户信息或令牌"))
        val owner = requireNotNull(authenticatedUser.id)
        val accepted = state.value
        fun checkSession() {
            if (
                login.currentToken != token ||
                    state.value.user?.id != authenticatedUser.id ||
                    state.value.revision != accepted.revision
            )
                throw CancellationException("账号已切换，请重新操作")
        }
        checkSession()
        // Migration is best effort and remains bound to the accepted login, including crypto work.
        try {
            withContext(AccountCredentials(token, owner, accepted.revision)) {
                val fingerprint = guest.getFingerprint()
                val signature =
                    withContext(Dispatchers.Default) { fingerprint?.let(guest::generateAuthCode) }
                if (fingerprint != null && signature != null)
                    requests.run {
                        if (
                            user
                                .guestMigrate(
                                    CommandLabUserService.GuestAuthRequest(fingerprint, signature)
                                )
                                .isSuccess()
                        )
                            guest.clearGuestSession()
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
        checkSession()
        return Result.success(authenticatedUser)
    }

    override fun logout() {
        login.logout()
        guest.clearGuestSession()
        cache.invalidate()
    }

    override suspend fun sendVerification(account: String, verification: String) =
        auth
            .sendCode(
                CommandLabUserService.SendCodeRequest(
                    specialCode = verification,
                    type = 0,
                    email = account.takeIf { '@' in it },
                    phone = account.takeUnless { '@' in it },
                )
            )
            .completion()

    override suspend fun register(details: Registration) =
        auth
            .register(
                CommandLabUserService.RegisterRequest(
                    specialCode = details.verification,
                    code = details.code,
                    email = details.account.takeIf { '@' in it },
                    phone = details.account.takeUnless { '@' in it },
                    nickname = details.nickname,
                    password = details.password,
                    androidId = guest.getFingerprint(),
                )
            )
            .completion()

    override suspend fun profile(id: Int, forceRefresh: Boolean) =
        cache.read("profile:$id", forceRefresh) {
            public.getUserProfile(id).outcome { it?.toDomain() ?: error("个人资料为空") }
        }

    override suspend fun updateProfile(id: Int, changes: ProfileChanges): RepositoryResult<Unit> {
        val revision = state.value.revision
        val result = requests.run {
            check(id == login.currentUser?.id) { "只能修改自己的资料" }
            user
                .updateProfile(
                    id,
                    UpdateProfileRequest(
                        changes.nickname,
                        changes.avatarUrl,
                        changes.homepage,
                        changes.signature,
                    ),
                )
                .completion()
        }
        if (result.isSuccess()) {
            cache.invalidate()
            withContext(Dispatchers.IO) {
                login.updateUser(id, revision) {
                    it.copy(
                        nickname = changes.nickname ?: it.nickname,
                        gravatarUrl = changes.avatarUrl ?: it.gravatarUrl,
                    )
                }
            }
        }
        return result
    }

    override suspend fun uploadAvatar(
        bytes: ByteArray,
        mimeType: String,
    ): RepositoryResult<AvatarUpload> {
        val before = state.value
        val owner = before.user?.id ?: error("请先登录")
        val result = requests.run {
            val ext =
                when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
            user.uploadAvatar(UploadFile(bytes.copyOf(), "avatar.$ext", mimeType)).outcome {
                it?.toDomain() ?: error("头像地址为空")
            }
        }
        if (result.isSuccess()) {
            cache.invalidate()
            withContext(Dispatchers.IO) {
                login.updateUser(owner, before.revision) {
                    it.copy(gravatarUrl = result.data?.avatarUrl ?: it.gravatarUrl)
                }
            }
        }
        return result
    }
}
