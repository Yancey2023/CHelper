/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import yancey.chelper.R
import yancey.chelper.network.library.data.SiteMessage
import yancey.chelper.ui.common.CHelperTheme
import yancey.chelper.ui.common.dialog.ChoosingDialog
import yancey.chelper.ui.common.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.common.widget.Icon
import yancey.chelper.ui.common.widget.Text
import androidx.compose.foundation.layout.heightIn
import yancey.chelper.ui.common.dialog.CustomDialog

@Composable
fun MessageScreen(
    viewModel: MessageViewModel = viewModel()
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (viewModel.messages.isEmpty() && !viewModel.isLoading) {
            viewModel.loadMessages()
        }
    }

    var selectedMessageForAction by remember { mutableStateOf<SiteMessage?>(null) }

    // 滚动到底部自动加载下一页
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && !viewModel.isLoading && viewModel.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }.collect { if (it) viewModel.loadMore() }
    }

    RootViewWithHeaderAndCopyright(
        title = "站内信",
        headerRight = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 只看未读 toggle（eye=已开启过滤，eye_off=显示全部）
                Icon(
                    id = if (viewModel.showUnreadOnly) R.drawable.eye else R.drawable.eye_off,
                    modifier = Modifier
                        .clickable {
                            viewModel.showUnreadOnly = !viewModel.showUnreadOnly
                            viewModel.refresh()
                        }
                        .padding(5.dp)
                        .size(24.dp),
                    contentDescription = "只看未读"
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    id = R.drawable.refresh,
                    modifier = Modifier
                        .clickable { viewModel.refresh() }
                        .padding(5.dp)
                        .size(24.dp),
                    contentDescription = "刷新"
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 未读过滤指示条
            if (viewModel.showUnreadOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CHelperTheme.colors.mainColor.copy(alpha = 0.1f))
                        .padding(horizontal = 15.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "仅显示未读消息",
                        style = TextStyle(
                            color = CHelperTheme.colors.mainColor,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (viewModel.errorMessage != null && viewModel.messages.isEmpty()) {
                    // 错误状态
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(15.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CHelperTheme.colors.backgroundComponent),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.errorMessage ?: "加载失败",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "点击重试",
                                modifier = Modifier.clickable { viewModel.refresh() },
                                style = TextStyle(color = CHelperTheme.colors.mainColor)
                            )
                        }
                    }
                } else if (viewModel.messages.isEmpty() && !viewModel.isLoading) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(15.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CHelperTheme.colors.backgroundComponent),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                id = R.drawable.ic_mail,
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(0.3f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (viewModel.showUnreadOnly) "没有未读消息" else "暂无站内信",
                                style = TextStyle(color = CHelperTheme.colors.textSecondary)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(viewModel.messages) { _, message ->
                            MessageItem(
                                message = message,
                                onRead = { message.id?.let { viewModel.markAsRead(it) } },
                                onClickAction = { selectedMessageForAction = message }
                            )
                        }
                        if (viewModel.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "加载中...",
                                        style = TextStyle(color = CHelperTheme.colors.textSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedMessageForAction != null) {
        val msg = selectedMessageForAction!!
        val isUnreadMsg = msg.isRead != true

        MessageDetailDialog(
            message = msg,
            onDismiss = { selectedMessageForAction = null },
            onRead = {
                val id = msg.id
                if (id != null && isUnreadMsg) {
                    viewModel.markAsRead(id)
                }
            },
            onDelete = {
                val id = msg.id
                if (id != null) {
                    viewModel.deleteMessage(id)
                }
                selectedMessageForAction = null
            }
        )
    }
}

@Composable
private fun MessageItem(
    message: SiteMessage,
    onRead: () -> Unit,
    onClickAction: () -> Unit
) {
    val isUnread = message.isRead != true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isUnread) CHelperTheme.colors.mainColor.copy(alpha = 0.06f)
                else CHelperTheme.colors.backgroundComponent
            )
            .clickable {
                // 点击自动标记已读并展开操作
                if (isUnread) onRead()
                onClickAction()
            }
            .padding(14.dp, 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 未读小圆点
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CHelperTheme.colors.mainColor)
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = message.title ?: "无标题",
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    color = CHelperTheme.colors.textMain
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.width(8.dp))

            // 消息类型标签
            message.msgType?.let { type ->
                val label = when (type) {
                    "system" -> "系统"
                    "review" -> "审核"
                    "notice" -> "通知"
                    else -> type
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CHelperTheme.colors.mainColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = CHelperTheme.colors.mainColor,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = message.content ?: "",
            style = TextStyle(
                color = CHelperTheme.colors.textSecondary,
                fontSize = 13.sp
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.createdAt ?: "",
                style = TextStyle(
                    color = CHelperTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            )
            if (message.isGlobal == true) {
                Text(
                    text = "全站广播",
                    style = TextStyle(
                        color = CHelperTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
    }

@Composable
private fun MessageDetailDialog(
    message: SiteMessage,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    CustomDialog(
        onDismissRequest = {
            onRead() // dismissing also automatically marks as read ideally
            onDismiss()
        }
    ) {
        yancey.chelper.ui.common.dialog.DialogContainer(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
            // Header
            Text(
                text = message.title ?: "消息详情",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CHelperTheme.colors.textMain
                )
            )
            
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.createdAt ?: "",
                    style = TextStyle(
                        color = CHelperTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                )

                // 消息类型
                message.msgType?.let { type ->
                    val label = when (type) {
                        "system" -> "系统"
                        "review" -> "审核"
                        "notice" -> "通知"
                        else -> type
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CHelperTheme.colors.mainColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                color = CHelperTheme.colors.mainColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 滚动消息体
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = message.content ?: "无内容",
                    style = TextStyle(
                        color = CHelperTheme.colors.textMain,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "删除",
                    modifier = Modifier
                        .clickable { onDelete() }
                        .padding(8.dp),
                    style = TextStyle(color = CHelperTheme.colors.textErrorReason)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "关闭",
                    modifier = Modifier
                        .clickable {
                            onRead()
                            onDismiss()
                        }
                        .padding(8.dp),
                    style = TextStyle(color = CHelperTheme.colors.mainColor, fontWeight = FontWeight.Bold)
                )
            }
        }
        }
    }
}

