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
import kotlinx.serialization.builtins.ListSerializer
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
    val tagClickBehavior: String? = null,
    val ambiguousLineDefault: String? = null,
    val isHideMetadataPreview: Boolean? = null,
    val isFloatingWindowFontAlphaSync: Boolean? = null,
    val syntaxHighlightMaxLength: Int? = null,
    val publicLibraryHomeRecommend: Boolean? = null,
    val isEnableMcdHighlight: Boolean? = null,
    val isEnableLoongFlowImportMiniIcon: Boolean? = null,
    val hasShownCommandEditorHint: Boolean? = null,
    /** 已安装资源包（拓展包）列表，JSON 数组字符串，顺序 = 列表顺序（列表靠前 = 叠加时优先） */
    val extensionPacksJson: String? = null,
)

/**
 * 已安装的拓展包条目（settings.extensionPacksJson 内每一项）。
 * @param fileName 包文件在 filesDir/packs/ 下的文件名（导入时以 <packId>-<versionCode>.chepack 命名）
 */
@Serializable
data class ExtensionPackEntry(
    val packId: String,
    val name: String,
    val version: String,
    val fileName: String,
    val enabled: Boolean = true,
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

    /**
     * 预热进程级共享的 DataStore，把设置文件读进内存缓存。
     * 必须在 Application.onCreate 中、任何 UI 创建前调用一次。
     * 之后 BaseComposeActivity / 悬浮窗的 [themeIdBlocking] 才能在主线程上
     * 几乎零开销地同步读取主题，避免冷读磁盘导致启动卡顿与首帧主题错误。
     */
    fun init() {
        runBlocking { context.settingsDataStore.data.first() }
    }

    fun isEnableUpdateNotifications(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isEnableUpdateNotifications ?: true }

    fun themeId(): Flow<String> =
        context.settingsDataStore.data.map { it.themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM" }

    /**
     * 同步读取主题设置。
     * Application.onCreate 已经通过 init() 预热过 DataStore，这里只会命中内存缓存，
     * 供 Activity / 悬浮窗在首帧渲染前确定主题，避免启动时先渲染亮色再动画切换到夜间。
     */
    fun themeIdBlocking(): String = runBlocking { themeId().first() }

    fun floatingWindowIconAlpha(): Flow<Float> =
        context.settingsDataStore.data.map { it.floatingWindowAlpha ?: 1.0f }

    fun floatingWindowScreenAlpha(): Flow<Float> =
        context.settingsDataStore.data.map { it.floatingWindowScreenAlpha ?: 1.0f }

    fun floatingWindowIconSize(): Flow<Int> =
        context.settingsDataStore.data.map { it.floatingWindowSize ?: 40 }

    fun isFloatingWindowFontAlphaSync(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isFloatingWindowFontAlphaSync ?: true }

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

    /** 已安装拓展包列表（顺序 = 列表顺序，列表靠前 = 叠加时优先） */
    fun extensionPacks(): Flow<List<ExtensionPackEntry>> =
        context.settingsDataStore.data.map { settings ->
            val json = settings.extensionPacksJson ?: return@map emptyList()
            runCatching {
                Json.decodeFromString(ListSerializer(ExtensionPackEntry.serializer()), json)
            }.getOrDefault(emptyList())
        }

    suspend fun setExtensionPacks(entries: List<ExtensionPackEntry>) {
        context.settingsDataStore.updateData {
            it.copy(
                extensionPacksJson = Json.encodeToString(
                    ListSerializer(ExtensionPackEntry.serializer()),
                    entries
                )
            )
        }
    }

    fun syntaxHighlightMaxLength(): Flow<Int> =
        context.settingsDataStore.data.map { it.syntaxHighlightMaxLength ?: 4000 }

    fun cpackBranch(): Flow<String> =
        // 主包段 id（"release/experiment"）；兼容旧值（"release-experiment"，'-' 视作 '/'）
        context.settingsDataStore.data.map { it.cpackBranch ?: "release/experiment" }

    fun isShowPublicLibrary(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isShowPublicLibrary ?: true }

    fun publicLibraryMinVersion(): Flow<Int> =
        context.settingsDataStore.data.map { it.publicLibraryMinVersion ?: 0 }

    fun isPublicLibraryHomeRecommend(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.publicLibraryHomeRecommend ?: true }

    suspend fun setPublicLibraryHomeRecommend(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(publicLibraryHomeRecommend = value) }
    }

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

    suspend fun setIsFloatingWindowFontAlphaSync(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isFloatingWindowFontAlphaSync = value) }
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

    suspend fun setSyntaxHighlightMaxLength(value: Int) {
        context.settingsDataStore.updateData { it.copy(syntaxHighlightMaxLength = value) }
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

    fun tagClickBehavior(): Flow<String> =
        context.settingsDataStore.data.map { it.tagClickBehavior ?: "search" }

    suspend fun setTagClickBehavior(value: String) {
        context.settingsDataStore.updateData { it.copy(tagClickBehavior = value) }
    }

    fun ambiguousLineDefault(): Flow<String> =
        context.settingsDataStore.data.map { it.ambiguousLineDefault ?: "comment" }

    suspend fun setAmbiguousLineDefault(value: String) {
        context.settingsDataStore.updateData { it.copy(ambiguousLineDefault = value) }
    }

    fun isHideMetadataPreview(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isHideMetadataPreview ?: false }

    suspend fun setIsHideMetadataPreview(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isHideMetadataPreview = value) }
    }

    fun isEnableMcdHighlight(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isEnableMcdHighlight ?: true }

    suspend fun setIsEnableMcdHighlight(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isEnableMcdHighlight = value) }
    }

    fun isEnableLoongFlowImportMiniIcon(): Flow<Boolean> =
        context.settingsDataStore.data.map { it.isEnableLoongFlowImportMiniIcon ?: true }

    suspend fun setIsEnableLoongFlowImportMiniIcon(value: Boolean) {
        context.settingsDataStore.updateData { it.copy(isEnableLoongFlowImportMiniIcon = value) }
    }

    suspend fun claimCommandEditorHint(): Boolean {
        var claimed = false
        context.settingsDataStore.updateData {
            if (it.hasShownCommandEditorHint == true) {
                it
            } else {
                claimed = true
                it.copy(hasShownCommandEditorHint = true)
            }
        }
        return claimed
    }
}

/**
 * 0.4.0 版本之后，软件设置存储从自己写的框架改为使用官方方案 DataStore，该文件用于数据迁移
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
                isFloatingWindowFontAlphaSync = (oldSettings["isFloatingWindowFontAlphaSync"] as? JsonPrimitive)?.booleanOrNull,
                syntaxHighlightMaxLength = (oldSettings["syntaxHighlightMaxLength"] as? JsonPrimitive)?.intOrNull,
                publicLibraryHomeRecommend = (oldSettings["publicLibraryHomeRecommend"] as? JsonPrimitive)?.booleanOrNull,
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
