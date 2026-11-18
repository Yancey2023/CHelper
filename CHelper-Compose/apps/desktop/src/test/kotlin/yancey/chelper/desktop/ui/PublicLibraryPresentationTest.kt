package yancey.chelper.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import yancey.chelper.desktop.di.DesktopContainer
import yancey.chelper.domain.library.LibraryContentRepository
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.domain.library.RepositoryResult
import yancey.chelper.domain.settings.AppSettingsRepository
import yancey.chelper.domain.settings.Settings
import yancey.chelper.ui.designsystem.CHelperTheme
import yancey.chelper.ui.feature.library.browse.PublicLibraryShowScreen
import yancey.chelper.ui.feature.library.browse.PublicLibraryShowViewModel
import yancey.chelper.ui.navigation.ClientNavHost
import yancey.chelper.ui.navigation.HomeScreenKey
import yancey.chelper.ui.navigation.PublicLibraryShowScreenKey
import yancey.chelper.ui.platform.ClientInteractionHost
import yancey.chelper.ui.platform.LocalClientServices

class PublicLibraryPresentationTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun preparedContentAppearsBeforeTheEntryAnimationFinishesWithoutAParserScreen() {
        DesktopContainer(
                temporary.root,
                engineFactory = { MockEngine { respondError(HttpStatusCode.BadRequest) } },
            )
            .use { container ->
                val services = container.dependencies.services
                val response = CompletableDeferred<RepositoryResult<LibraryFunction>>()
                val libraries =
                    object : LibraryContentRepository by services.libraryContent {
                        override suspend fun detail(id: Int, private: Boolean) = response.await()
                    }
                val preferences =
                    object : AppSettingsRepository {
                        override val state = flowOf(Settings(isEnableMcdHighlight = false))

                        override suspend fun update(transform: (Settings) -> Settings) = Unit
                    }
                val model =
                    PublicLibraryShowViewModel(
                        services.accounts,
                        libraries,
                        preferences,
                        Dispatchers.Unconfined,
                    )
                val owner =
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                owner.viewModelStore.put("detail", model)
                lateinit var nav: NavHostController
                try {
                    compose.setContent {
                        CompositionLocalProvider(
                            LocalClientServices provides services,
                            LocalViewModelStoreOwner provides owner,
                        ) {
                            CHelperTheme(CHelperTheme.Theme.Light, null) {
                                ClientInteractionHost {
                                    Box(Modifier.size(420.dp, 740.dp)) {
                                        nav = rememberNavController()
                                        ClientNavHost(nav, HomeScreenKey) {
                                            composable<HomeScreenKey> {
                                                Box(Modifier.size(420.dp, 740.dp))
                                            }
                                            composable<PublicLibraryShowScreenKey> {
                                                PublicLibraryShowScreen(
                                                    7,
                                                    navController = nav,
                                                    settings = preferences,
                                                    viewModel = model,
                                                )
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
                    compose.onNodeWithContentDescription("正在加载命令库").assertExists()
                    compose.runOnUiThread {
                        response.complete(
                            RepositoryResult(
                                LibraryFunction(id = 7, name = "示例命令库", content = "say hello")
                            )
                        )
                    }
                    compose.mainClock.advanceTimeBy(128)
                    compose.onNodeWithText("say hello").assertIsDisplayed()
                    compose.onNodeWithContentDescription("正在加载命令库").assertDoesNotExist()
                    compose.runOnIdle {
                        assertEquals(
                            Lifecycle.State.STARTED,
                            nav.currentBackStackEntry!!.lifecycle.currentState,
                        )
                    }
                    repeat(32) {
                        compose.mainClock.advanceTimeByFrame()
                        compose.onNodeWithText("解析中...").assertDoesNotExist()
                        compose.onNodeWithText("加载中...").assertDoesNotExist()
                    }
                    compose.onNodeWithText("say hello").assertIsDisplayed()
                    compose.onNodeWithText("示例命令库").assertIsDisplayed()
                    compose.onNodeWithContentDescription("正在加载命令库").assertDoesNotExist()
                    compose.mainClock.autoAdvance = true
                } finally {
                    compose.runOnUiThread { owner.viewModelStore.clear() }
                }
            }
    }
}
