package yancey.chelper.desktop.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.jetbrains.compose.resources.painterResource
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.settings.Settings
import yancey.chelper.resources.Res
import yancey.chelper.resources.ic_loong_flow_bubble
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.loongflow.LoongFlowMiniPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowMode
import yancey.chelper.ui.feature.loongflow.LoongFlowPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowViewModel
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.ClientMessages
import yancey.chelper.ui.platform.ClientServices
import yancey.chelper.ui.platform.LocalClientServices

/** A native window session; command selection, editing and serialization use the shared model. */
internal class DesktopLoongFlow(
    private val copyText: (String) -> Unit,
    private val readClipboard: suspend () -> String?,
    private val shareText: (String) -> Unit,
) {
    var model by mutableStateOf<LoongFlowViewModel?>(null)
        private set

    var library by mutableStateOf<LibraryFunction?>(null)
        private set

    var minimized by mutableStateOf(false)
    var closeRequest by mutableLongStateOf(0L)
        private set

    private var exitAfterDismiss: (() -> Unit)? = null

    fun openImport(library: LibraryFunction) {
        open(
            LoongFlowViewModel(copyText, readClipboard, shareText).also { it.initImport(library) },
            library,
        )
    }

    fun openExport() {
        open(LoongFlowViewModel(copyText, readClipboard, shareText).also { it.initExport() }, null)
    }

    private fun open(value: LoongFlowViewModel, source: LibraryFunction?) {
        cancelClose()
        model = value
        library = source
        minimized = false
        closeRequest = 0
    }

    fun requestClose() {
        minimized = false
        closeRequest++
    }

    fun requestApplicationExit(onConfirmed: () -> Unit): Boolean {
        if (model?.mode != LoongFlowMode.EXPORT) return false
        exitAfterDismiss = onConfirmed
        requestClose()
        return true
    }

    fun cancelClose() {
        exitAfterDismiss = null
    }

    fun dismiss() {
        val exit = exitAfterDismiss
        exitAfterDismiss = null
        model = null
        library = null
        minimized = false
        closeRequest = 0
        exit?.invoke()
    }
}

/** Only window position, focus and topmost behavior belong to the Windows host. */
@Composable
internal fun ApplicationScope.DesktopLoongFlowWindow(
    host: DesktopLoongFlow,
    services: ClientServices,
    settings: Settings,
) {
    val model = host.model ?: return
    key(model) {
        val mini =
            host.minimized &&
                model.mode == LoongFlowMode.IMPORT &&
                model.importStep == 1 &&
                model.importMiniIconEnabled &&
                settings.isEnableLoongFlowImportMiniIcon != false
        val state = rememberWindowState(width = 400.dp, height = 600.dp)
        var expanded by remember { mutableStateOf(false) }
        LaunchedEffect(host.minimized, mini, expanded) {
            state.size =
                when {
                    mini -> DpSize(318.dp, 88.dp)
                    host.minimized -> DpSize(48.dp, 48.dp)
                    expanded -> DpSize(560.dp, 720.dp)
                    else -> DpSize(400.dp, 600.dp)
                }
        }
        Window(
            onCloseRequest = host::requestClose,
            title = if (model.mode == LoongFlowMode.IMPORT) "游龙导入" else "游龙导出",
            state = state,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = true,
            focusable = !host.minimized,
        ) {
            LaunchedEffect(host.closeRequest) {
                if (host.closeRequest > 0) {
                    window.toFront()
                    window.requestFocus()
                }
            }
            val move: (Float, Float) -> Unit = { x, y ->
                window.setLocation(window.x + x.toInt(), window.y + y.toInt())
            }
            CHelperTheme(
                CHelperTheme.themeOf(
                    settings.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM",
                    isSystemInDarkTheme(),
                ),
                backgroundBitmap = null,
            ) {
                CompositionLocalProvider(LocalClientServices provides services) {
                    ClientInteractionHost(
                        onBack = host::requestClose,
                        showMessages = !host.minimized,
                    ) {
                        when {
                            mini ->
                                LoongFlowMiniPanel(
                                    model,
                                    { host.minimized = false },
                                    host::dismiss,
                                    ClientMessages::show,
                                    move,
                                )
                            host.minimized ->
                                Image(
                                    painterResource(Res.drawable.ic_loong_flow_bubble),
                                    "展开游龙",
                                    Modifier.fillMaxSize()
                                        .pointerInput(model) {
                                            detectDragGestures { change, delta ->
                                                change.consume()
                                                move(delta.x, delta.y)
                                            }
                                        }
                                        .clickable { host.minimized = false },
                                )
                            else ->
                                LoongFlowPanel(
                                    mode = model.mode,
                                    library = host.library,
                                    viewModel = model,
                                    isEnableImportMiniIcon =
                                        settings.isEnableLoongFlowImportMiniIcon != false,
                                    onMinimize = { host.minimized = true },
                                    onDismiss = host::dismiss,
                                    onToggleSize = { expanded = !expanded },
                                    onRequestInputFocus = { window.requestFocus() },
                                    onMove = move,
                                    closeRequest = host.closeRequest,
                                    onCloseCancelled = host::cancelClose,
                                )
                        }
                    }
                }
            }
        }
    }
}
