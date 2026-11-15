package yancey.chelper.ui.platform

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import yancey.chelper.data.local.NoticeStore
import yancey.chelper.data.local.history.CopyHistoryDataStore
import yancey.chelper.data.local.library.FavoriteLibraryDataStore
import yancey.chelper.data.local.library.LocalCommandLabDataStore
import yancey.chelper.data.local.settings.SettingsDataStore
import yancey.chelper.data.remote.NetworkServices
import yancey.chelper.domain.assets.AssetRepository
import yancey.chelper.domain.command.CommandDraftRepository
import yancey.chelper.domain.command.CommandEngine
import yancey.chelper.domain.enumeration.EnumerationRepository
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.rawtext.RawtextRepository
import yancey.chelper.ui.app.AppSession
import yancey.chelper.ui.feature.home.HomeAction

/** Shared repositories and system capabilities, assembled once by each platform host. */
class ClientServices(
    val network: NetworkServices,
    val settings: SettingsDataStore,
    val libraries: LocalCommandLabDataStore,
    val favorites: FavoriteLibraryDataStore,
    val history: CopyHistoryDataStore,
    val platform: ClientPlatform,
    val loadCommandEngine: (String) -> CommandEngine,
    val enumeration: EnumerationRepository,
    val commandDrafts: CommandDraftRepository,
    val session: AppSession = AppSession(),
    val rawtext: RawtextRepository,
    val assets: AssetRepository,
    val versionName: String,
    val versionCode: Int,
    val notices: NoticeStore,
    val packFilenames: List<String>,
    val clipboardCodec: TextClipCodec,
)

val LocalClientServices =
    staticCompositionLocalOf<ClientServices> { error("ClientServices must be provided by the app") }

interface ClientPlatform {
    val homeActions: Set<HomeAction>
        get() = emptySet()

    val floatingWindow: FloatingWindowController?
        get() = null

    val needsXiaomiClipboardTips: Boolean
        get() = false

    fun shareText(text: String)

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClientMessages.show("读取图片失败：${e.message}")
            }
        }
    }
}
