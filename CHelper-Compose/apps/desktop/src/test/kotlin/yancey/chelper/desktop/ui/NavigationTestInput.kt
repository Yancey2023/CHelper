package yancey.chelper.desktop.ui

import androidx.compose.runtime.*
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

internal class NavigationTestInput {
    private lateinit var input: DirectNavigationEventInput

    fun back() = input.backCompleted()

    @Composable
    fun Content(content: @Composable () -> Unit) {
        val owner = rememberNavigationEventDispatcherOwner(parent = null)
        val source = remember(owner) { DirectNavigationEventInput() }
        SideEffect { input = source }
        DisposableEffect(owner, source) {
            owner.navigationEventDispatcher.addInput(source)
            onDispose { owner.navigationEventDispatcher.removeInput(source) }
        }
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides owner,
            content = content,
        )
    }
}
