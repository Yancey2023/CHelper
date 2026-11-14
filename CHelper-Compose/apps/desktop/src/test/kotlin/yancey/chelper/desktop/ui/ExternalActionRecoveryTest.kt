@file:OptIn(ExperimentalComposeUiApi::class)

package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import yancey.chelper.data.policy.PolicyGrantManager
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.desktop.di.DesktopContainer
import yancey.chelper.domain.rawtext.*
import yancey.chelper.ui.app.CHelperApp
import yancey.chelper.ui.app.PersistedEditor
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.widget.Text
import yancey.chelper.ui.feature.loongflow.LoongFlowMode
import yancey.chelper.ui.feature.loongflow.LoongFlowPanel
import yancey.chelper.ui.feature.loongflow.LoongFlowViewModel
import yancey.chelper.ui.feature.rawtext.RawtextScreen
import yancey.chelper.ui.feature.rawtext.RawtextViewModel
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.LocalClientServices

class ExternalActionRecoveryTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var container: DesktopContainer
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    @Before
    fun prepare() {
        container = DesktopContainer(temporary.root)
        PolicyGrantManager.init("test policy", File(temporary.root, "policy.txt"))
        ServiceManager.init(File(temporary.root, "cache"), false) { builder ->
            builder
                .interceptors()
                .add(
                    0,
                    Interceptor { chain ->
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """{"status":1,"message":"offline test"}"""
                                    .toResponseBody("application/json".toMediaType())
                            )
                            .build()
                    },
                )
        }
    }

    @After
    fun close() {
        compose.runOnUiThread { owner.viewModelStore.clear() }
        container.close()
    }

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalClientServices provides container.dependencies.services,
            ) {
                Box(Modifier.requiredSize(420.dp, 740.dp)) { body() }
            }
        }
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { action ->
            compose.runOnUiThread { action() }
        }
    }

    @Test
    fun busyClipboardShowsFeedbackAndLaterCopyStillWorks() {
        var busy = true
        var writes = 0
        val clipboard =
            object : Clipboard {
                override suspend fun getClipEntry(): ClipEntry? = null

                override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                    check(!busy) { "clipboard busy" }
                    writes++
                }
            }
        val repository =
            object : RawtextRepository {
                override val edits = RawtextEditTracker()

                override fun preferences() = flowOf(RawtextPreferences(autoSavePrompted = true))

                override suspend fun savePreferences(
                    preferences: RawtextPreferences,
                    token: RawtextEditToken,
                ) = Unit
            }
        content {
            CompositionLocalProvider(LocalClipboard provides clipboard) {
                CHelperTheme(CHelperTheme.Theme.Light, null) {
                    ClientInteractionHost {
                        val model = remember {
                            RawtextViewModel(repository).also {
                                owner.viewModelStore.put("rawtext", it)
                            }
                        }
                        RawtextScreen(model)
                    }
                }
            }
        }
        compose.onNodeWithText("复制 JSON").performScrollTo()
        click("复制 JSON")
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("剪贴板访问失败，请稍后重试").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { busy = false }
        click("复制 JSON")
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("已复制格式化 JSON").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test
    fun replacingAnEditorWaitsForItsPreviousPendingWrite() {
        val session = container.dependencies.services.session
        val repository = container.dependencies.services.settings
        var fail = true
        var saved = false
        session.register(repository) {
            if (fail) throw IOException("disk unavailable")
            saved = true
        }
        content {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ClientInteractionHost {
                    PersistedEditor(session, repository) {
                        SideEffect {
                            check(saved) { "New editor replaced an unsaved controller" }
                            session.register(repository) {}
                        }
                        Text("编辑器已恢复")
                    }
                }
            }
        }
        compose.onNodeWithText("暂时无法恢复编辑").assertIsDisplayed()
        compose.onNodeWithText("编辑器已恢复").assertDoesNotExist()
        compose.runOnIdle { fail = false }
        click("重试")
        compose.onNodeWithText("编辑器已恢复").assertIsDisplayed()
        compose.runOnIdle { assertTrue(saved) }
    }

    @Test
    fun cancellingAFailedDeepLinkDoesNotRetryAfterACancelledWindowClose() {
        var attempts = 0
        val closing = mutableStateOf(false)
        container.dependencies.services.session.register("save") {
            attempts++
            throw IOException("disk unavailable")
        }
        content {
            CHelperApp(
                container.dependencies,
                chooseBackground = {},
                restoreBackground = {},
                onClose = {},
                openRequest = 1L to CommandLabDeepLink.LibraryHome,
                closing = closing.value,
            )
        }
        compose.onNodeWithText("暂时无法打开链接").assertIsDisplayed()
        click("取消")
        compose.runOnIdle { closing.value = true }
        compose.waitForIdle()
        compose.runOnIdle { closing.value = false }
        compose.waitForIdle()
        compose.onNodeWithText("暂时无法打开链接").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, attempts) }
    }

    @Test
    fun exportCloseConfirmationDistinguishesCancellationFromConfirmation() {
        val model = LoongFlowViewModel({}, { null }, {}).apply { initExport() }
        val request = mutableLongStateOf(1L)
        var cancelled = 0
        var dismissed = 0
        content {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                ClientInteractionHost {
                    LoongFlowPanel(
                        mode = LoongFlowMode.EXPORT,
                        library = null,
                        viewModel = model,
                        onMinimize = {},
                        onDismiss = { dismissed++ },
                        closeRequest = request.longValue,
                        onCloseCancelled = { cancelled++ },
                    )
                }
            }
        }
        compose.onNodeWithText("关闭游龙导出？").assertIsDisplayed()
        click("取消")
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, dismissed)
            request.longValue++
        }
        compose.onNodeWithText("关闭游龙导出？").assertIsDisplayed()
        click("关闭")
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun failedDeepLinkSaveKeepsThePageAndCanBeRetried() {
        var fail = true
        var attempts = 0
        container.dependencies.services.session.register("save") {
            attempts++
            if (fail) throw IOException("disk unavailable")
        }
        content {
            CHelperApp(
                container.dependencies,
                chooseBackground = {},
                restoreBackground = {},
                onClose = {},
                openRequest = 1L to CommandLabDeepLink.LibraryHome,
            )
        }
        compose.onNodeWithText("暂时无法打开链接").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, attempts)
            fail = false
        }
        click("重试")
        compose.onNodeWithText("暂时无法打开链接").assertDoesNotExist()
        compose.onNodeWithText("云端").assertExists()
        compose.runOnIdle { assertEquals(2, attempts) }
    }
}
