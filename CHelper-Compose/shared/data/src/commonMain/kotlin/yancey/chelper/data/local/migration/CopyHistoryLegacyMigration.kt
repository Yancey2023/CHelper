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
import yancey.chelper.data.local.history.COPY_HISTORY_MAX_SIZE
import yancey.chelper.data.local.history.CopyHistoryData
import yancey.chelper.data.local.isFile
import yancey.chelper.data.local.readText

class CopyHistoryDataMigrationToV75(
    private val legacyDirectory: Path,
    private val destination: Path? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : DataMigration<CopyHistoryData> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: CopyHistoryData): Boolean {
        return fileSystem.isFile(legacyDirectory.resolve("history.txt")) &&
            destination?.let(fileSystem::exists) != true
    }

    override suspend fun migrate(currentData: CopyHistoryData): CopyHistoryData {
        return try {
            val oldFile = legacyDirectory.resolve("history.txt")
            val content = fileSystem.readText(oldFile)
            val oldHistoryList = content.split("\n").filter { it.isNotEmpty() }
            CopyHistoryData(history = oldHistoryList.take(COPY_HISTORY_MAX_SIZE)).also {
                imported = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        if (!imported) return
        val oldFile = legacyDirectory.resolve("history.txt")
        if (fileSystem.exists(oldFile)) {
            fileSystem.delete(oldFile)
        }
    }
}
