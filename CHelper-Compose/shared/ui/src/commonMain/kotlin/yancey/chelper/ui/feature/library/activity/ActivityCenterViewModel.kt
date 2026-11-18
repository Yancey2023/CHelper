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

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

class ActivityCenterViewModel(
    private val accounts: AccountRepository,
    private val activity: ActivityRepository,
) : ViewModel() {
    var config by mutableStateOf<ActivityConfig?>(null)
        private set

    var summary by mutableStateOf<ActivitySummary?>(null)
        private set

    var tierDetails by mutableStateOf<TierDetails?>(null)
        private set

    var ledger by mutableStateOf<List<ActivityLedgerItem>>(emptyList())
        private set

    var redemptions by mutableStateOf<List<RewardRedemption>>(emptyList())
        private set

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
    private var version = 0L
    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            var revision = accounts.state.value.revision
            accounts.state.collect { account ->
                if (revision != account.revision) {
                    revision = account.revision
                    version++
                    loadJob?.cancel()
                    moreJob?.cancel()
                    config = null
                    summary = null
                    tierDetails = null
                    ledger = emptyList()
                    redemptions = emptyList()
                    initialized = false
                    isLoading = false
                    isLoadingMore = false
                    hasMoreLedger = false
                    if (account.user != null) load()
                }
            }
        }
    }

    fun load(force: Boolean = false) {
        if (!accounts.isLoggedIn || accounts.currentUser?.isGuest == true) {
            errorMessage = "请先登录正式账号使用创作季"
            return
        }
        if (!force && (isLoading || initialized)) return
        loadJob?.cancel()
        moreJob?.cancel()
        val generation = ++version
        isLoading = true
        isLoadingMore = false
        errorMessage = null
        loadJob = viewModelScope.launch {
            try {
                val payload = activity.dashboard()
                ensureActive()
                if (generation != version) return@launch
                config = payload.config.data ?: config
                summary = payload.summary.data ?: summary
                tierDetails = payload.tiers.data ?: payload.summary.data?.tier ?: tierDetails
                payload.ledger.data?.let {
                    ledger = it.items.toList()
                    nextLedgerCursor = it.nextCursor
                    hasMoreLedger = it.hasMore
                }
                payload.redemptions.data?.let { redemptions = it.items.toList() }
                val failed =
                    listOf(
                            payload.config,
                            payload.summary,
                            payload.ledger,
                            payload.redemptions,
                            payload.tiers,
                        )
                        .firstOrNull { !it.isSuccess() }
                errorMessage = failed?.message ?: if (failed != null) "加载失败" else null
                initialized = failed == null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "加载失败"
            } finally {
                if (generation == version) isLoading = false
            }
        }
    }

    fun loadMoreLedger() {
        if (isLoading || isLoadingMore || !hasMoreLedger) return
        val generation = version
        val cursor = nextLedgerCursor
        isLoadingMore = true
        moreJob = viewModelScope.launch {
            try {
                val response = activity.ledger(cursor)
                ensureActive()
                if (generation != version) return@launch
                if (!response.isSuccess()) error(response.message ?: "流水加载失败")
                response.data?.let {
                    ledger = (ledger + it.items).distinctBy { row -> row.id }
                    nextLedgerCursor = it.nextCursor
                    hasMoreLedger = it.hasMore
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionMessage = e.message ?: "流水加载失败"
            } finally {
                if (generation == version) isLoadingMore = false
            }
        }
    }

    fun redeem(product: RewardProduct, fulfillmentInfo: String? = null) {
        val id = product.id ?: return
        if (redeemingProductId != null) return
        redeemingProductId = id
        viewModelScope.launch {
            try {
                val result = activity.redeem(id, fulfillmentInfo)
                actionMessage = result.message ?: if (result.isSuccess()) "兑换成功" else "兑换失败"
                if (result.isSuccess()) load(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionMessage = e.message ?: "兑换失败"
            } finally {
                redeemingProductId = null
            }
        }
    }
}
