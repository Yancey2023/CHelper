package yancey.chelper.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yancey.chelper.desktop.di.DesktopContainer
import yancey.chelper.domain.command.*
import yancey.chelper.domain.library.ChainItem
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.library.browse.*
import yancey.chelper.ui.feature.library.mcd.*
import yancey.chelper.ui.navigation.*
import yancey.chelper.ui.platform.LocalClientServices

@OptIn(ExperimentalAnimationApi::class)
class McdHighlightSchedulingTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test fun fastContentDoesNotStartNativeWorkDuringEntry() = checkScheduling(true)

    @Test fun lateContentPaintsAndFinishesItsFadeBeforeNativeWork() = checkScheduling(false)

    private fun checkScheduling(fast: Boolean) {
        val loads = AtomicInteger()
        val parses = AtomicInteger()
        val loader: (String) -> CommandEngine = {
            loads.incrementAndGet()
            object : CommandEngine {
                override suspend fun parse(text: String): ParsedCommand {
                    parses.incrementAndGet()
                    return object : ParsedCommand {
                        override val structure: String?
                            get() = error("coloring must not read structure")

                        override val errors: Array<ErrorReason>? = null
                        override val tokens = IntArray(text.length) { 7 }
                        override val nodeCount: Int
                            get() = error("coloring must not count nodes")

                        override fun hint(index: Int): String? =
                            error("coloring must not read hints")

                        override fun suggestionCount(index: Int): Int =
                            error("coloring must not build suggestions")

                        override fun suggestion(index: Int, which: Int): Suggestion? = null

                        override fun apply(index: Int, which: Int): ClickSuggestionResult? = null

                        override fun close() = Unit
                    }
                }

                override fun close() = Unit
            }
        }
        val ready = mutableStateOf(false)
        val preview =
            McdPreview(listOf(MCDRenderRow.Item(ChainItem.RawCommand("say hello"))), "test", true)
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        DesktopContainer(
                temporary.root,
                engineFactory = { MockEngine { respondError(HttpStatusCode.BadRequest) } },
            )
            .use { container ->
                lateinit var nav: NavHostController
                try {
                    compose.setContent {
                        CompositionLocalProvider(
                            LocalClientServices provides container.dependencies.services,
                            LocalViewModelStoreOwner provides owner,
                        ) {
                            CHelperTheme(CHelperTheme.Theme.Light, null) {
                                Box(Modifier.size(420.dp, 640.dp)) {
                                    nav = rememberNavController()
                                    ClientNavHost(nav, HomeScreenKey) {
                                        composable<HomeScreenKey> { Box(Modifier.fillMaxSize()) }
                                        composable<PublicLibraryShowScreenKey> {
                                            val phase = libraryDetailPhase(ready.value, false)
                                            val fade = updateTransition(phase)
                                            val highlight =
                                                rememberLibraryHighlightingStarted(
                                                    preview.takeIf { ready.value },
                                                    fade,
                                                )
                                            fade.Crossfade(
                                                Modifier.fillMaxSize(),
                                                animationSpec = tween(90),
                                            ) { state ->
                                                if (state == LibraryDetailPhase.Ready) {
                                                    PreparedMCDContentView(
                                                        preview,
                                                        Modifier.fillMaxSize(),
                                                        highlightingEnabled = highlight,
                                                        loadCommandEngine = loader,
                                                    )
                                                } else LibraryDetailSkeleton()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread { nav.navigate(PublicLibraryShowScreenKey(7)) }
                    compose.mainClock.advanceTimeBy(64)
                    if (!fast) compose.mainClock.advanceTimeBy(400)
                    compose.runOnUiThread { ready.value = true }
                    compose.mainClock.advanceTimeBy(if (fast) 128 else 32)
                    compose.onNodeWithText("say hello").assertIsDisplayed()
                    assertEquals(
                        "Content must paint without starting the native engine",
                        0,
                        loads.get(),
                    )
                    assertEquals(0, parses.get())
                    compose.mainClock.autoAdvance = true
                    compose.waitUntil(5000) { parses.get() > 0 }
                    compose.waitUntil(5000) { hasSyntaxSpans() }
                    assertEquals(1, loads.get())
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread { nav.popBackStack() }
                    compose.mainClock.advanceTimeBy(64)
                    assertTrue("Return previews must keep the highlighted text", hasSyntaxSpans())
                    assertEquals(1, loads.get())
                    compose.mainClock.autoAdvance = true
                } finally {
                    compose.runOnUiThread { owner.viewModelStore.clear() }
                }
            }
    }

    private fun hasSyntaxSpans(): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("say hello").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        return layouts.single().layoutInput.text.spanStyles.isNotEmpty()
    }
}
