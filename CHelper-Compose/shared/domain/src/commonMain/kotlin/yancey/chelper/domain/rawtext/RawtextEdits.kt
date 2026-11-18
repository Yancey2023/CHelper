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

package yancey.chelper.domain.rawtext

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

enum class RawtextField {
    COPY_FORMAT,
    AUTO_SAVE,
    DRAFT,
    MOCK,
}

data class RawtextEditToken(val revisions: Map<RawtextField, Long> = emptyMap()) {
    operator fun plus(other: RawtextEditToken) = RawtextEditToken(revisions + other.revisions)
}

/**
 * Versions describe actual user edits, never a lifecycle flush or completion of background work.
 */
@OptIn(ExperimentalAtomicApi::class)
class RawtextEditTracker {
    private data class Revisions(
        val revision: Long = 0,
        val latest: Map<RawtextField, Long> = emptyMap(),
    )

    private val state = AtomicReference(Revisions())

    fun claim(vararg fields: RawtextField): RawtextEditToken {
        while (true) {
            val before = state.load()
            val revision = before.revision + 1
            val token = RawtextEditToken(fields.associateWith { revision })
            if (state.compareAndSet(before, Revisions(revision, before.latest + token.revisions)))
                return token
        }
    }

    /**
     * Called inside DataStore.updateData so validation and merging use the latest stored values.
     */
    fun merge(
        current: RawtextPreferences,
        edited: RawtextPreferences,
        token: RawtextEditToken,
    ): RawtextPreferences {
        val latest = state.load().latest
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
