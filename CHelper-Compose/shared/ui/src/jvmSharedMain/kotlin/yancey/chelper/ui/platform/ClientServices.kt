package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yancey.chelper.data.local.history.CopyHistoryDataStore
import yancey.chelper.data.local.library.FavoriteLibraryDataStore
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.data.local.settings.SettingsDataStore
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.library.LibraryFunction

/** Shared repositories and system capabilities, assembled once by each platform host. */
class ClientServices(
    val settings: SettingsDataStore,
    val libraries: LocalCommandLabDataStore,
    val favorites: FavoriteLibraryDataStore,
    val history: CopyHistoryDataStore,
    val platform: ClientPlatform,
    val packFilenames: List<String>,
    val openCore: (String) -> yancey.chelper.core.CHelperCore,
    val commandDrafts: CommandDraftRepository,
    val session: yancey.chelper.ui.app.AppSession = yancey.chelper.ui.app.AppSession(),
    val rawtext: yancey.chelper.domain.rawtext.RawtextRepository,
    val assets: yancey.chelper.domain.assets.AssetRepository,
    val versionName: String,
    val versionCode: Int,
    val dataDirectory: java.io.File,
)

val LocalClientServices =
    staticCompositionLocalOf<ClientServices> { error("ClientServices must be provided by the app") }

interface ClientPlatform {
    val homeActions: Set<yancey.chelper.ui.feature.home.HomeAction>
        get() = emptySet()

    val floatingWindow: FloatingWindowController?
        get() = null

    val needsXiaomiClipboardTips: Boolean
        get() = false

    fun copyText(text: String)

    suspend fun clipboardText(): String?

    fun openUrl(url: String)

    fun shareText(text: String) {
        copyText(text)
    }

    suspend fun pickImage(): PickedImage?

    val supportsLoongFlow: Boolean
        get() = false

    fun loongFlowPermissionGranted(): Boolean = false

    fun requestLoongFlowPermission(result: (Boolean) -> Unit) {
        result(false)
    }

    fun showLoongFlow(library: LibraryFunction): Boolean = false

    fun showLoongFlowExport() {}

    suspend fun enqueueSync(localEntryId: String, ownerId: Int, revision: String)

    @Composable fun Captcha(action: String, onDismiss: () -> Unit, onSuccess: (String) -> Unit)

    @Composable
    fun CommandEditor(
        model: yancey.chelper.ui.feature.command.CompletionViewModel,
        modifier: androidx.compose.ui.Modifier,
        errorRange: yancey.chelper.ui.feature.command.CommandErrorSelection?,
    )
}

interface FloatingWindowController {
    val isUsingFloatingWindow: Boolean

    fun startFloatingWindow(
        iconSize: Int,
        iconAlpha: Float,
        screenAlpha: Float,
        fontAlphaSync: Boolean,
    )

    fun stopFloatingWindow()

    fun showExport()
}

data class PickedImage(val bytes: ByteArray, val name: String, val mimeType: String)

object ClientToast {
    private val mutable = MutableStateFlow<Pair<Long, String>?>(null)
    val messages = mutable.asStateFlow()
    private var version = 0L
    var native: ((String) -> Unit)? = null

    @Synchronized
    fun show(value: Any?) {
        val message = value?.toString().orEmpty()
        native?.invoke(message) ?: run { mutable.value = ++version to message }
    }

    fun clear(id: Long) {
        if (mutable.value?.first == id) mutable.value = null
    }
}

object ClientDiagnostics {
    var report: ((Throwable, String) -> Unit)? = null

    fun generateCustomLog(error: Throwable, category: String) {
        report?.invoke(error, category) ?: error.printStackTrace()
    }
}

@Composable
fun rememberImagePicker(onResult: (PickedImage) -> Unit): () -> Unit {
    val platform = LocalClientServices.current.platform
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onResult)
    return {
        scope.launch {
            try {
                platform.pickImage()?.let(current)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientToast.show("读取图片失败：${e.message}")
            }
        }
    }
}
