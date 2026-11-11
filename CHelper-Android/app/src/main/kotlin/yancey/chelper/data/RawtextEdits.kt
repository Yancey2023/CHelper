/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package yancey.chelper.data

internal enum class RawtextField {
    COPY_FORMAT,
    AUTO_SAVE,
    DRAFT,
    MOCK,
}

internal data class RawtextEditToken(val revisions: Map<RawtextField, Long> = emptyMap()) {
    operator fun plus(other: RawtextEditToken) = RawtextEditToken(revisions + other.revisions)
}

/**
 * Versions describe actual user edits, never a lifecycle flush or completion of background work.
 */
internal class RawtextEditTracker {
    private var revision = 0L
    private val latest = mutableMapOf<RawtextField, Long>()

    @Synchronized
    fun claim(vararg fields: RawtextField): RawtextEditToken {
        val version = ++revision
        return RawtextEditToken(
            fields.associateWith {
                latest[it] = version
                version
            }
        )
    }

    /**
     * Called inside DataStore.updateData so validation and merging use the latest stored values.
     */
    @Synchronized
    fun merge(
        current: RawtextPreferences,
        edited: RawtextPreferences,
        token: RawtextEditToken,
    ): RawtextPreferences {
        fun accepts(field: RawtextField) =
            token.revisions[field]?.let { it == latest[field] } == true
        var result = current
        if (accepts(RawtextField.COPY_FORMAT)) result = result.copy(copyFormat = edited.copyFormat)
        if (accepts(RawtextField.AUTO_SAVE))
            result =
                result.copy(
                    autoSavePrompted = edited.autoSavePrompted,
                    autoSaveEnabled = edited.autoSaveEnabled,
                )
        if (accepts(RawtextField.MOCK)) result = result.copy(mockSettings = edited.mockSettings)
        if (!result.autoSaveEnabled) result = result.copy(draft = null)
        else if (accepts(RawtextField.DRAFT)) result = result.copy(draft = edited.draft)
        return result
    }
}
