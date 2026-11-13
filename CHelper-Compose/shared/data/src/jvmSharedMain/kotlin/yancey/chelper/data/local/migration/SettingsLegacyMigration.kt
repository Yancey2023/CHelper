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
import yancey.chelper.domain.settings.Settings

class SettingsMigrationToV74(
    private val legacyDirectory: File,
    private val destination: File? = null,
) : DataMigration<Settings> {
    private var imported = false

    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        return legacyDirectory.resolve("settings").resolve("settings.json").isFile &&
            destination?.exists() != true
    }

    override suspend fun migrate(currentData: Settings): Settings {
        return try {
            val oldSettings =
                Json.decodeFromString<JsonObject>(
                    legacyDirectory
                        .resolve("settings")
                        .resolve("settings.json")
                        .readBytes()
                        .decodeToString()
                )
            var cpackBranch = (oldSettings["cpackPath"] as? JsonPrimitive)?.content
            if (
                cpackBranch == null ||
                    !(cpackBranch == "release-vanilla" ||
                        cpackBranch == "release-experiment" ||
                        cpackBranch == "beta-vanilla" ||
                        cpackBranch == "beta-experiment" ||
                        cpackBranch == "netease-vanilla" ||
                        cpackBranch == "netease-experiment")
            ) {
                cpackBranch = null
            }
            currentData
                .copy(
                    isEnableUpdateNotifications =
                        (oldSettings["isEnableUpdateNotifications"] as? JsonPrimitive)
                            ?.booleanOrNull,
                    themeId = (oldSettings["themeId"] as? JsonPrimitive)?.content,
                    floatingWindowAlpha =
                        (oldSettings["floatingWindowAlpha"] as? JsonPrimitive)?.floatOrNull,
                    floatingWindowSize =
                        (oldSettings["floatingWindowSize"] as? JsonPrimitive)?.intOrNull,
                    isCheckingBySelection =
                        (oldSettings["isCheckingBySelection"] as? JsonPrimitive)?.booleanOrNull,
                    isHideWindowWhenCopying =
                        (oldSettings["isHideWindowWhenCopying"] as? JsonPrimitive)?.booleanOrNull,
                    isSavingWhenPausing =
                        (oldSettings["isSavingWhenPausing"] as? JsonPrimitive)?.booleanOrNull,
                    isCrowded =
                        (oldSettings["isCrowed"] as? JsonPrimitive)
                            ?.booleanOrNull, // 之前的配置文件中 crowded 名字拼写错了
                    isShowErrorReason =
                        (oldSettings["isShowErrorReason"] as? JsonPrimitive)?.booleanOrNull,
                    isSyntaxHighlight =
                        (oldSettings["isSyntaxHighlight"] as? JsonPrimitive)?.booleanOrNull,
                    cpackBranch = cpackBranch,
                    isFloatingWindowFontAlphaSync =
                        (oldSettings["isFloatingWindowFontAlphaSync"] as? JsonPrimitive)
                            ?.booleanOrNull,
                    syntaxHighlightMaxLength =
                        (oldSettings["syntaxHighlightMaxLength"] as? JsonPrimitive)?.intOrNull,
                    publicLibraryHomeRecommend =
                        (oldSettings["publicLibraryHomeRecommend"] as? JsonPrimitive)?.booleanOrNull,
                )
                .also { imported = true }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            currentData
        }
    }

    override suspend fun cleanUp() {
        if (!imported) return
        val oldFile = legacyDirectory.resolve("settings").resolve("settings.json")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val oldDir = legacyDirectory.resolve("settings")
        if (oldDir.exists() && oldDir.listFiles()?.isEmpty() == true) {
            oldDir.delete()
        }
    }
}
