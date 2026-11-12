package yancey.chelper.desktop.di

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import yancey.chelper.domain.command.CommandDraft
import yancey.chelper.domain.command.CommandDraftWrite
import yancey.chelper.domain.library.LibraryFunction
import yancey.chelper.nativecore.command.NativeCommandEngine

class DesktopContainerTest {
    @Test
    fun `stale editor cannot resurrect a deleted document`() = runBlocking {
        val root = Files.createTempDirectory("CHelper-deleted-").toFile()
        DesktopContainer(root).use { container ->
            val local = container.stores.libraries
            val id = local.addLocalLibraryFunction(LibraryFunction(name = "original"))
            val editor = local.localLibraryFunctions().first().single()
            local.removeLocalLibraryFunction(id)
            assertFalse(local.updateLocalLibraryDocument(id, editor.copy(name = "stale")))
            assertTrue(local.localLibraryFunctions().first().isEmpty())
        }
        assertTrue(root.deleteRecursively())
    }

    @Test
    fun `JNI analyzes and completes commands from a unicode installation path`() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = Files.createTempDirectory("CHelper-中文-").toFile()
        DesktopContainer(root).use { container ->
            container.initialize()
            val services = container.dependencies.services
            NativeCommandEngine.load(services.packFilenames, services.openCore, "release-vanilla")
                .use { engine ->
                    engine.parse("say hello").use { parsed ->
                        assertTrue(parsed.errors.isNullOrEmpty())
                        assertEquals(9, parsed.tokens?.size)
                    }
                    engine.parse("sa").use { parsed ->
                        val index =
                            (0 until parsed.suggestionCount(2)).first {
                                parsed.suggestion(2, it)?.name?.startsWith("say") == true
                            }
                        assertTrue(parsed.apply(2, index)?.text?.startsWith("say") == true)
                    }
                }
            assertTrue(
                container.dependencies
                    .convert("execute @a ~ ~ ~ say hello")
                    .startsWith("execute as @a")
            )
        }
    }

    @Test
    fun `final draft is flushed before closing and survives restart`() = runBlocking {
        val root = Files.createTempDirectory("CHelper-drafts-").toFile()
        DesktopContainer(root).use { container ->
            val repo = container.stores.commandDrafts
            repeat(5) {
                repo.save(
                    CommandDraftWrite(CommandDraft("say $it", 5, 5), repo.edits.claimText(), true)
                )
            }
        }
        DesktopContainer(root).use { container ->
            assertEquals("say 4", container.stores.commandDrafts.read()?.text)
        }
        assertTrue(root.deleteRecursively())
    }

    @Test
    fun `local library edits keep their identity and deletion survives restart`() = runBlocking {
        val root = Files.createTempDirectory("CHelper-libraries-").toFile()
        val id =
            DesktopContainer(root).use { container ->
                val platform = container.stores.libraries
                val id =
                    platform.addLocalLibraryFunction(
                        LibraryFunction(name = "one", content = "say one")
                    )
                assertTrue(
                    platform.updateLocalLibraryDocument(
                        id,
                        platform.localLibraryFunctions().first().single().copy(name = "two"),
                    )
                )
                assertEquals("two", platform.localLibraryFunctions().first().single().name)
                id
            }
        DesktopContainer(root).use { container ->
            val platform = container.stores.libraries
            assertEquals(id, platform.localLibraryFunctions().first().single().localEntryId)
            platform.removeLocalLibraryFunction(id)
            assertTrue(platform.localLibraryFunctions().first().isEmpty())
        }
        DesktopContainer(root).use {
            assertTrue(it.stores.libraries.localLibraryFunctions().first().isEmpty())
        }
        assertTrue(root.deleteRecursively())
    }
}
