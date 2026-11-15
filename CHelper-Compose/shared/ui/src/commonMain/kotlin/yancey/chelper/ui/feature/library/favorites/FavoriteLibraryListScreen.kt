/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 */

package yancey.chelper.ui.feature.library.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.MenuDialog
import yancey.chelper.ui.designsystem.layout.RootViewWithHeaderAndCopyright
import yancey.chelper.ui.designsystem.widget.Icon
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.LocalClientServices

@Composable
fun FavoriteLibraryListScreen(
    navController: NavHostController,
    accounts: AccountRepository = LocalClientServices.current.accounts,
    favorites: FavoriteCollectionRepository = LocalClientServices.current.cloudFavorites,
    viewModel: FavoriteLibraryListViewModel = viewModel {
        FavoriteLibraryListViewModel(accounts, favorites)
    },
) {
    val context = LocalClientServices.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var menuIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) { viewModel.load() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load(force = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(viewModel.actionMessage) {
        viewModel.actionMessage?.let {
            ClientMessages.show(it)
            viewModel.actionMessage = null
        }
        onDispose {}
    }

    RootViewWithHeaderAndCopyright(
        title = "我的收藏",
        showBack = true,
        onBack = { navController.popBackStack() },
        headerRight = {
            Icon(
                id = Res.drawable.refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(24.dp).clickable { viewModel.load(force = true) },
            )
        },
    ) {
        if (viewModel.isLoading && viewModel.favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载中...", style = TextStyle(color = CHelperTheme.colors.textSecondary))
            }
        } else if (viewModel.errorMessage != null && viewModel.favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = viewModel.errorMessage ?: "加载失败",
                        style = TextStyle(color = CHelperTheme.colors.textSecondary),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "点击重试",
                        modifier = Modifier.clickable { viewModel.load(force = true) },
                        style = TextStyle(color = CHelperTheme.colors.mainColor),
                    )
                }
            }
        } else if (viewModel.favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "还没有收藏",
                        style =
                            TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 14.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "在市场卡片或详情页点「收藏」即可添加",
                        style =
                            TextStyle(color = CHelperTheme.colors.textSecondary, fontSize = 12.sp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 10.dp)
            ) {
                itemsIndexed(viewModel.favorites, key = { index, lib -> lib.id ?: "fav-$index" }) {
                    index,
                    library ->
                    FavoriteItem(
                        library = library,
                        onClick = {
                            library.id?.let { id ->
                                navController.navigate(
                                    PublicLibraryShowScreenKey(id = id, isPrivate = false)
                                )
                            }
                        },
                        onMore = { menuIndex = index },
                    )
                    if (index < viewModel.favorites.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (menuIndex >= 0 && menuIndex < viewModel.favorites.size) {
        val target = viewModel.favorites[menuIndex]
        MenuDialog(
            onDismissRequest = { menuIndex = -1 },
            data = arrayOf("打开云端库" to "open", "取消收藏" to "remove", "关闭" to "close"),
            onChoose = { action ->
                when (action) {
                    "open" -> {
                        target.id?.let { id ->
                            navController.navigate(
                                PublicLibraryShowScreenKey(id = id, isPrivate = false)
                            )
                        }
                    }

                    "remove" -> viewModel.removeFavorite(target)
                }
                menuIndex = -1
            },
        )
    }
}

@Composable
private fun FavoriteItem(library: LibraryFunction, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CHelperTheme.colors.backgroundComponent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name ?: "未命名",
                style =
                    TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = CHelperTheme.colors.textMain,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val subtitle = buildString {
                library.author?.name?.let { append("@").append(it) }
                val note = library.note?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                if (note.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(note)
                }
                if (isEmpty()) {
                    library.preview?.take(40)?.let { append(it) }
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 12.sp, color = CHelperTheme.colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            id = Res.drawable.more,
            contentDescription = "更多",
            modifier = Modifier.size(22.dp).clickable(onClick = onMore),
        )
    }
}
