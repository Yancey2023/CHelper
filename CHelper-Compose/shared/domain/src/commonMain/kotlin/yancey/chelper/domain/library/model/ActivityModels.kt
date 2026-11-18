package yancey.chelper.domain.library.model

data class ActivityRule(
    val eventType: String? = null,
    val title: String? = null,
    val points: Double? = null,
    val description: String? = null,
    val isActive: Boolean? = null,
)

data class RewardProduct(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val rewardType: String? = null,
    val originalPrice: Double? = null,
    val salePrice: Double? = null,
    val cost: Double? = null,
    val hasDiscount: Boolean? = null,
    val stock: Int? = null,
    val available: Boolean? = null,
    val tier: Int? = null,
    val days: Int? = null,
    val fulfillmentNote: String? = null,
)

data class ActivityConfig(
    val name: String? = null,
    val rules: List<ActivityRule> = emptyList(),
    val products: List<RewardProduct> = emptyList(),
)

data class TierGrant(
    val id: Int? = null,
    val tier: Int? = null,
    val tierName: String? = null,
    val label: String? = null,
    val startsAt: String? = null,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val status: String? = null,
)

data class TierSummary(
    val tier: Int? = null,
    val tierName: String? = null,
    val status: String? = null,
    val startsAt: String? = null,
    val expiresAt: String? = null,
    val nextStartsAt: String? = null,
    val grantCount: Int? = null,
)

data class TierTimelineItem(
    val tier: Int? = null,
    val tierName: String? = null,
    val startsAt: String? = null,
    val expiresAt: String? = null,
)

data class TierDetails(
    val baseTier: Int? = null,
    val baseTierName: String? = null,
    val effectiveTier: Int? = null,
    val effectiveTierName: String? = null,
    val effectiveTierExpiresAt: String? = null,
    val nextTierChangeAt: String? = null,
    val tierSummaries: List<TierSummary> = emptyList(),
    val effectiveTimeline: List<TierTimelineItem> = emptyList(),
    val grants: List<TierGrant> = emptyList(),
)

data class ActivitySummary(
    val points: Double? = null,
    val totalEarned: Double? = null,
    val totalSpent: Double? = null,
    val totalReversed: Double? = null,
    val earnedByType: Map<String, Double> = emptyMap(),
    val tier: TierDetails? = null,
)

data class ActivityLedgerItem(
    val id: Int? = null,
    val eventType: String? = null,
    val delta: Double? = null,
    val description: String? = null,
    val createdAt: String? = null,
)

data class ActivityLedgerPage(
    val items: List<ActivityLedgerItem> = emptyList(),
    val nextCursor: Int? = null,
    val hasMore: Boolean = false,
)

data class RewardRedemption(
    val id: Int? = null,
    val productId: String? = null,
    val productName: String? = null,
    val rewardType: String? = null,
    val pointsCost: Double? = null,
    val status: String? = null,
    val fulfillmentInfo: String? = null,
    val fulfilledAt: String? = null,
    val createdAt: String? = null,
)

data class RewardRedemptions(val items: List<RewardRedemption> = emptyList())

data class RedemptionReceipt(
    val remainingPoints: Double? = null,
    val product: RewardProduct? = null,
    val redemption: RewardRedemption? = null,
    val effectiveTier: Int? = null,
    val grant: TierGrant? = null,
)
