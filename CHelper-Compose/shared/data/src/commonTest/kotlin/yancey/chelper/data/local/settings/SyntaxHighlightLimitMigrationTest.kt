package yancey.chelper.data.local.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import yancey.chelper.data.local.ClientDataStores
import yancey.chelper.data.local.writeText
import yancey.chelper.domain.settings.Settings

class SyntaxHighlightLimitMigrationTest {
    @Test
    fun defaultIsExpandedAndStoredLimitsAreExpandedOnlyOnceAcrossRestarts() = runTest {
        val fs = FakeFileSystem()
        val root = "/highlight-limit".toPath()
        fs.createDirectories(root)
        fs.writeText(
            root / "settings.json",
            """{"syntaxHighlightMaxLength":4000,"isSyntaxHighlight":false}""",
        )
        try {
            ClientDataStores(root, fileSystem = fs).use { stores ->
                val settings = stores.settings.settings().first()
                assertEquals(400000, settings.syntaxHighlightMaxLength)
                assertEquals(1, settings.syntaxHighlightLimitVersion)
                assertEquals(false, settings.isSyntaxHighlight)
                stores.settings.setSyntaxHighlightMaxLength(1234)
            }
            ClientDataStores(root, fileSystem = fs).use { stores ->
                assertEquals(1234, stores.settings.syntaxHighlightMaxLength().first())
            }
            ClientDataStores(root / "fresh", fileSystem = fs).use { stores ->
                assertEquals(400000, stores.settings.syntaxHighlightMaxLength().first())
            }
        } finally {
            fs.deleteRecursively(root)
        }
    }

    @Test
    fun customLimitsAreScaledAndOverflowAndZeroAreHandled() = runTest {
        val migration = SyntaxHighlightLimitMigration()
        for ((before, after) in
            listOf(1234 to 123400, 100000 to 10000000, Int.MAX_VALUE to 10000000, 0 to 0)) {
            val result = migration.migrate(Settings(syntaxHighlightMaxLength = before))
            assertEquals(after, result.syntaxHighlightMaxLength)
            assertFalse(migration.shouldMigrate(result))
            assertEquals(result, migration.migrate(result))
        }
        val future = Settings(syntaxHighlightMaxLength = 4321, syntaxHighlightLimitVersion = 2)
        assertFalse(migration.shouldMigrate(future))
        assertEquals(future, migration.migrate(future))
    }
}
