package yancey.chelper.desktop

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.desktop.di.DesktopContainer
import yancey.chelper.desktop.instance.DesktopInstance
import yancey.chelper.desktop.instance.desktopDataDirectory
import yancey.chelper.desktop.window.DesktopLoongFlowWindow
import yancey.chelper.resources.Res
import yancey.chelper.resources.pack_icon
import yancey.chelper.ui.app.CHelperApp
import yancey.chelper.ui.navigation.CommandLabDeepLink

fun main(args: Array<String>) {
    val directory = desktopDataDirectory()
    val instance = DesktopInstance.acquire(directory, args.toList()) ?: return
    try {
        runApplication(directory, instance)
    } finally {
        instance.close()
    }
}

private fun runApplication(directory: java.io.File, instance: DesktopInstance) =
    application(exitProcessOnExit = false) {
        val container = remember { DesktopContainer(directory) }
        val session = container.dependencies.services.session
        val background by container.appearance.background.collectAsState(initial = null)
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        var closing by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<String?>(null) }
        var openRequest by remember { mutableStateOf<Pair<Long, CommandLabDeepLink>?>(null) }
        val windowState = rememberWindowState(width = 1100.dp, height = 780.dp)
        val scope = rememberCoroutineScope()
        val startup =
            remember(container) {
                yancey.chelper.data.startup.StartupState(
                    settingsFlow = container.stores.settings.settings(),
                    loadBackground = {
                        try {
                            container.appearance.load()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure = e.message
                        }
                    },
                    scope = scope,
                    awaitCredentials = { container.initialize()?.let { failure = it } },
                )
            }
        val startupSettings by startup.settings.collectAsState()
        val ready = startupSettings != null
        fun close() {
            if (closing) return
            val flush = session.prepareClose()
            closing = true
            scope.launch {
                try {
                    flush()
                    owner.viewModelStore.clear()
                    withContext(Dispatchers.IO) { container.close() }
                    exitApplication()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = "保存失败：${e.message}"
                    closing = false
                }
            }
        }
        startupSettings?.let {
            DesktopLoongFlowWindow(container.loongFlow, container.dependencies.services, it)
        }
        Window(
            onCloseRequest = ::close,
            title = "CHelper",
            icon = painterResource(Res.drawable.pack_icon),
            state = windowState,
        ) {
            LaunchedEffect(instance) {
                instance.requests.collect { request ->
                    openRequest = request.target?.let { request.sequence to it }
                    windowState.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                when {
                    ready ->
                        CHelperApp(
                            container.dependencies,
                            background,
                            chooseBackground = {
                                scope.launch {
                                    try {
                                        container.appearance.choose()
                                    } catch (e: Exception) {
                                        failure = e.message
                                    }
                                }
                            },
                            restoreBackground = {
                                scope.launch {
                                    try {
                                        container.appearance.restore()
                                    } catch (e: Exception) {
                                        failure = e.message
                                    }
                                }
                            },
                            onClose = ::close,
                            openRequest = openRequest,
                            initialSettings = requireNotNull(startupSettings),
                        )
                    else -> BasicText("正在加载 CHelper…")
                }
                if (closing)
                    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                        BasicText("正在保存并退出…")
                    }
                failure?.let { BasicText(it) }
            }
        }
    }
