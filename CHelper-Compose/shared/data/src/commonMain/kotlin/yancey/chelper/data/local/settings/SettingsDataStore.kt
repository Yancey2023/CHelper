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

package yancey.chelper.data.local.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import yancey.chelper.domain.settings.Settings
import yancey.chelper.domain.settings.withDefaults

object SettingsSerializer : OkioSerializer<Settings> {

    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(source: BufferedSource): Settings =
        try {
            Json.decodeFromString<Settings>(source.readByteArray().decodeToString())
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Settings", serialization)
        }

    override suspend fun writeTo(t: Settings, sink: BufferedSink) {
        sink.write(Json.encodeToString(t).encodeToByteArray())
    }
}

class SettingsDataStore(private val store: DataStore<Settings>) :
    yancey.chelper.domain.settings.AppSettingsRepository {
    override val state: Flow<Settings>
        get() = settings()

    override suspend fun update(transform: (Settings) -> Settings) {
        store.updateData(transform)
    }

    fun settings(): Flow<Settings> = store.data

    fun isEnableUpdateNotifications(): Flow<Boolean> =
        store.data.map { it.withDefaults().isEnableUpdateNotifications!! }

    fun themeId(): Flow<String> = store.data.map { it.withDefaults().themeId!! }

    fun floatingWindowIconAlpha(): Flow<Float> =
        store.data.map { it.withDefaults().floatingWindowAlpha!! }

    fun floatingWindowScreenAlpha(): Flow<Float> =
        store.data.map { it.withDefaults().floatingWindowScreenAlpha!! }

    fun floatingWindowIconSize(): Flow<Int> =
        store.data.map { it.withDefaults().floatingWindowSize!! }

    fun isFloatingWindowFontAlphaSync(): Flow<Boolean> =
        store.data.map { it.withDefaults().isFloatingWindowFontAlphaSync!! }

    fun isCheckingBySelection(): Flow<Boolean> =
        store.data.map { it.withDefaults().isCheckingBySelection!! }

    fun isHideWindowWhenCopying(): Flow<Boolean> =
        store.data.map { it.withDefaults().isHideWindowWhenCopying!! }

    fun isSavingWhenPausing(): Flow<Boolean> =
        store.data.map { it.withDefaults().isSavingWhenPausing!! }

    fun isCrowded(): Flow<Boolean> = store.data.map { it.withDefaults().isCrowded!! }

    fun isShowErrorReason(): Flow<Boolean> =
        store.data.map { it.withDefaults().isShowErrorReason!! }

    fun isSyntaxHighlight(): Flow<Boolean> =
        store.data.map { it.withDefaults().isSyntaxHighlight!! }

    fun syntaxHighlightMaxLength(): Flow<Int> =
        store.data.map { it.withDefaults().syntaxHighlightMaxLength!! }

    fun cpackBranch(): Flow<String> = store.data.map { it.withDefaults().cpackBranch!! }

    fun isShowPublicLibrary(): Flow<Boolean> =
        store.data.map { it.withDefaults().isShowPublicLibrary!! }

    fun publicLibraryMinVersion(): Flow<Int> =
        store.data.map { it.withDefaults().publicLibraryMinVersion!! }

    fun isPublicLibraryHomeRecommend(): Flow<Boolean> =
        store.data.map { it.withDefaults().publicLibraryHomeRecommend!! }

    suspend fun setPublicLibraryHomeRecommend(value: Boolean) {
        store.updateData { it.copy(publicLibraryHomeRecommend = value) }
    }

    suspend fun setIsEnableUpdateNotifications(value: Boolean) {
        store.updateData { it.copy(isEnableUpdateNotifications = value) }
    }

    suspend fun setThemeId(value: String) {
        store.updateData { it.copy(themeId = value) }
    }

    suspend fun setFloatingWindowIconAlpha(value: Float) {
        store.updateData { it.copy(floatingWindowAlpha = value) }
    }

    suspend fun setFloatingWindowScreenAlpha(value: Float) {
        store.updateData { it.copy(floatingWindowScreenAlpha = value) }
    }

    suspend fun setFloatingWindowIconSize(value: Int) {
        store.updateData { it.copy(floatingWindowSize = value) }
    }

    suspend fun setIsFloatingWindowFontAlphaSync(value: Boolean) {
        store.updateData { it.copy(isFloatingWindowFontAlphaSync = value) }
    }

    suspend fun setIsCheckingBySelection(value: Boolean) {
        store.updateData { it.copy(isCheckingBySelection = value) }
    }

    suspend fun setIsHideWindowWhenCopying(value: Boolean) {
        store.updateData { it.copy(isHideWindowWhenCopying = value) }
    }

    suspend fun setIsSavingWhenPausing(value: Boolean) {
        store.updateData { it.copy(isSavingWhenPausing = value) }
    }

    suspend fun setIsCrowded(value: Boolean) {
        store.updateData { it.copy(isCrowded = value) }
    }

    suspend fun setIsShowErrorReason(value: Boolean) {
        store.updateData { it.copy(isShowErrorReason = value) }
    }

    suspend fun setIsSyntaxHighlight(value: Boolean) {
        store.updateData { it.copy(isSyntaxHighlight = value) }
    }

    suspend fun setSyntaxHighlightMaxLength(value: Int) {
        store.updateData { it.copy(syntaxHighlightMaxLength = value) }
    }

    suspend fun setCpackBranch(value: String) {
        store.updateData { it.copy(cpackBranch = value) }
    }

    suspend fun setIsShowPublicLibrary(value: Boolean) {
        store.updateData { it.copy(isShowPublicLibrary = value) }
    }

    suspend fun setPublicLibraryMinVersion(value: Int) {
        store.updateData { it.copy(publicLibraryMinVersion = value) }
    }

    fun tagClickBehavior(): Flow<String> = store.data.map { it.withDefaults().tagClickBehavior!! }

    suspend fun setTagClickBehavior(value: String) {
        store.updateData { it.copy(tagClickBehavior = value) }
    }

    fun ambiguousLineDefault(): Flow<String> =
        store.data.map { it.withDefaults().ambiguousLineDefault!! }

    suspend fun setAmbiguousLineDefault(value: String) {
        store.updateData { it.copy(ambiguousLineDefault = value) }
    }

    fun isHideMetadataPreview(): Flow<Boolean> =
        store.data.map { it.withDefaults().isHideMetadataPreview!! }

    suspend fun setIsHideMetadataPreview(value: Boolean) {
        store.updateData { it.copy(isHideMetadataPreview = value) }
    }

    fun isEnableMcdHighlight(): Flow<Boolean> =
        store.data.map { it.withDefaults().isEnableMcdHighlight!! }

    suspend fun setIsEnableMcdHighlight(value: Boolean) {
        store.updateData { it.copy(isEnableMcdHighlight = value) }
    }

    fun isEnableLoongFlowImportMiniIcon(): Flow<Boolean> =
        store.data.map { it.withDefaults().isEnableLoongFlowImportMiniIcon!! }

    suspend fun setIsEnableLoongFlowImportMiniIcon(value: Boolean) {
        store.updateData { it.copy(isEnableLoongFlowImportMiniIcon = value) }
    }

    suspend fun claimCommandEditorHint(): Boolean {
        var claimed = false
        store.updateData {
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
