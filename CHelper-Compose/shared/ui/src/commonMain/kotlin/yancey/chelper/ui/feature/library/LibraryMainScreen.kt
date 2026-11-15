package yancey.chelper.ui.feature.library

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import yancey.chelper.ui.feature.library.browse.PublicLibraryListScreen
import yancey.chelper.ui.feature.library.components.LibraryTabs
import yancey.chelper.ui.feature.library.local.LocalLibraryListScreen
import yancey.chelper.ui.platform.LocalClientServices

@Composable
fun LibraryMainScreen(navController: NavHostController, isFloatingWindow: Boolean = false) {
    val inbox = LocalClientServices.current.messages
    var unread by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val response = inbox.unreadCount()
            if (response.isSuccess()) unread = response.data?.count ?: 0
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }
    LibraryTabs(
        isFloatingWindow = isFloatingWindow,
        unreadCount = unread,
        publicContent = {
            PublicLibraryListScreen(
                navController = navController,
                isFloatingWindow = isFloatingWindow,
                isTab = true,
            )
        },
        localContent = { LocalLibraryListScreen(navController = navController, isTab = true) },
    )
}
