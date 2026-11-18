package yancey.chelper.data.migration.desktop

import kotlinx.serialization.Serializable
import yancey.chelper.domain.rawtext.RawtextMockSnapshot
import yancey.chelper.domain.rawtext.RawtextSegmentSnapshot

/** Read-only migration schema for the former additional workbench. */
@Serializable
data class LegacyWorkspaceDraft(
    val packId: String = "",
    val segments: List<RawtextSegmentSnapshot> = listOf(RawtextSegmentSnapshot("Text")),
    val mock: RawtextMockSnapshot = RawtextMockSnapshot(),
    val darkMode: Boolean? = null,
)
