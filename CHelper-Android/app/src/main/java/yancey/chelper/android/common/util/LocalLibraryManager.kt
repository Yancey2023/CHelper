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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.network.ServiceManager
import yancey.chelper.network.library.data.LibraryFunction
import java.io.File

class LocalLibraryManager private constructor(private val file: File) {
    private var isInit = false
    private var libraryFunctions = mutableStateListOf<LibraryFunction>()

    suspend fun ensureInit() {
        if (!isInit) {
            if (file.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        val libraryFunctions0 =
                            ServiceManager.GSON!!.fromJson<List<LibraryFunction>>(
                                FileUtil.readString(file),
                                object :
                                    TypeToken<List<LibraryFunction>>() {
                                }.type
                            )
                        libraryFunctions.clear()
                        libraryFunctions.addAll(libraryFunctions0)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    fun getFunctions(): SnapshotStateList<LibraryFunction> {
        return libraryFunctions
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        FileUtil.writeString(file, ServiceManager.GSON!!.toJson(libraryFunctions))
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