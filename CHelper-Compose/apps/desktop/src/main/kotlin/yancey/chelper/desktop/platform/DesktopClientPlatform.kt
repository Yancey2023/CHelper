package yancey.chelper.desktop.platform

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.data.remote.library.model.CaptchaStatusResponse
import yancey.chelper.data.remote.library.model.CaptchaTokenRequest
import yancey.chelper.desktop.window.DesktopLoongFlow
import yancey.chelper.ui.designsystem.dialog.CustomDialog
import yancey.chelper.ui.designsystem.dialog.DialogContainer
import yancey.chelper.ui.designsystem.widget.Button
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.command.CompletionViewModel
import yancey.chelper.ui.platform.ClientPlatform
import yancey.chelper.ui.platform.PickedImage

/** Native desktop integrations; feature screens and their ViewModels remain shared. */
internal class DesktopClientPlatform(private val scheduleSync: () -> Unit) : ClientPlatform {
    internal val loongFlow = DesktopLoongFlow(::copyText, ::clipboardText, ::shareText)

    override val homeActions = setOf(yancey.chelper.ui.feature.home.HomeAction.LoongFlow)
    override val supportsLoongFlow = true

    override fun loongFlowPermissionGranted() = true

    override fun requestLoongFlowPermission(result: (Boolean) -> Unit) = result(true)

    override fun showLoongFlow(library: yancey.chelper.domain.library.LibraryFunction): Boolean {
        loongFlow.openImport(library)
        return true
    }

    override fun showLoongFlowExport() = loongFlow.openExport()

    override fun copyText(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override suspend fun clipboardText(): String? =
        withContext(Dispatchers.Swing) {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .getContents(null)
                ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
                ?.getTransferData(DataFlavor.stringFlavor) as? String
        }

    override fun openUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme in setOf("http", "https"))
        Desktop.getDesktop().browse(uri)
    }

    override suspend fun pickImage(): PickedImage? {
        val file =
            withContext(Dispatchers.Swing) {
                JFileChooser().run {
                    fileFilter = FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "webp")
                    if (showOpenDialog(null) == JFileChooser.APPROVE_OPTION) selectedFile else null
                }
            } ?: return null
        return withContext(Dispatchers.IO) {
            PickedImage(
                file.readBytes(),
                file.name,
                java.nio.file.Files.probeContentType(file.toPath()) ?: "image/jpeg",
            )
        }
    }

    override suspend fun enqueueSync(localEntryId: String, ownerId: Int, revision: String) {
        scheduleSync()
    }

    @Composable
    override fun CommandEditor(
        model: CompletionViewModel,
        modifier: Modifier,
        errorRange: yancey.chelper.ui.feature.command.CommandErrorSelection?,
    ) {
        yancey.chelper.ui.feature.command.ComposeCommandEditor(
            command = model.command,
            editorMode = model.isCommandEditorMode,
            tokens = model.syntaxHighlightTokens,
            errors =
                model.errorReasons.orEmpty().map {
                    yancey.chelper.domain.command.CommandProblem(
                        it.errorReason.orEmpty(),
                        it.start,
                        it.end,
                    )
                },
            modifier = modifier,
            errorSelection = errorRange,
        )
    }

    /** Same verification token/status protocol as Android; the OS browser renders the challenge. */
    @Composable
    override fun Captcha(action: String, onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
        var url by remember(action) { mutableStateOf<String?>(null) }
        var failure by remember(action) { mutableStateOf<String?>(null) }
        val success by rememberUpdatedState(onSuccess)
        val dismiss by rememberUpdatedState(onDismiss)
        LaunchedEffect(action) {
            try {
                val code = java.util.UUID.randomUUID().toString()
                val response =
                    ServiceManager.CAPTCHA_SERVICE.requestToken(
                        CaptchaTokenRequest().apply {
                            specialCode = code
                            this.action = action
                        }
                    )
                val token = response.data?.verificationToken
                check(response.isSuccess() && token != null) { response.message ?: "获取验证凭证失败" }
                url =
                    "${ServiceManager.LAB_BASE_URL.removeSuffix("/")}/captcha/verifing?token=$token&callback=androidCallback"
                openUrl(requireNotNull(url))
                withTimeout(300_000) {
                    while (isActive) {
                        delay(1500)
                        val result = ServiceManager.CAPTCHA_SERVICE.getStatus(code)
                        if (
                            result.isSuccess() &&
                                result.data?.status == CaptchaStatusResponse.STATUS_VERIFIED
                        ) {
                            success(result.data?.specialCode ?: code)
                            dismiss()
                            return@withTimeout
                        }
                        check(result.data?.status != CaptchaStatusResponse.STATUS_FAILED) { "验证失败" }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                failure = "验证超时"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: "验证失败"
            }
        }
        CustomDialog(onDismiss) {
            DialogContainer {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(failure ?: "请在系统浏览器中完成验证，完成后会自动继续。")
                    url?.let { link -> Button("打开验证页面") { openUrl(link) } }
                    Button("取消") { onDismiss() }
                }
            }
        }
    }
}
