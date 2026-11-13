package yancey.chelper.ui.platform

import androidx.compose.ui.platform.UriHandler
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ClientFeedbackTest {
    @Test
    fun platformFeedbackDoesNotNeedAHostOrAlsoAppearInTheComposeHost() = runTest {
        val presented = mutableListOf<String>()
        val composeMessages = mutableListOf<String>()
        ClientMessages.setPlatformPresenter { presented.add(it) }
        try {
            // No Compose host or flow subscriber exists when this callback runs.
            withContext(Dispatchers.Default) { ClientMessages.show("悬浮窗已关闭") }
            assertEquals(listOf("悬浮窗已关闭"), presented)
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    ClientMessages.messages.toList(composeMessages)
                }
            ClientMessages.show("已复制")
            ClientMessages.show("已复制")
            ClientMessages.show("  ")
            runCurrent()
            assertEquals(listOf("悬浮窗已关闭", "已复制", "已复制"), presented)
            assertTrue(composeMessages.isEmpty())

            ClientMessages.setPlatformPresenter(null)
            ClientMessages.show("桌面窗口反馈")
            runCurrent()
            assertEquals(listOf("桌面窗口反馈"), composeMessages)
            collector.cancel()
        } finally {
            ClientMessages.setPlatformPresenter(null)
        }
    }

    @Test
    fun messagesAreTransientAndRepeatedMessagesAreDelivered() = runTest {
        ClientMessages.show("旧窗口的消息")
        val received = mutableListOf<String>()
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ClientMessages.messages.take(2).toList(received)
            }
        ClientMessages.show(null)
        ClientMessages.show("  ")
        ClientMessages.show("已复制")
        ClientMessages.show("已复制")
        runCurrent()
        assertEquals(listOf("已复制", "已复制"), received)
        collector.join()
    }

    @Test
    fun webLinksUseTheComposeHandlerAndRejectOtherSchemes() {
        val opened = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val handler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
        openWebUrl(handler, "https://example.com/帮助?q=1#section", failures::add)
        openWebUrl(handler, "HTTP://example.com", failures::add)
        openWebUrl(handler, "file:///session.enc", failures::add)
        openWebUrl(handler, "javascript:alert(1)", failures::add)
        assertEquals(listOf("https://example.com/帮助?q=1#section", "HTTP://example.com"), opened)
        assertEquals(listOf("无法打开链接", "无法打开链接"), failures)
    }

    @Test
    fun missingBrowserIsReportedThroughSharedFeedback() {
        val failures = mutableListOf<String>()
        openWebUrl(
            object : UriHandler {
                override fun openUri(uri: String) {
                    error("No browser")
                }
            },
            "https://example.com",
            failures::add,
        )
        assertEquals(listOf("无法打开链接"), failures)
    }
}
