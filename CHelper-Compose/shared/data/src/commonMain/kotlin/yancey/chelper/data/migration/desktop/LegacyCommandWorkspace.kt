package yancey.chelper.data.migration.desktop

import kotlinx.serialization.Serializable

@Serializable
data class LegacyCommandWorkspace(
    val command: String = "",
    val selection: Int = 0,
    val packId: String = "",
    val oldCommand: String = "",
)
