package yancey.chelper.data.local.settings

import androidx.datastore.core.DataMigration
import okio.FileSystem
import okio.Path
import yancey.chelper.domain.settings.MAX_SYNTAX_HIGHLIGHT_LENGTH
import yancey.chelper.domain.settings.Settings

/** Expand old thresholds once, including existing customized values, without integer overflow. */
class SyntaxHighlightLimitMigration(
    private val legacySettingsFile: Path? = null,
    private val destination: Path? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : DataMigration<Settings> {
    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        if ((currentData.syntaxHighlightLimitVersion ?: 0) >= 1) return false
        // An unreadable legacy source must remain recoverable. Do not create a destination
        // containing only our version marker and thereby prevent its import on the next launch.
        return !(currentData == Settings() &&
            legacySettingsFile?.let(fileSystem::exists) == true &&
            destination?.let(fileSystem::exists) != true)
    }

    override suspend fun migrate(currentData: Settings): Settings {
        if (!shouldMigrate(currentData)) return currentData
        return currentData.copy(
            syntaxHighlightMaxLength =
                currentData.syntaxHighlightMaxLength?.let {
                    (it.toLong() * 100).coerceIn(0, MAX_SYNTAX_HIGHLIGHT_LENGTH.toLong()).toInt()
                },
            syntaxHighlightLimitVersion = 1,
        )
    }

    override suspend fun cleanUp() = Unit
}
