package yancey.chelper.desktop.instance

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.ui.navigation.CommandLabDeepLink

class DesktopInstanceTest {
    @Test
    fun aSecondLaunchForwardsTheOriginalShareLinkAndReleasesOwnershipOnExit() = runBlocking {
        val directory = Files.createTempDirectory("CHelper-instance-").toFile()
        try {
            DesktopInstance.acquire(directory, emptyList())!!.use { first ->
                assertNull(
                    DesktopInstance.acquire(
                        directory,
                        listOf("chelper://commandlab/library/42?private=1"),
                    )
                )
                val request = withTimeout(2000) { first.requests.first { it.target != null } }
                assertEquals(CommandLabDeepLink.Library(42, isPrivate = true), request.target)
                assertTrue(request.sequence > 1)
                assertNull(DesktopInstance.acquire(directory, emptyList()))
                assertNull(
                    withTimeout(2000) { first.requests.first { it.sequence > request.sequence } }
                        .target
                )
            }
            DesktopInstance.acquire(directory, listOf("chelper://commandlab/library/7"))!!.use {
                assertEquals(CommandLabDeepLink.Library(7), it.requests.first().target)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun isolatedDataDirectoriesHaveIndependentApplicationOwners() {
        val directory = Files.createTempDirectory("CHelper-isolated-instances-").toFile()
        try {
            DesktopInstance.acquire(directory.resolve("one"), emptyList())!!.use {
                DesktopInstance.acquire(directory.resolve("two"), emptyList())!!.use {
                    assertNotNull(it)
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
