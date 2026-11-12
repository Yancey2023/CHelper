package yancey.chelper.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PresentationTest {
    protected val scheduler = TestCoroutineScheduler()
    protected val dispatcher = StandardTestDispatcher(scheduler)
    private var nextId = 0
    private val store = ViewModelStore()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun clearOwners() {
        store.clear()
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    protected fun <T : ViewModel> own(model: T): T =
        model.also { store.put("test-${nextId++}", it) }

    protected fun clearViewModels() = store.clear()
}
