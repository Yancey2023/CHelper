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

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.InputStream
import java.io.OutputStream

private val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.json",
    serializer = SettingsSerializer,
    produceMigrations = {
        listOf(SettingsMigrationToV74(it))
    }
)

@Serializable
data class Settings(
    val isEnableUpdateNotifications: Boolean? = null,
    val themeId: String? = null,
    val floatingWindowAlpha: Float? = null,
    val floatingWindowScreenAlpha: Float? = null,
    val floatingWindowSize: Int? = null,
    val isCheckingBySelection: Boolean? = null,
    val isHideWindowWhenCopying: Boolean? = null,
    val isSavingWhenPausing: Boolean? = null,
    val isCrowded: Boolean? = null,
    val isShowErrorReason: Boolean? = null,
    val isSyntaxHighlight: Boolean? = null,
    val cpackBranch: String? = null,
    val isShowPublicLibrary: Boolean? = null,
    val publicLibraryMinVersion: Int? = null,
)

object SettingsSerializer : Serializer<Settings> {

    override val defaultValue: Settings = Settings()

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

    fun init() {
        runBlocking { context.settingsDataStore.data.first() }
    }

    fun isEnableUpdateNotifications(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isEnableUpdateNotifications ?: true }

    fun themeId(): Flow<String> =
        context.settingsDataStore.data.map { it.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM" }

    fun floatingWindowIconAlpha(): Flow<Float> =
        context.settingsDataStore.data.map { it.floatingWindowAlpha ?: 1.0f }

    fun floatingWindowScreenAlpha(): Flow<Float> =
        context.settingsDataStore.data.map { it.floatingWindowScreenAlpha ?: 1.0f }

    fun floatingWindowIconSize(): Flow<Int> =
        context.settingsDataStore.data.map { it.floatingWindowSize ?: 40 }

    fun isCheckingBySelection(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isCheckingBySelection ?: true }

    fun isHideWindowWhenCopying(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isHideWindowWhenCopying ?: false }

    fun isSavingWhenPausing(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isSavingWhenPausing ?: true }

    fun isCrowded(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isCrowded ?: false }

    fun isShowErrorReason(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isShowErrorReason ?: true }

    fun isSyntaxHighlight(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isSyntaxHighlight ?: true }

    fun cpackBranch(): Flow<String> =
        context.settingsDataStore.data.map { it.cpackBranch ?: "release-experiment" }

    fun isShowPublicLibrary(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isShowPublicLibrary ?: true }

    fun publicLibraryMinVersion(): Flow<Int> =
        context.settingsDataStore.data.map { it.publicLibraryMinVersion ?: 0 }

    suspend fun setIsEnableUpdateNotifications(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isEnableUpdateNotifications = value) }
    }

    suspend fun setThemeId(value: String) {
        context.settingsDataStore.updateData { it.copy(themeId = value) }
    }

    suspend fun setFloatingWindowIconAlpha(value: Float) {
        context.settingsDataStore.updateData { it.copy(floatingWindowAlpha = value) }
    }

    suspend fun setFloatingWindowScreenAlpha(value: Float) {
        context.settingsDataStore.updateData { it.copy(floatingWindowScreenAlpha = value) }
    }

    suspend fun setFloatingWindowIconSize(value: Int) {
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

    suspend fun setPublicLibraryMinVersion(value: Int) {
        context.settingsDataStore.updateData { it.copy(publicLibraryMinVersion = value) }
    }
}

/**
 * 0.4.0 版本之后，软件设置存储从自己写的框架改为使用官方方案 DataScore，该文件用于数据迁移
 */
class SettingsMigrationToV74(private val context: Context) : DataMigration<Settings> {
    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        return context.dataDir.resolve("settings").resolve("settings.json").exists()
    }

    override suspend fun migrate(currentData: Settings): Settings {
        return try {
            val oldSettings = Json.decodeFromString<JsonObject>(
                context.dataDir.resolve("settings").resolve("settings.json").readBytes()
                    .decodeToString()
            )
            var cpackBranch = (oldSettings["cpackPath"] as? JsonPrimitive)?.content
            if (cpackBranch == null ||
                !(cpackBranch == "release-vanilla" ||
                        cpackBranch == "release-experiment" ||
                        cpackBranch == "beta-vanilla" ||
                        cpackBranch == "beta-experiment" ||
                        cpackBranch == "netease-vanilla" ||
                        cpackBranch == "netease-experiment")
            ) {
                cpackBranch = null
            }
            currentData.copy(
                isEnableUpdateNotifications = (oldSettings["isEnableUpdateNotifications"] as? JsonPrimitive)?.booleanOrNull,
                themeId = (oldSettings["themeId"] as? JsonPrimitive)?.content,
                floatingWindowAlpha = (oldSettings["floatingWindowAlpha"] as? JsonPrimitive)?.floatOrNull,
                floatingWindowSize = (oldSettings["floatingWindowSize"] as? JsonPrimitive)?.intOrNull,
                isCheckingBySelection = (oldSettings["isCheckingBySelection"] as? JsonPrimitive)?.booleanOrNull,
                isHideWindowWhenCopying = (oldSettings["isHideWindowWhenCopying"] as? JsonPrimitive)?.booleanOrNull,
                isSavingWhenPausing = (oldSettings["isSavingWhenPausing"] as? JsonPrimitive)?.booleanOrNull,
                isCrowded = (oldSettings["isCrowed"] as? JsonPrimitive)?.booleanOrNull,// 之前的配置文件中 crowded 名字拼写错了
                isShowErrorReason = (oldSettings["isShowErrorReason"] as? JsonPrimitive)?.booleanOrNull,
                isSyntaxHighlight = (oldSettings["isSyntaxHighlight"] as? JsonPrimitive)?.booleanOrNull,
                cpackBranch = cpackBranch,
            )
        } catch (_: Throwable) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        val oldFile = context.dataDir.resolve("settings").resolve("settings.json")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val oldDir = context.dataDir.resolve("settings")
        if (oldDir.exists() && oldDir.listFiles()?.isEmpty() == true) {
            oldDir.delete()
        }
    }
}
