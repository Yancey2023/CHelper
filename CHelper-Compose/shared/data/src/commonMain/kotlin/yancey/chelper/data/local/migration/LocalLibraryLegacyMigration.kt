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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import yancey.chelper.data.local.isFile
import yancey.chelper.data.local.library.LocalLibraryData
import yancey.chelper.data.local.readText
import yancey.chelper.domain.library.LibraryFunction

class LocalLibraryDataMigrationToV75(
    private val legacyDirectory: Path,
    private val destination: Path? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : DataMigration<LocalLibraryData> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: LocalLibraryData): Boolean {
        return fileSystem.isFile(legacyDirectory.resolve("localLibrary").resolve("data.json")) &&
            destination?.let(fileSystem::exists) != true
    }

    override suspend fun migrate(currentData: LocalLibraryData): LocalLibraryData {
        return try {
            val oldFile = legacyDirectory.resolve("localLibrary").resolve("data.json")
            val oldFunctions =
                Json.decodeFromString<List<LibraryFunction>>(fileSystem.readText(oldFile))
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
        if (fileSystem.exists(oldFile)) {
            fileSystem.delete(oldFile)
        }
        val oldDir = legacyDirectory.resolve("localLibrary")
        if (fileSystem.exists(oldDir) && fileSystem.list(oldDir).isEmpty()) {
            fileSystem.delete(oldDir)
        }
    }
}
