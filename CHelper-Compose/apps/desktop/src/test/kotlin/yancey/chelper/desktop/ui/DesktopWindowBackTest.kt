package yancey.chelper.desktop.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.designsystem.dialog.AppDialog
import yancey.chelper.ui.platform.ClientInteractionHost

/** Uses the real Compose window's keyboard integration, without a production key interceptor. */
class DesktopWindowBackTest {
    @Test
    fun officialWindowDispatchesEscapeBeforeAndAfterTextFieldFocus() {
        var window: ComposeWindow? = null
        lateinit var focusEditor: () -> Unit
        val ready = CountDownLatch(1)
        val dialogReady = CountDownLatch(1)
        val showDialog = mutableStateOf(false)
        val backs = AtomicInteger()
        try {
            SwingUtilities.invokeAndWait {
                window =
                    ComposeWindow().apply {
                        setSize(400, 300)
                        setLocation(-10000, -10000)
                        setAutoRequestFocus(false)
                        setContent {
                            val state = remember { TextFieldState("say hello") }
                            val focus = remember { FocusRequester() }
                            SideEffect { focusEditor = { focus.requestFocus() } }
                            LaunchedEffect(Unit) { ready.countDown() }
                            CHelperTheme(CHelperTheme.Theme.Light, null) {
                                ClientInteractionHost(onBack = { backs.incrementAndGet() }) {
                                    BasicTextField(state, Modifier.focusRequester(focus))
                                    if (showDialog.value) {
                                        AppDialog(onDismissRequest = { showDialog.value = false }) {
                                            BasicTextField(remember { TextFieldState("modal") })
                                            LaunchedEffect(Unit) { dialogReady.countDown() }
                                        }
                                    }
                                }
                            }
                        }
                        isVisible = true
                    }
            }
            assertTrue("Window composition must start", ready.await(10, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait {
                pressEscape(requireNotNull(window))
                assertEquals(1, backs.get())
                focusEditor()
                pressEscape(requireNotNull(window))
                assertEquals(2, backs.get())
                showDialog.value = true
            }
            assertTrue("In-window dialog must compose", dialogReady.await(10, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait {
                assertTrue(requireNotNull(window).ownedWindows.none { it.isVisible })
                pressEscape(requireNotNull(window))
                assertFalse(showDialog.value)
                assertEquals(2, backs.get())
            }
        } finally {
            SwingUtilities.invokeAndWait { window?.dispose() }
        }
    }

    private fun pressEscape(window: ComposeWindow) {
        val target = keyTarget(window) ?: error("No Compose keyboard listener")
        val event =
            KeyEvent(
                target,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED,
            )
        target.keyListeners.forEach { it.keyPressed(event) }
    }

    private fun keyTarget(component: Component): Component? =
        if (component.keyListeners.isNotEmpty()) component
        else (component as? Container)?.components?.firstNotNullOfOrNull(::keyTarget)
}
