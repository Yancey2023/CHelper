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

package yancey.chelper.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

const val DEFAULT_SYNTAX_HIGHLIGHT_MAX_LENGTH = 400_000
const val MAX_SYNTAX_HIGHLIGHT_LENGTH = 10_000_000

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
    val syntaxHighlightLimitVersion: Int? = null,
    val publicLibraryHomeRecommend: Boolean? = null,
    val isEnableMcdHighlight: Boolean? = null,
    val isEnableLoongFlowImportMiniIcon: Boolean? = null,
    val hasShownCommandEditorHint: Boolean? = null,
)

interface AppSettingsRepository {
    val state: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}

/** Defaults shared by both hosts; nullable persisted fields retain migration compatibility. */
fun Settings.withDefaults(): Settings =
    copy(
        isEnableUpdateNotifications = isEnableUpdateNotifications ?: true,
        themeId = themeId ?: "MODE_NIGHT_FOLLOW_SYSTEM",
        floatingWindowAlpha = floatingWindowAlpha ?: 1.0f,
        floatingWindowScreenAlpha = floatingWindowScreenAlpha ?: 1.0f,
        floatingWindowSize = floatingWindowSize ?: 40,
        isFloatingWindowFontAlphaSync = isFloatingWindowFontAlphaSync ?: true,
        isCheckingBySelection = isCheckingBySelection ?: true,
        isHideWindowWhenCopying = isHideWindowWhenCopying ?: false,
        isSavingWhenPausing = isSavingWhenPausing ?: true,
        isCrowded = isCrowded ?: false,
        isShowErrorReason = isShowErrorReason ?: true,
        isSyntaxHighlight = isSyntaxHighlight ?: true,
        syntaxHighlightMaxLength = syntaxHighlightMaxLength ?: DEFAULT_SYNTAX_HIGHLIGHT_MAX_LENGTH,
        cpackBranch = cpackBranch ?: "release-experiment",
        isShowPublicLibrary = isShowPublicLibrary ?: true,
        publicLibraryMinVersion = publicLibraryMinVersion ?: 0,
        publicLibraryHomeRecommend = publicLibraryHomeRecommend ?: true,
        tagClickBehavior = tagClickBehavior ?: "search",
        ambiguousLineDefault = ambiguousLineDefault ?: "comment",
        isHideMetadataPreview = isHideMetadataPreview ?: false,
        isEnableMcdHighlight = isEnableMcdHighlight ?: true,
        isEnableLoongFlowImportMiniIcon = isEnableLoongFlowImportMiniIcon ?: true,
    )
