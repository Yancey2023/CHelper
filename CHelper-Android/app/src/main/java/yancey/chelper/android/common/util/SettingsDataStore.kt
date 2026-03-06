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

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer,
)

@Serializable
data class Settings(
    val isEnableUpdateNotifications: Boolean,
    val themeId: String,
    val floatingWindowAlpha: Float,
    val floatingWindowSize: Int,
    val isCheckingBySelection: Boolean,
    val isHideWindowWhenCopying: Boolean,
    val isSavingWhenPausing: Boolean,
    val isCrowded: Boolean,
    val isShowErrorReason: Boolean,
    val isSyntaxHighlight: Boolean,
    val cpackBranch: String,
    val isShowPublicLibrary: Boolean,
)

object SettingsSerializer : Serializer<Settings> {

    override val defaultValue: Settings = Settings(
        isEnableUpdateNotifications = true,
        themeId = "MODE_NIGHT_FOLLOW_SYSTEM",
        floatingWindowAlpha = 1.0f,
        floatingWindowSize = 40,
        isCheckingBySelection = true,
        isHideWindowWhenCopying = false,
        isSavingWhenPausing = true,
        isCrowded = false,
        isShowErrorReason = true,
        isSyntaxHighlight = true,
        cpackBranch = "release-experiment",
        isShowPublicLibrary = true,
    )

    override suspend fun readFrom(input: InputStream): Settings =
        try {
            withContext(Dispatchers.IO) {
                Json.decodeFromString<Settings>(
                    input.readBytes().decodeToString()
                )
            }
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Settings", serialization)
        }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                Json.encodeToString(t)
                    .encodeToByteArray()
            )
        }
    }
}

class SettingsDataStore(private val context: Context) {

    fun isEnableUpdateNotifications(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isEnableUpdateNotifications }

    fun themeId(): Flow<String> =
        context.settingsDataStore.data.map { it.themeId }

    fun floatingWindowAlpha(): Flow<Float> =
        context.settingsDataStore.data.map { it.floatingWindowAlpha }

    fun floatingWindowSize(): Flow<Int> =
        context.settingsDataStore.data.map { it.floatingWindowSize }

    fun isCheckingBySelection(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isCheckingBySelection }

    fun isHideWindowWhenCopying(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isHideWindowWhenCopying }

    fun isSavingWhenPausing(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isSavingWhenPausing }

    fun isCrowded(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isCrowded }

    fun isShowErrorReason(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isShowErrorReason }

    fun isSyntaxHighlight(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isSyntaxHighlight }

    fun cpackBranch(): Flow<String> =
        context.settingsDataStore.data.map { it.cpackBranch }

    fun isShowPublicLibrary(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isShowPublicLibrary }

    suspend fun setIsEnableUpdateNotifications(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isEnableUpdateNotifications = value) }
    }

    suspend fun setThemeId(value: String) {
        context.settingsDataStore.updateData { it.copy(themeId = value) }
    }

    suspend fun setFloatingWindowAlpha(value: Float) {
        context.settingsDataStore.updateData { it.copy(floatingWindowAlpha = value) }
    }

    suspend fun setFloatingWindowSize(value: Int) {
        context.settingsDataStore.updateData { it.copy(floatingWindowSize = value) }
    }

    suspend fun setIsCheckingBySelection(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isCheckingBySelection = value) }
    }

    suspend fun setIsHideWindowWhenCopying(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isHideWindowWhenCopying = value) }
    }

    suspend fun setIsSavingWhenPausing(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isSavingWhenPausing = value) }
    }

    suspend fun setIsCrowded(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isCrowded = value) }
    }

    suspend fun setIsShowErrorReason(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isShowErrorReason = value) }
    }

    suspend fun setIsSyntaxHighlight(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isSyntaxHighlight = value) }
    }

    suspend fun setCpackBranch(value: String) {
        context.settingsDataStore.updateData { it.copy(cpackBranch = value) }
    }

    suspend fun setIsShowPublicLibrary(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isShowPublicLibrary = value) }
    }
}
