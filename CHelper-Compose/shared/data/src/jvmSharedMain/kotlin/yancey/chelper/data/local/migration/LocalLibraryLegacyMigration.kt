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

package yancey.chelper.data.local.migration

import androidx.datastore.core.DataMigration
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.domain.library.LibraryFunction

class LocalLibraryDataMigrationToV75(
    private val legacyDirectory: File,
    private val destination: File? = null,
) : DataMigration<LocalLibraryData> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: LocalLibraryData): Boolean {
        return legacyDirectory.resolve("localLibrary").resolve("data.json").isFile &&
            destination?.exists() != true
    }

    override suspend fun migrate(currentData: LocalLibraryData): LocalLibraryData {
        return try {
            val oldFile = legacyDirectory.resolve("localLibrary").resolve("data.json")
            val oldFunctions =
                Json.decodeFromString<List<LibraryFunction>>(oldFile.readBytes().decodeToString())
            LocalLibraryData(functions = oldFunctions).also { imported = true }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        if (!imported) return
        val oldFile = legacyDirectory.resolve("localLibrary").resolve("data.json")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val oldDir = legacyDirectory.resolve("localLibrary")
        if (oldDir.exists() && oldDir.listFiles()?.isEmpty() == true) {
            oldDir.delete()
        }
    }
}
