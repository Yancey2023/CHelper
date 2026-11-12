package yancey.chelper.ui.feature.library.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.resources.*
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.layout.RootView
import yancey.chelper.ui.designsystem.widget.Text

@Composable
fun LibraryTabs(
    isFloatingWindow: Boolean = false,
    unreadCount: Int = 0,
    initialLocal: Boolean = false,
    publicContent: @Composable () -> Unit,
    localContent: @Composable () -> Unit,
) {

    var selectedTab by rememberSaveable { mutableStateOf(if (initialLocal) 1 else 0) }
    // 未读消息数

    // 悬浮窗模式下tab只允许云端
    if (isFloatingWindow) {
        selectedTab = 0
    }

    RootView {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // 用 if/else 保留所有 tab 的 composition 状态，
                // 切换时不会重新触发 ViewModel 初始化
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedTab == 0) {
                        publicContent()
                    }
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedTab == 1) {
                        localContent()
                    }
                }
            }

            if (!isFloatingWindow) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(56.dp)
                            .background(CHelperTheme.colors.backgroundComponent),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomNavItem(
                        iconRes = Res.drawable.box,
                        title = "云端",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavItem(
                        iconRes = Res.drawable.folder,
                        title = "我的库",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        badgeCount = unreadCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val color = if (isSelected) CHelperTheme.colors.mainColor else CHelperTheme.colors.textSecondary
    Column(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(color),
            )
            // 未读消息红点
            if (badgeCount > 0) {
                Box(
                    modifier =
                        Modifier.size(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color.Red)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = title, style = TextStyle(color = color, fontSize = 11.sp))
    }
}
