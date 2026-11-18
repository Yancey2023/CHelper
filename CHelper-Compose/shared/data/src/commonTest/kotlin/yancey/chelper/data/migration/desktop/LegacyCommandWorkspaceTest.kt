package yancey.chelper.data.migration.desktop

import kotlin.test.*
import kotlinx.serialization.json.Json

class LegacyCommandWorkspaceTest {
    @Test
    fun draftRoundTripPreservesContentAndTheme() {
        val draft =
            LegacyCommandWorkspace(command = "say 中文😀", selection = 4, oldCommand = "other draft")
        assertEquals(
            draft,
            Json.decodeFromString<LegacyCommandWorkspace>(Json.encodeToString(draft)),
        )
    }
}
