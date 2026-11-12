package yancey.chelper.ui.feature.conversion

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class Old2NewViewModelTest {
    @Test
    fun `obsolete conversion cannot replace a newer result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = Old2NewViewModel(scope)
            val old = CompletableDeferred<String>()
            val current = CompletableDeferred<String>()
            vm.oldCommand.setTextAndPlaceCursorAtEnd("old")
            vm.convert("old") { withContext(NonCancellable) { old.await() } }
            vm.oldCommand.setTextAndPlaceCursorAtEnd("new")
            vm.convert("new") { current.await() }
            old.complete("obsolete")
            assertTrue(vm.isConverting)
            assertEquals("", vm.newCommand)
            current.complete("converted new")
            assertEquals("converted new", vm.newCommand)
            assertFalse(vm.isConverting)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `input changed before observation does not accept a stale conversion`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = Old2NewViewModel(scope)
            val response = CompletableDeferred<String>()
            vm.oldCommand.setTextAndPlaceCursorAtEnd("old")
            vm.convert("old") { response.await() }
            vm.oldCommand.setTextAndPlaceCursorAtEnd("changed")
            response.complete("converted old")
            assertEquals("", vm.newCommand)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `clearing input cancels conversion and leaves an empty result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = Old2NewViewModel(scope)
            val response = CompletableDeferred<String>()
            vm.oldCommand.setTextAndPlaceCursorAtEnd("old")
            vm.convert("old") { withContext(NonCancellable) { response.await() } }
            vm.oldCommand.setTextAndPlaceCursorAtEnd("")
            vm.convert("") { error("Empty input must not enter native code") }
            response.complete("obsolete")
            assertEquals("", vm.newCommand)
            assertFalse(vm.isConverting)
        } finally {
            scope.cancel()
        }
    }
}
