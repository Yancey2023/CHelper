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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlin.math.abs
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.ActivityLedgerItem
import yancey.chelper.domain.library.model.ActivityRule
import yancey.chelper.domain.library.model.RewardProduct
import yancey.chelper.domain.library.model.RewardRedemption
import yancey.chelper.domain.library.model.TierDetails
import yancey.chelper.domain.library.model.TierGrant
import yancey.chelper.domain.library.model.TierSummary
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.designsystem.dialog.DialogContainer
import yancey.chelper.ui.designsystem.dialog.IsConfirmDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.text.formatUnixTime
import yancey.chelper.ui.designsystem.widget.Divider
import yancey.chelper.ui.designsystem.widget.DividerVertical
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.designsystem.widget.TextField
import yancey.chelper.ui.navigation.LeaderboardScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.LocalClientServices

@Composable
fun ActivityCenterScreen(
    navController: NavHostController,
    initialSection: Int = 0,
    accounts: AccountRepository = LocalClientServices.current.accounts,
    activity: ActivityRepository = LocalClientServices.current.activity,
    viewModel: ActivityCenterViewModel = viewModel { ActivityCenterViewModel(accounts, activity) },
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    var confirmProduct by remember { mutableStateOf<RewardProduct?>(null) }
    var fulfillmentProduct by remember { mutableStateOf<RewardProduct?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(viewModel.actionMessage) {
        viewModel.actionMessage?.let {
            ClientMessages.show(it)
            viewModel.actionMessage = null
        }
        onDispose {}
    }

    RootViewWithHeaderAndCopyright(
        title = "CommandLab 创作季",
        showBack = true,
        onBack = { navController.popBackStack() },
        headerRight = {
            Icon(
                id = Res.drawable.refresh_cw,
                contentDescription = "刷新",
                modifier =
                    Modifier.padding(5.dp).size(24.dp).clickable { viewModel.load(force = true) },
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ActivityTabs(selectedSection) { selectedSection = it }
            when {
                viewModel.isLoading && viewModel.summary == null -> CenterMessage("正在同步活动数据...")
                viewModel.errorMessage != null && viewModel.summary == null ->
                    CenterMessage(
                        viewModel.errorMessage ?: "加载失败",
                        action = "点击重试",
                        onAction = { viewModel.load(force = true) },
                    )

                selectedSection == 0 -> ActivityOverview(viewModel, navController)
                selectedSection == 1 ->
                    ActivityStore(
                        viewModel,
                        onRedeem = { product ->
                            if (product.rewardType == "physical") fulfillmentProduct = product
                            else confirmProduct = product
                        },
                    )

                selectedSection == 2 -> ActivityLedger(viewModel)
                else -> ActivityTier(viewModel.tierDetails ?: viewModel.summary?.tier)
            }
        }
    }

    confirmProduct?.let { product ->
        IsConfirmDialog(
            onDismissRequest = { confirmProduct = null },
            title = "确认兑换",
            content = "使用 ${formatPoints(product.cost)} PTS 兑换「${product.name ?: "商品"}」？",
            confirmText = "兑换",
            onConfirm = {
                confirmProduct = null
                viewModel.redeem(product)
            },
        )
    }
    fulfillmentProduct?.let { product ->
        FulfillmentDialog(
            product = product,
            onDismiss = { fulfillmentProduct = null },
            onConfirm = { info ->
                fulfillmentProduct = null
                viewModel.redeem(product, info)
            },
        )
    }
}

@Composable
private fun ActivityTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("概览", "商店", "流水", "Tier")
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected == index) CHelperTheme.colors.mainColor
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style =
                        TextStyle(
                            fontSize = 13.sp,
                            fontWeight =
                                if (selected == index) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (selected == index) Color.White
                                else CHelperTheme.colors.textSecondary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ActivityOverview(viewModel: ActivityCenterViewModel, navController: NavHostController) {
    val summary = viewModel.summary
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CHelperTheme.colors.mainColor)
                        .padding(20.dp)
            ) {
                Text(
                    "可用积分",
                    style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)),
                )
                Text(
                    text = "${formatPoints(summary?.points)} PTS",
                    style =
                        TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        "累计 ${formatPoints(summary?.totalEarned)} · 已用 ${formatPoints(summary?.totalSpent)} · 扣回 ${
                        formatPoints(
                            summary?.totalReversed
                        )
                    }",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f)),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier =
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { navController.navigate(LeaderboardScreenKey) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("查看创作者排行榜", style = TextStyle(fontSize = 12.sp, color = Color.White))
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Res.drawable.chevron_right,
                        modifier = Modifier.size(14.dp),
                        contentDescription = null,
                    )
                }
            }
        }
        item { SectionTitle("积分规则") }
        items(viewModel.config?.rules.orEmpty(), key = { it.eventType ?: it.title.orEmpty() }) {
            ActivityRuleCard(it)
        }
    }
}

@Composable
private fun ActivityRuleCard(rule: ActivityRule) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rule.title ?: "积分规则",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                rule.description.orEmpty(),
                style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Text(
            "+${formatPoints(rule.points)} PTS",
            style =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.mainColor,
                ),
        )
    }
}

@Composable
private fun ActivityStore(viewModel: ActivityCenterViewModel, onRedeem: (RewardProduct) -> Unit) {
    val balance = viewModel.summary?.points ?: 0.0
    val baseTier = viewModel.tierDetails?.baseTier ?: viewModel.summary?.tier?.baseTier ?: 0
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("积分余额", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                Text(
                    "${formatPoints(balance)} PTS",
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.mainColor,
                        ),
                )
            }
        }
        item { SectionTitle("在售商品") }
        items(viewModel.config?.products.orEmpty(), key = { it.id.orEmpty() }) { product ->
            ProductCard(
                product,
                balance,
                baseTier,
                viewModel.redeemingProductId == product.id,
                onRedeem,
            )
        }
        item { SectionTitle("我的兑换") }
        if (viewModel.redemptions.isEmpty()) {
            item { EmptyCard("还没有兑换记录") }
        } else {
            items(viewModel.redemptions, key = { it.id ?: 0 }) { RedemptionCard(it) }
        }
    }
}

@Composable
private fun ProductCard(
    product: RewardProduct,
    balance: Double,
    baseTier: Int,
    isRedeeming: Boolean,
    onRedeem: (RewardProduct) -> Unit,
) {
    val cost = product.cost ?: product.originalPrice ?: 0.0
    val coveredTier = product.rewardType == "tier" && baseTier >= (product.tier ?: Int.MAX_VALUE)
    val enabled = product.available == true && balance >= cost && !coveredTier && !isRedeeming
    val stateText =
        when {
            product.available != true -> "暂时售罄"
            coveredTier -> "永久 Tier 已覆盖"
            balance < cost -> "积分不足"
            isRedeeming -> "兑换中..."
            else -> product.fulfillmentNote ?: "可立即兑换"
        }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    when (product.rewardType) {
                        "tier" -> "Tier 权益"
                        "physical" -> "实物奖品"
                        else -> "虚拟奖品"
                    },
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.mainColor),
            )
            Spacer(Modifier.weight(1f))
            product.stock?.let {
                Text(
                    "剩余 $it",
                    style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            product.name ?: "未命名商品",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            product.description.orEmpty(),
            style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatPoints(cost)} PTS",
                style =
                    TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CHelperTheme.colors.mainColor,
                    ),
            )
            if (product.hasDiscount == true) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "原价 ${formatPoints(product.originalPrice)}",
                    style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(
                            if (enabled) CHelperTheme.colors.mainColor
                            else CHelperTheme.colors.background
                        )
                        .clickable(enabled = enabled) { onRedeem(product) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (isRedeeming) "兑换中" else "兑换",
                    style =
                        TextStyle(
                            fontSize = 13.sp,
                            color = if (enabled) Color.White else CHelperTheme.colors.textSecondary,
                        ),
                )
            }
        }
        Text(
            stateText,
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
        )
    }
}

@Composable
private fun RedemptionCard(item: RewardRedemption) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.productName ?: "兑换记录", style = TextStyle(fontWeight = FontWeight.Medium))
            Text(
                "${if (item.status == "fulfilled") "已完成" else "等待发放"} · ${item.createdAt.formatUnixTime()}",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Text(
            "-${formatPoints(item.pointsCost)}",
            style = TextStyle(color = CHelperTheme.colors.mainColor, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun ActivityLedger(viewModel: ActivityCenterViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CHelperTheme.colors.backgroundComponent)
                        .padding(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("当前余额", style = TextStyle(color = CHelperTheme.colors.textSecondary))
                Text(
                    "${formatPoints(viewModel.summary?.points)} PTS",
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = CHelperTheme.colors.mainColor,
                        ),
                )
            }
        }
        if (viewModel.ledger.isEmpty()) {
            item { EmptyCard("还没有积分流水") }
        } else {
            items(viewModel.ledger, key = { it.id ?: 0 }) { LedgerCard(it) }
        }
        if (viewModel.hasMoreLedger) {
            item {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !viewModel.isLoadingMore) {
                                viewModel.loadMoreLedger()
                            }
                            .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (viewModel.isLoadingMore) "加载中..." else "加载更多",
                        style = TextStyle(color = CHelperTheme.colors.mainColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerCard(item: ActivityLedgerItem) {
    val delta = item.delta ?: 0.0
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.description ?: "积分变动", style = TextStyle(fontWeight = FontWeight.Medium))
            Text(
                item.createdAt.formatUnixTime(),
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Text(
            "${if (delta >= 0) "+" else ""}${formatPoints(delta)}",
            style =
                TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (delta >= 0) CHelperTheme.colors.mainColor else Color(0xFFD32F2F),
                ),
        )
    }
}

@Composable
private fun ActivityTier(details: TierDetails?) {
    if (details == null) {
        CenterMessage("暂无 Tier 数据")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CHelperTheme.colors.mainColor)
                        .padding(20.dp)
            ) {
                Text(
                    "当前有效 Tier",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)),
                )
                Text(
                    "Tier ${details.effectiveTier ?: 0} · ${details.effectiveTierName ?: "普通用户"}",
                    style =
                        TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                )
                Text(
                    details.nextTierChangeAt?.let { "下一次调整：${it.formatUnixTime()}" }
                        ?: "永久基础 Tier：${details.baseTier ?: 0}",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f)),
                )
            }
        }
        item { SectionTitle("各级权益") }
        items(details.tierSummaries, key = { it.tier ?: 0 }) { TierSummaryCard(it) }
        item { SectionTitle("授权明细") }
        if (details.grants.isEmpty()) {
            item { EmptyCard("还没有独立的限时 Tier 授权") }
        } else {
            items(details.grants, key = { it.id ?: 0 }) { TierGrantCard(it) }
        }
    }
}

@Composable
private fun TierSummaryCard(item: TierSummary) {
    val detail =
        when (item.status) {
            "permanent" -> "永久有效"
            "active" -> "有效至 ${item.expiresAt.formatUnixTime()}"
            "scheduled" -> "${item.nextStartsAt.formatUnixTime()} 开始"
            else -> "暂无权益"
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "T${item.tier ?: 0}",
            style =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.mainColor,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.tierName ?: "Tier", style = TextStyle(fontWeight = FontWeight.Medium))
            Text(
                detail,
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Text(
            tierStatusText(item.status),
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.mainColor),
        )
    }
}

@Composable
private fun TierGrantCard(item: TierGrant) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "T${item.tier ?: 0}",
            style =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.mainColor,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.label ?: item.tierName ?: "Tier 授权",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${item.startsAt.formatUnixTime()} 至 ${item.expiresAt.formatUnixTime()}",
                style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.textSecondary),
            )
        }
        Text(
            tierStatusText(item.status),
            style = TextStyle(fontSize = 11.sp, color = CHelperTheme.colors.mainColor),
        )
    }
}

@Composable
private fun FulfillmentDialog(
    product: RewardProduct,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val info = rememberTextFieldState()
    AppDialog(onDismissRequest = onDismiss) {
        DialogContainer(backgroundNoTranslate = true) {
            Column {
                Text(
                    text = "兑换 ${product.name ?: "实物奖品"}",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style =
                        TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                )
                Text(
                    "请填写收件人、联系电话和地址，内容会随兑换单提交给管理员。",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                )
                TextField(
                    state = info,
                    modifier = Modifier.fillMaxWidth().height(110.dp).padding(16.dp),
                    contentAlignment = Alignment.TopStart,
                    hint = "收件人 / 电话 / 地址",
                    lineLimits = TextFieldLineLimits.MultiLine(3, 6),
                )
                Divider(0.dp)
                Row(Modifier.height(45.dp)) {
                    DialogAction("取消", Modifier.weight(1f), onDismiss)
                    DividerVertical(0.dp)
                    DialogAction("确认兑换", Modifier.weight(1f)) {
                        val value = info.text.toString().trim()
                        if (value.isBlank()) ClientMessages.show("请填写收货信息") else onConfirm(value)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 17.sp, color = CHelperTheme.colors.mainColor))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style =
            TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = CHelperTheme.colors.textMain,
            ),
    )
}

@Composable
private fun EmptyCard(text: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = CHelperTheme.colors.textSecondary))
    }
}

@Composable
private fun CenterMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = TextStyle(color = CHelperTheme.colors.textSecondary))
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    action,
                    modifier = Modifier.clickable(onClick = onAction),
                    style = TextStyle(color = CHelperTheme.colors.mainColor),
                )
            }
        }
    }
}

private fun formatPoints(value: Double?): String {
    val number = value ?: 0.0
    val whole = number.toLong()
    return if (number == whole.toDouble()) whole.toString() else formatDecimalAmount(number)
}

private fun tierStatusText(status: String?): String =
    when (status) {
        "permanent" -> "永久"
        "active" -> "生效中"
        "scheduled" -> "等待启用"
        "expired" -> "已到期"
        "revoked" -> "已撤销"
        else -> "未生效"
    }

/** Decimal rounding preserves the previous US two-decimal display without a JVM formatter. */
internal fun formatDecimalAmount(number: Double): String {
    if (!number.isFinite()) return number.toString()
    val raw = abs(number).toString().uppercase()
    val coefficient = raw.substringBefore('E')
    val exponent = raw.substringAfter('E', "0").toInt()
    val point = coefficient.indexOf('.').let { if (it < 0) coefficient.length else it }
    val digits = coefficient.replace(".", "")
    val cutoff = point + exponent + 2
    var minor = if (cutoff <= 0) "0" else digits.padEnd(cutoff, '0').take(cutoff)
    if (cutoff >= 0 && (digits.getOrNull(cutoff) ?: '0') >= '5') {
        val chars = minor.toCharArray()
        var index = chars.lastIndex
        while (index >= 0 && chars[index] == '9') {
            chars[index] = '0'
            index--
        }
        if (index < 0) minor = "1" + chars.concatToString()
        else {
            chars[index]++
            minor = chars.concatToString()
        }
    }
    minor = minor.trimStart('0').ifEmpty { "0" }.padStart(3, '0')
    val sign = if (number < 0) "-" else ""
    return "$sign${minor.dropLast(2)}.${minor.takeLast(2)}".trimEnd('0').trimEnd('.')
}
