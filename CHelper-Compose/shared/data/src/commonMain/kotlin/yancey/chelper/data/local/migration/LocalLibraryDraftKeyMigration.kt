package yancey.chelper.data.local.migration

import androidx.datastore.core.DataMigration
import yancey.chelper.data.library.document.localLibraryEditDraftKey
import yancey.chelper.data.local.library.LocalLibraryEditDraftData

internal fun originalLibraryDraftKey(key: String): String =
    when {
        key.startsWith("local-library:") -> key
        key == "new" -> localLibraryEditDraftKey()
        else -> localLibraryEditDraftKey(key)
    }

/** Earlier desktop editors used different draft keys; preserve recovery in the original editor. */
class LocalLibraryDraftKeyMigration : DataMigration<LocalLibraryEditDraftData> {
    override suspend fun shouldMigrate(currentData: LocalLibraryEditDraftData) =
        currentData.drafts.keys.any { originalLibraryDraftKey(it) != it }

    override suspend fun migrate(
        currentData: LocalLibraryEditDraftData
    ): LocalLibraryEditDraftData {
        val normalized =
            currentData.drafts.mapKeys { (key, _) -> originalLibraryDraftKey(key) }.toMutableMap()
        currentData.drafts
            .filterKeys { it.startsWith("local-library:") }
            .forEach { (key, value) -> normalized[key] = value }
        return currentData.copy(drafts = normalized)
    }

    override suspend fun cleanUp() = Unit
}
