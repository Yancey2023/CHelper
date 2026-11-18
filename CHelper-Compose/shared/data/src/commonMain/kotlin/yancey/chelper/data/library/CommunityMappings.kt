package yancey.chelper.data.library

import yancey.chelper.data.remote.library.model.ActivityConfig as ActivityConfigDto
import yancey.chelper.data.remote.library.model.ActivityLedgerItem as ActivityLedgerItemDto
import yancey.chelper.data.remote.library.model.ActivityLedgerPage as ActivityLedgerPageDto
import yancey.chelper.data.remote.library.model.ActivityRule as ActivityRuleDto
import yancey.chelper.data.remote.library.model.ActivitySummary as ActivitySummaryDto
import yancey.chelper.data.remote.library.model.LeaderboardData as LeaderboardDataDto
import yancey.chelper.data.remote.library.model.LeaderboardUser as LeaderboardUserDto
import yancey.chelper.data.remote.library.model.MessageListResponse as MessageListResponseDto
import yancey.chelper.data.remote.library.model.RedeemActivityResponse as RedeemActivityResponseDto
import yancey.chelper.data.remote.library.model.RewardProduct as RewardProductDto
import yancey.chelper.data.remote.library.model.RewardRedemption as RewardRedemptionDto
import yancey.chelper.data.remote.library.model.RewardRedemptions as RewardRedemptionsDto
import yancey.chelper.data.remote.library.model.SiteMessage as SiteMessageDto
import yancey.chelper.data.remote.library.model.TierDetails as TierDetailsDto
import yancey.chelper.data.remote.library.model.TierGrant as TierGrantDto
import yancey.chelper.data.remote.library.model.TierSummary as TierSummaryDto
import yancey.chelper.data.remote.library.model.TierTimelineItem as TierTimelineItemDto
import yancey.chelper.data.remote.library.model.UnreadCountResponse as UnreadCountResponseDto
import yancey.chelper.data.remote.library.model.UserProfileData as UserProfileDataDto
import yancey.chelper.data.remote.library.service.CommandLabPublicService.GetFunctionsResponse as GetFunctionsResponseDto
import yancey.chelper.data.remote.library.service.CommandLabPublicService.LibraryLikeResponse as LibraryLikeResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.FavoriteLibraryResponse as FavoriteLibraryResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.LibraryShareResponse as LibraryShareResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.QuotaResponse as QuotaResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.UploadAvatarResponse as UploadAvatarResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.UploadLibraryResponse as UploadLibraryResponseDto
import yancey.chelper.data.remote.library.service.CommandLabUserService.User as UserDto
import yancey.chelper.domain.library.model.*

internal fun ActivityRuleDto.toDomain() =
    ActivityRule(
        eventType = eventType,
        title = title,
        points = points,
        description = description,
        isActive = isActive,
    )

internal fun RewardProductDto.toDomain() =
    RewardProduct(
        id = id,
        name = name,
        description = description,
        rewardType = rewardType,
        originalPrice = originalPrice,
        salePrice = salePrice,
        cost = cost,
        hasDiscount = hasDiscount,
        stock = stock,
        available = available,
        tier = tier,
        days = days,
        fulfillmentNote = fulfillmentNote,
    )

internal fun ActivityConfigDto.toDomain() =
    ActivityConfig(
        name = name,
        rules = rules.map { it.toDomain() },
        products = products.map { it.toDomain() },
    )

internal fun TierGrantDto.toDomain() =
    TierGrant(
        id = id,
        tier = tier,
        tierName = tierName,
        label = label,
        startsAt = startsAt,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        status = status,
    )

internal fun TierSummaryDto.toDomain() =
    TierSummary(
        tier = tier,
        tierName = tierName,
        status = status,
        startsAt = startsAt,
        expiresAt = expiresAt,
        nextStartsAt = nextStartsAt,
        grantCount = grantCount,
    )

internal fun TierTimelineItemDto.toDomain() =
    TierTimelineItem(
        tier = tier,
        tierName = tierName,
        startsAt = startsAt,
        expiresAt = expiresAt,
    )

internal fun TierDetailsDto.toDomain() =
    TierDetails(
        baseTier = baseTier,
        baseTierName = baseTierName,
        effectiveTier = effectiveTier,
        effectiveTierName = effectiveTierName,
        effectiveTierExpiresAt = effectiveTierExpiresAt,
        nextTierChangeAt = nextTierChangeAt,
        tierSummaries = tierSummaries.map { it.toDomain() },
        effectiveTimeline = effectiveTimeline.map { it.toDomain() },
        grants = grants.map { it.toDomain() },
    )

internal fun ActivitySummaryDto.toDomain() =
    ActivitySummary(
        points = points,
        totalEarned = totalEarned,
        totalSpent = totalSpent,
        totalReversed = totalReversed,
        earnedByType = earnedByType.toMap(),
        tier = tier?.toDomain(),
    )

internal fun ActivityLedgerItemDto.toDomain() =
    ActivityLedgerItem(
        id = id,
        eventType = eventType,
        delta = delta,
        description = description,
        createdAt = createdAt,
    )

internal fun ActivityLedgerPageDto.toDomain() =
    ActivityLedgerPage(
        items = items.map { it.toDomain() },
        nextCursor = nextCursor,
        hasMore = hasMore,
    )

internal fun RewardRedemptionDto.toDomain() =
    RewardRedemption(
        id = id,
        productId = productId,
        productName = productName,
        rewardType = rewardType,
        pointsCost = pointsCost,
        status = status,
        fulfillmentInfo = fulfillmentInfo,
        fulfilledAt = fulfilledAt,
        createdAt = createdAt,
    )

internal fun RewardRedemptionsDto.toDomain() =
    RewardRedemptions(items = items.map { it.toDomain() })

internal fun RedeemActivityResponseDto.toDomain() =
    RedemptionReceipt(
        remainingPoints = remainingPoints,
        product = product?.toDomain(),
        redemption = redemption?.toDomain(),
        effectiveTier = effectiveTier,
        grant = grant?.toDomain(),
    )

internal fun UserProfileDataDto.toDomain() =
    UserProfile(
        id = id,
        nickname = nickname,
        avatarUrl = avatarUrl,
        tier = tier,
        signature = signature,
        homepage = homepage,
        totalPublicFunctions = totalPublicFunctions,
        totalLikes = totalLikes,
        recentFunctions = recentFunctions?.toList(),
        userTitle = userTitle,
        email = email,
        createdAt = createdAt,
        tierExpiresAt = tierExpiresAt,
    )

internal fun LeaderboardUserDto.toDomain() =
    LeaderboardEntry(
        id = id,
        avatarUrl = avatarUrl,
        nickname = nickname,
        tier = tier,
        userTitle = userTitle,
        totalLikes = totalLikes,
        totalFunctions = totalFunctions,
    )

internal fun LeaderboardDataDto.toDomain() =
    Leaderboard(leaderboard = leaderboard?.map { it.toDomain() })

internal fun SiteMessageDto.toDomain() =
    InboxMessage(
        id = id,
        title = title,
        content = content,
        msgType = msgType,
        senderId = senderId,
        createdAt = createdAt,
        isRead = isRead,
        readAt = readAt,
        isGlobal = isGlobal,
    )

internal fun MessageListResponseDto.toDomain() =
    MessagePage(
        messages = messages?.map { it.toDomain() },
        total = total,
        page = page,
        pageSize = pageSize,
    )

internal fun UnreadCountResponseDto.toDomain() = UnreadCount(count = count)

internal fun UserDto.toDomain() =
    AccountUser(
        id = id,
        email = email,
        nickname = nickname,
        isGuest = isGuest,
        isAdmin = isAdmin,
        isModerator = isModerator,
        isVerified = isVerified,
        createdAt = createdAt,
        gravatarUrl = gravatarUrl,
        tier = tier,
    )

internal fun UploadAvatarResponseDto.toDomain() = AvatarUpload(avatarUrl = avatarUrl)

internal fun UploadLibraryResponseDto.toDomain() = LibraryUpload(uuid = uuid)

internal fun FavoriteLibraryResponseDto.toDomain() =
    FavoriteState(
        isFavorited = isFavorited,
        uuid = uuid,
    )

internal fun LibraryShareResponseDto.toDomain() =
    LibraryShare(
        code = code,
        shareUrl = shareUrl,
        sharePath = sharePath,
        libraryId = libraryId,
        libraryName = libraryName,
        sharerNickname = sharerNickname,
        importToLocal = importToLocal,
        expiresAt = expiresAt,
        downloadUrl = downloadUrl,
    )

internal fun QuotaResponseDto.toDomain() =
    LibraryQuota(
        used = used,
        limit = limit,
    )

internal fun GetFunctionsResponseDto.toDomain() =
    LibraryPage(
        functions = functions?.toList(),
        currentPage = currentPage,
        perPage = perPage,
        totalCount = totalCount,
    )

internal fun LibraryLikeResponseDto.toDomain() =
    LibraryReaction(
        likeCount = likeCount,
        isLiked = isLiked,
        dailyFirstLikePoints = dailyFirstLikePoints,
        pointsAwarded = pointsAwarded,
    )
