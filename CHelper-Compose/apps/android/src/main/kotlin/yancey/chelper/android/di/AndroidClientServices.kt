package yancey.chelper.android.di

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.hjq.device.compat.DeviceOs
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.*
import okio.Path.Companion.toOkioPath
import yancey.chelper.BuildConfig
import yancey.chelper.android.networkServices
import yancey.chelper.android.storage.clientDataStores
import yancey.chelper.android.ui.AndroidTextClipCodec
import yancey.chelper.android.ui.captcha.AndroidCaptchaDialog
import yancey.chelper.android.util.MonitorUtil
import yancey.chelper.android.window.FloatingWindowManager
import yancey.chelper.android.window.LoongFlowWindowManager
import yancey.chelper.android.work.LibrarySyncScheduler
import yancey.chelper.core.CHelperCore
import yancey.chelper.data.local.NoticeStore
import yancey.chelper.domain.assets.AssetRepository
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.nativecore.command.NativeCommandEngine
import yancey.chelper.nativecore.enumeration.JvmEnumerationRepository
import yancey.chelper.ui.feature.home.HomeAction
import yancey.chelper.ui.platform.ClientDiagnostics
import yancey.chelper.ui.platform.ClientPlatform
import yancey.chelper.ui.platform.ClientServices
import yancey.chelper.ui.platform.FloatingWindowController
import yancey.chelper.ui.platform.PickedImage

@Composable
fun rememberAndroidClientServices(
    floatingWindowManager: FloatingWindowManager? = null
): ClientServices {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<CompletableDeferred<PickedImage?>?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val request = pending
            pending = null
            if (uri == null) request?.complete(null)
            else
                scope.launch {
                    try {
                        val image =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use {
                                    PickedImage(
                                        it.readBytes(),
                                        "avatar.jpg",
                                        context.contentResolver.getType(uri) ?: "image/jpeg",
                                    )
                                }
                            }
                        request?.complete(image)
                    } catch (e: Exception) {
                        request?.completeExceptionally(e)
                    }
                }
        }
    DisposableEffect(Unit) { onDispose { pending?.cancel() } }
    SideEffect { ClientDiagnostics.report = MonitorUtil::generateCustomLog }
    return remember(context, floatingWindowManager) {
        val stores = context.clientDataStores()
        ClientServices(
            context.networkServices.accounts,
            context.networkServices.libraryContent,
            context.networkServices.activity,
            context.networkServices.messages,
            context.networkServices.browserLinks,
            context.networkServices.appContent,
            context.networkServices.publicLibraries,
            context.networkServices.search,
            context.networkServices.favorites(stores.favorites),
            stores.settings,
            stores.libraries,
            stores.favorites,
            stores.history,
            object : ClientPlatform {
                override val homeActions =
                    setOf(
                        HomeAction.FloatingWindow,
                        HomeAction.Old2NewIMEGuide,
                        HomeAction.LoongFlow,
                    )
                override val needsXiaomiClipboardTips
                    get() = DeviceOs.isHyperOs() || DeviceOs.isMiui()

                override val floatingWindow = floatingWindowManager?.let { manager ->
                    object : FloatingWindowController {
                        override val isUsingFloatingWindow
                            get() = manager.isUsingFloatingWindow

                        override fun startFloatingWindow(
                            iconSize: Int,
                            iconAlpha: Float,
                            screenAlpha: Float,
                            fontAlphaSync: Boolean,
                        ) {
                            manager.startFloatingWindow(
                                context,
                                iconSize,
                                iconAlpha,
                                screenAlpha,
                                fontAlphaSync,
                            )
                        }

                        override fun stopFloatingWindow() {
                            manager.stopFloatingWindow()
                        }

                        override fun showExport() {
                            manager.loongFlowManager.showExport(context)
                        }
                    }
                }

                private fun copyText(text: String) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(null, text))
                }

                override fun shareText(text: String) {
                    copyText(text)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            "分享命令库",
                        )
                    )
                }

                override suspend fun pickImage(): PickedImage? {
                    check(pending == null)
                    val request = CompletableDeferred<PickedImage?>()
                    pending = request
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    return request.await()
                }

                override fun showLoongFlowExport() {
                    LoongFlowWindowManager.INSTANCE.showExport(context)
                }

                override val supportsLoongFlow = true

                override fun loongFlowPermissionGranted() =
                    XXPermissions.isGrantedPermission(
                        context,
                        PermissionLists.getSystemAlertWindowPermission(),
                    )

                override fun requestLoongFlowPermission(result: (Boolean) -> Unit) {
                    XXPermissions.with(context)
                        .permission(PermissionLists.getSystemAlertWindowPermission())
                        .request { _, denied -> result(denied.isEmpty()) }
                }

                override fun showLoongFlow(library: LibraryFunction) =
                    LoongFlowWindowManager.INSTANCE.showImport(context, library)

                override suspend fun enqueueSync(
                    localEntryId: String,
                    ownerId: Int,
                    revision: String,
                ) {
                    LibrarySyncScheduler.enqueue(
                        context.applicationContext,
                        ownerId,
                        localEntryId,
                        revision,
                    )
                }

                @Composable
                override fun Captcha(
                    action: String,
                    onDismiss: () -> Unit,
                    onSuccess: (String) -> Unit,
                ) {
                    AndroidCaptchaDialog(action, onDismiss, onSuccess)
                }
            },
            { branch ->
                NativeCommandEngine.load(
                    context.assets.list("cpack").orEmpty().toList(),
                    { path -> CHelperCore.fromAssets(context.assets, path) },
                    branch,
                )
            },
            JvmEnumerationRepository(),
            stores.commandDrafts,
            rawtext = stores.rawtext,
            assets =
                AssetRepository { path ->
                    withContext(Dispatchers.IO) {
                        context.assets.open(path).bufferedReader().use { it.readText() }
                    }
                },
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            clipboardCodec = AndroidTextClipCodec(context),
            notices = NoticeStore(context.dataDir.toOkioPath()),
            packFilenames = context.assets.list("cpack").orEmpty().toList(),
        )
    }
}
