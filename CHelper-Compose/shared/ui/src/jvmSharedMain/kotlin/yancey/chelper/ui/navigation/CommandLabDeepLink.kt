/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package yancey.chelper.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface CommandLabDeepLink {
    data object LibraryHome : CommandLabDeepLink

    data class Library(
        val id: Int,
        val isPrivate: Boolean = false,
        val importToLocal: Boolean = false,
    ) : CommandLabDeepLink
}

object CommandLabDeepLinkParser {
    fun parse(rawUrl: String?): CommandLabDeepLink? {
        val uri =
            rawUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it) }.getOrNull() }
                ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val query = queryParameters(uri.rawQuery)

        return when {
            scheme == "chelper" && host == "commandlab" ->
                parseCommandLabPath(
                    segments,
                    queryLibraryId(query),
                    queryPrivate(query),
                    queryImportToLocal(query),
                )

            scheme in setOf("http", "https") &&
                host in setOf("abyssous.site", "www.abyssous.site") -> {
                if (segments.firstOrNull() != "app") return null
                parseCommandLabPath(
                    segments.drop(1),
                    queryLibraryId(query),
                    queryPrivate(query),
                    queryImportToLocal(query),
                )
            }

            else -> null
        }
    }

    private fun parseCommandLabPath(
        segments: List<String>,
        queryId: Int?,
        isPrivate: Boolean,
        importToLocal: Boolean,
    ): CommandLabDeepLink? {
        if (segments.isEmpty()) {
            return queryId?.let { CommandLabDeepLink.Library(it, isPrivate, importToLocal) }
                ?: CommandLabDeepLink.LibraryHome
        }
        if (segments.first() != "library") return null
        val id = segments.getOrNull(1)?.toIntOrNull() ?: queryId
        return id?.takeIf { it > 0 }
            ?.let { CommandLabDeepLink.Library(it, isPrivate, importToLocal) }
            ?: if (segments.size == 1) CommandLabDeepLink.LibraryHome else null
    }

    private fun queryLibraryId(query: Map<String, String>): Int? =
        sequenceOf("library_id", "detail_id").mapNotNull(query::get).firstNotNullOfOrNull { value ->
            value.toIntOrNull()?.takeIf { it > 0 }
        }

    private fun queryPrivate(query: Map<String, String>): Boolean {
        val value = query["private"] ?: query["is_private"] ?: return false
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun queryImportToLocal(query: Map<String, String>): Boolean {
        val value = query["local"] ?: query["import_local"] ?: return false
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').associate { item ->
            val parts = item.split('=', limit = 2)
            val name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
            name to value
        }
    }
}

fun NavHostController.openCommandLabDeepLink(target: CommandLabDeepLink) {
    when (target) {
        CommandLabDeepLink.LibraryHome -> {
            if (currentDestination?.hasRoute<LibraryMainScreenKey>() == true) return
            navigate(LibraryMainScreenKey) {
                popUpTo(graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }

        is CommandLabDeepLink.Library -> {
            val current =
                currentBackStackEntry
                    ?.takeIf { it.destination.hasRoute<PublicLibraryShowScreenKey>() }
                    ?.toRoute<PublicLibraryShowScreenKey>()
            if (
                current?.id == target.id &&
                    current.isPrivate == target.isPrivate &&
                    current.importToLocal == target.importToLocal &&
                    !target.importToLocal
            )
                return
            navigate(
                PublicLibraryShowScreenKey(
                    id = target.id,
                    isPrivate = target.isPrivate,
                    importToLocal = target.importToLocal,
                )
            ) {
                // 外部深链始终 Home -> 目标页。
                popUpTo(graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}
