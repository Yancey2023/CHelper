/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package yancey.chelper.data.remote.library.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityRule(
    @SerialName("event_type") val eventType: String? = null,
    val title: String? = null,
    val points: Double? = null,
    val description: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class RewardProduct(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("reward_type") val rewardType: String? = null,
    @SerialName("original_price") val originalPrice: Double? = null,
    @SerialName("sale_price") val salePrice: Double? = null,
    val cost: Double? = null,
    @SerialName("has_discount") val hasDiscount: Boolean? = null,
    val stock: Int? = null,
    val available: Boolean? = null,
    val tier: Int? = null,
    val days: Int? = null,
    @SerialName("fulfillment_note") val fulfillmentNote: String? = null,
)

@Serializable
data class ActivityConfig(
    val name: String? = null,
    val rules: List<ActivityRule> = emptyList(),
    val products: List<RewardProduct> = emptyList(),
)

@Serializable
data class TierGrant(
    val id: Int? = null,
    val tier: Int? = null,
    @SerialName("tier_name") val tierName: String? = null,
    val label: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
    val status: String? = null,
)

@Serializable
data class TierSummary(
    val tier: Int? = null,
    @SerialName("tier_name") val tierName: String? = null,
    val status: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("next_starts_at") val nextStartsAt: String? = null,
    @SerialName("grant_count") val grantCount: Int? = null,
)

@Serializable
data class TierTimelineItem(
    val tier: Int? = null,
    @SerialName("tier_name") val tierName: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class TierDetails(
    @SerialName("base_tier") val baseTier: Int? = null,
    @SerialName("base_tier_name") val baseTierName: String? = null,
    @SerialName("effective_tier") val effectiveTier: Int? = null,
    @SerialName("effective_tier_name") val effectiveTierName: String? = null,
    @SerialName("effective_tier_expires_at") val effectiveTierExpiresAt: String? = null,
    @SerialName("next_tier_change_at") val nextTierChangeAt: String? = null,
    @SerialName("tier_summaries") val tierSummaries: List<TierSummary> = emptyList(),
    @SerialName("effective_timeline") val effectiveTimeline: List<TierTimelineItem> = emptyList(),
    val grants: List<TierGrant> = emptyList(),
)

@Serializable
data class ActivitySummary(
    val points: Double? = null,
    @SerialName("total_earned") val totalEarned: Double? = null,
    @SerialName("total_spent") val totalSpent: Double? = null,
    @SerialName("total_reversed") val totalReversed: Double? = null,
    @SerialName("earned_by_type") val earnedByType: Map<String, Double> = emptyMap(),
    val tier: TierDetails? = null,
)

@Serializable
data class ActivityLedgerItem(
    val id: Int? = null,
    @SerialName("event_type") val eventType: String? = null,
    val delta: Double? = null,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ActivityLedgerPage(
    val items: List<ActivityLedgerItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: Int? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class RewardRedemption(
    val id: Int? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("reward_type") val rewardType: String? = null,
    @SerialName("points_cost") val pointsCost: Double? = null,
    val status: String? = null,
    @SerialName("fulfillment_info") val fulfillmentInfo: String? = null,
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable data class RewardRedemptions(val items: List<RewardRedemption> = emptyList())

@Serializable
data class RedeemActivityRequest(
    @SerialName("fulfillment_info") val fulfillmentInfo: String? = null
)

@Serializable
data class RedeemActivityResponse(
    @SerialName("remaining_points") val remainingPoints: Double? = null,
    val product: RewardProduct? = null,
    val redemption: RewardRedemption? = null,
    @SerialName("effective_tier") val effectiveTier: Int? = null,
    val grant: TierGrant? = null,
)
