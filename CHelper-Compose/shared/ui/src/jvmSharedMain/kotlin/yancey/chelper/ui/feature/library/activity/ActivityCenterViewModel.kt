/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package yancey.chelper.ui.feature.library.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yancey.chelper.data.auth.LoginUtil
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.model.ActivityConfig
import yancey.chelper.data.remote.library.model.ActivityLedgerItem
import yancey.chelper.data.remote.library.model.ActivityLedgerPage
import yancey.chelper.data.remote.library.model.ActivitySummary
import yancey.chelper.data.remote.library.model.BaseResult
import yancey.chelper.data.remote.library.model.RedeemActivityRequest
import yancey.chelper.data.remote.library.model.RewardProduct
import yancey.chelper.data.remote.library.model.RewardRedemption
import yancey.chelper.data.remote.library.model.RewardRedemptions
import yancey.chelper.data.remote.library.model.TierDetails

class ActivityCenterViewModel : ViewModel() {
    var config by mutableStateOf<ActivityConfig?>(null)
        private set

    var summary by mutableStateOf<ActivitySummary?>(null)
        private set

    var tierDetails by mutableStateOf<TierDetails?>(null)
        private set

    val ledger = mutableStateListOf<ActivityLedgerItem>()
    val redemptions = mutableStateListOf<RewardRedemption>()
    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var redeemingProductId by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var actionMessage by mutableStateOf<String?>(null)
    var hasMoreLedger by mutableStateOf(false)
        private set

    private var nextLedgerCursor: Int? = null
    private var initialized = false

    fun load(force: Boolean = false) {
        if (isLoading || (!force && initialized)) return
        initialized = true
        viewModelScope.launch {
            if (!LoginUtil.isLoggedIn || LoginUtil.currentUser?.isGuest == true) {
                errorMessage = "请先登录正式账号使用创作季"
                return@launch
            }
            isLoading = true
            errorMessage = null
            try {
                val payload =
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            val configRequest = async {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.getActivityConfig()
                            }
                            val summaryRequest = async {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.getMyActivity()
                            }
                            val ledgerRequest = async {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.getActivityLedger()
                            }
                            val redemptionRequest = async {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.getActivityRedemptions()
                            }
                            val tierRequest = async {
                                ServiceManager.COMMAND_LAB_USER_SERVICE.getMyTierDetails()
                            }
                            ActivityPayload(
                                configRequest.await(),
                                summaryRequest.await(),
                                ledgerRequest.await(),
                                redemptionRequest.await(),
                                tierRequest.await(),
                            )
                        }
                    }

                config = payload.config.data ?: config
                summary = payload.summary.data ?: summary
                tierDetails = payload.tiers.data ?: payload.summary.data?.tier ?: tierDetails
                payload.ledger.data?.let { page ->
                    ledger.clear()
                    ledger.addAll(page.items)
                    nextLedgerCursor = page.nextCursor
                    hasMoreLedger = page.hasMore
                }
                payload.redemptions.data?.let { data ->
                    redemptions.clear()
                    redemptions.addAll(data.items)
                }

                errorMessage =
                    listOf(
                            payload.config,
                            payload.summary,
                            payload.ledger,
                            payload.redemptions,
                            payload.tiers,
                        )
                        .firstOrNull { !it.isSuccess() }
                        ?.message
            } catch (e: Exception) {
                errorMessage = "网络错误: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMoreLedger() {
        if (isLoadingMore || !hasMoreLedger) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val response =
                    withContext(Dispatchers.IO) {
                        ServiceManager.COMMAND_LAB_USER_SERVICE.getActivityLedger(nextLedgerCursor)
                    }
                if (response.isSuccess() && response.data != null) {
                    val page = response.data!!
                    ledger.addAll(page.items)
                    nextLedgerCursor = page.nextCursor
                    hasMoreLedger = page.hasMore
                } else {
                    actionMessage = response.message ?: "流水加载失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun redeem(product: RewardProduct, fulfillmentInfo: String? = null) {
        val productId = product.id ?: return
        if (redeemingProductId != null) return
        viewModelScope.launch {
            redeemingProductId = productId
            try {
                val response =
                    withContext(Dispatchers.IO) {
                        ServiceManager.COMMAND_LAB_USER_SERVICE.redeemActivityProduct(
                            productId,
                            RedeemActivityRequest(fulfillmentInfo),
                        )
                    }
                if (response.isSuccess()) {
                    actionMessage = response.message ?: "兑换成功"
                    load(force = true)
                } else {
                    actionMessage = response.message ?: "兑换失败"
                }
            } catch (e: Exception) {
                actionMessage = "网络错误: ${e.message}"
            } finally {
                redeemingProductId = null
            }
        }
    }

    private data class ActivityPayload(
        val config: BaseResult<ActivityConfig?>,
        val summary: BaseResult<ActivitySummary?>,
        val ledger: BaseResult<ActivityLedgerPage?>,
        val redemptions: BaseResult<RewardRedemptions?>,
        val tiers: BaseResult<TierDetails?>,
    )
}
