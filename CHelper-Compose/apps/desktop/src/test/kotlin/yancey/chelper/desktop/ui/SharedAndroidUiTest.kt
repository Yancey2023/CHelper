package yancey.chelper.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jetbrains.compose.resources.painterResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import yancey.chelper.data.policy.PolicyGrantManager
import yancey.chelper.data.remote.ServiceManager
import yancey.chelper.desktop.di.DesktopContainer
import yancey.chelper.resources.Res
import yancey.chelper.resources.allDrawableResources
import yancey.chelper.ui.app.CHelperApp
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.navigation.CommandLabDeepLink
import yancey.chelper.ui.navigation.CommandLabDeepLinkParser

/** Exercises the same content composables used by Android, including their real resource bundle. */
class SharedAndroidUiTest {
    @get:Rule val compose = createComposeRule()
    private val navigation = NavigationTestInput()

    @Test
    fun androidHomeRawtextAndSettingsUseSharedPagesAndBackNavigation() {
        val directory = Files.createTempDirectory("CHelper-shared-ui-").toFile()
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val container = DesktopContainer(directory)
        val session = container.dependencies.services.session
        val externalOpen = mutableStateOf<Pair<Long, CommandLabDeepLink>?>(null)
        runBlocking { container.initialize() }
        PolicyGrantManager.INSTANCE.agree()
        ServiceManager.init(File(directory, "test-cache"), false) { builder ->
            builder
                .interceptors()
                .add(
                    0,
                    Interceptor { chain ->
                        val path = chain.request().url.encodedPath
                        val json =
                            when {
                                path.endsWith("/library/detail/42") ->
                                    """{"status":0,"data":{"id":42,"name":"外链命令库","content":"say linked"}}"""
                                path.endsWith("announcement.json") ->
                                    """{"isEnable":false,"isForce":false}"""
                                path.endsWith("apk-latest.json") -> """{"version_name":"26.2.0"}"""
                                path.contains("/guest/") ->
                                    """{"status":1,"message":"offline test"}"""
                                else ->
                                    """{"status":0,"data":{"functions":[],"count":0,"total_count":0,"per_page":20}}"""
                            }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(json.toResponseBody("application/json".toMediaType()))
                            .build()
                    },
                )
        }
        try {
            compose.setContent {
                navigation.Content {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        Box(Modifier.requiredSize(420.dp, 740.dp).testTag("shared-app")) {
                            CHelperApp(
                                container.dependencies,
                                chooseBackground = {},
                                restoreBackground = {},
                                onClose = {},
                                openRequest = externalOpen.value,
                            )
                        }
                    }
                }
            }
            compose.onNodeWithText("CHelper").assertExists()
            compose.onNodeWithText("我的世界基岩版命令助手").assertExists()
            compose.onNodeWithText("命令补全").assertExists()
            compose.onNodeWithText("Compose 工作台（Android / Windows）").assertDoesNotExist()
            screenshot("home-android-ui")

            compose.onNodeWithText("原始 Json 文本生成器").performScrollTo().clickOnMain()
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("不用了").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("不用了").clickOnMain()
            compose.onAllNodesWithText("颜色", substring = true)[0].assertExists()
            screenshot("rawtext-android-ui")
            compose.onNodeWithContentDescription("返回").clickOnMain()
            compose.onNodeWithText("CHelper").assertExists()

            compose.onNodeWithText("设置").performScrollTo().clickOnMain()
            compose.onNodeWithText("选择主题").assertExists()
            compose.onNodeWithText("悬浮窗图标大小").assertDoesNotExist()
            compose.onNodeWithText("是否启用小图标模式").assertExists()
            compose.onNodeWithText("当前命令分支：正式版-实验性玩法", substring = true).assertExists()
            screenshot("settings-android-ui")
            compose.onNodeWithText("选择主题").clickOnMain()
            compose.onNodeWithText("深色模式").clickOnMain()
            compose.onNodeWithText("确认").clickOnMain()
            compose.waitForIdle()
            runBlocking { withContext(Dispatchers.Swing) { session.flush() } }
            val stored = runBlocking { container.dependencies.services.settings.state.first() }
            assertTrue(stored.themeId == "MODE_NIGHT_YES")
            compose.onNodeWithContentDescription("返回").clickOnMain()
            compose.onAllNodesWithText("应用模式")[0].performScrollTo().clickOnMain()
            compose.onNode(hasSetTextAction()).performTextInput("say hello")
            compose.runOnUiThread { navigation.back() }
            compose.onNodeWithText("CHelper").assertExists()
            compose.onAllNodesWithText("应用模式")[0].performScrollTo().clickOnMain()
            compose.onNode(hasSetTextAction()).assertTextEquals("say hello")
            screenshot("completion-android-ui")
            compose.runOnUiThread { navigation.back() }
            compose.waitForIdle()
            screenshot("after-completion-back")
            compose.onNodeWithText("命令库").performScrollTo().clickOnMain()
            compose.onNodeWithText("我的库").clickOnMain()
            compose.onNodeWithText("登录后可管理云端命令库").clickOnMain()
            compose.onNodeWithText("登录", useUnmergedTree = true).assertExists()
            screenshot("account-original-android-ui")
            compose.onNodeWithContentDescription("返回").clickOnMain()
            compose.onNodeWithContentDescription("添加").clickOnMain()
            compose.onNodeWithText("版本信息").assertExists()
            compose.onNodeWithText("命令区").assertExists()
            screenshot("library-editor-original-android-ui")
            compose.runOnUiThread {
                externalOpen.value =
                    1L to
                        requireNotNull(
                            CommandLabDeepLinkParser.parse("chelper://commandlab/library/42")
                        )
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("外链命令库").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("外链命令库").assertExists()
            screenshot("external-link-shared-library-page")
            compose.onNodeWithContentDescription("返回").clickOnMain()
            compose.onNodeWithText("CHelper").assertExists()
        } catch (error: Throwable) {
            error.printStackTrace()
            throw error
        } finally {
            runBlocking {
                withContext(Dispatchers.Swing) {
                    session.flush()
                    owner.viewModelStore.clear()
                }
            }
            container.close()
        }
    }

    @Test
    fun allSharedAndroidVectorIconsCanBeParsedByTheDesktopRenderer() {
        compose.setContent {
            CHelperTheme(CHelperTheme.Theme.Light, null) {
                FlowRow(Modifier.testTag("shared-app")) {
                    Res.allDrawableResources.forEach { (name, resource) ->
                        Image(painterResource(resource), name, Modifier.requiredSize(32.dp, 32.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        screenshot("all-shared-icons")
    }

    private fun SemanticsNodeInteraction.clickOnMain(): SemanticsNodeInteraction {
        performSemanticsAction(SemanticsActions.OnClick) { action ->
            compose.runOnUiThread { action() }
        }
        return this
    }

    private fun screenshot(name: String) {
        val directory = File(System.getProperty("chelper.screenshotsDir"))
        directory.mkdirs()
        val file = File(directory, "$name.png")
        ImageIO.write(
            compose.onNodeWithTag("shared-app").captureToImage().toAwtImage(),
            "png",
            file,
        )
        assertTrue(file.length() > 1000)
    }
}
