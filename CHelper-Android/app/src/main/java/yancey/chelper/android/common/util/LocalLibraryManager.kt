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

package yancey.chelper.android.common.util

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import yancey.chelper.network.library.data.LibraryFunction
import java.io.File

class LocalLibraryManager private constructor(private val file: File) {
    private var isInit = false
    private var libraryFunctions = mutableStateListOf<LibraryFunction>()

    suspend fun ensureInit() {
        if (!isInit && file.exists()) {
            val libraryFunctions0 = withContext(Dispatchers.IO) {
                try {
                    return@withContext FileUtil.readString(file)?.let {
                        Json.decodeFromString<List<LibraryFunction>>(it)
                    }
                } catch (_: Throwable) {
                }
                return@withContext null
            }
            if (libraryFunctions0 != null) {
                libraryFunctions.clear()
                libraryFunctions.addAll(libraryFunctions0)
            }
        }
    }

    fun getFunctions(): SnapshotStateList<LibraryFunction> {
        return libraryFunctions
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        file.outputStream().write(
            Json.encodeToString(libraryFunctions.toList())
                .encodeToByteArray()
        )
    }

    companion object {
        @JvmField
        var INSTANCE: LocalLibraryManager? = null

        @JvmStatic
        fun init(file: File) {
            INSTANCE = LocalLibraryManager(file)
        }
    }
}