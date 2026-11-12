package yancey.chelper.ui.feature.library

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.ui.feature.library.browse.PublicLibraryListScreen
import yancey.chelper.ui.feature.library.components.LibraryTabs
import yancey.chelper.ui.feature.library.local.LocalLibraryListScreen

@Composable
fun LibraryMainScreen(navController: NavHostController, isFloatingWindow: Boolean = false) {
    var unread by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val response = ServiceManager.COMMAND_LAB_USER_SERVICE.getUnreadCount()
            if (response.status == 0) unread = response.data?.count ?: 0
        } catch (e: kotlinx.coroutines.CancellationException) {
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
