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

package yancey.chelper.domain.library

fun validateLibraryEntry(library: LibraryEntry): String? {
    if (library.name.isBlank()) return "名称不能为空"
    if (library.body.isBlank()) return "执行脚本不能为空"
    if (
        sequenceOf(library.name, library.version, library.note, library.tags.joinToString(","))
            .any { '\n' in it || '\r' in it }
    ) {
        return "名称、版本、描述和标签不能包含换行"
    }
    val body = library.body
    if (
        body.contains("###Function###", ignoreCase = true) ||
            body.contains("###End###", ignoreCase = true) ||
            containsMcdMetadataOutsideChat(body)
    ) {
        return "脚本区只填写命令正文，不要粘贴 MCD 头部或 Function 标记"
    }
    val validation = validateMCDContent(body)
    val firstError = validation.lines.firstOrNull { it.type == LineType.AMBIGUOUS }
    if (firstError != null) {
        return "第 ${firstError.lineNumber} 行无法识别，共 ${validation.errorCount} 行需要修正"
    }
    if (library.isV2) {
        val stateRegex =
            Regex("""^>\s*([ICRH_])?([?_])?([!_])?(?:t(\d+|_))?\s*$""", RegexOption.IGNORE_CASE)
        val invalidState =
            body.lines().withIndex().firstOrNull {
                it.value.trim().startsWith(">") && !stateRegex.matches(it.value.trim())
            }
        if (invalidState != null) return "第 ${invalidState.index + 1} 行不是合法的 V2 状态标记"
        findUnboundV2StateLine(body, stateRegex)?.let { line ->
            return "第 $line 行状态标记没有对应的命令或聊天文本"
        }
    }
    return null
}

private fun findUnboundV2StateLine(body: String, stateRegex: Regex): Int? {
    var pendingLine: Int? = null
    var pendingChat = false

    for ((index, line) in body.lines().withIndex()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        if (pendingLine != null) {
            if (pendingChat) {
                pendingLine = null
                pendingChat = false
                continue
            }
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            if (trimmed.startsWith(">") || (trimmed.startsWith("---") && trimmed.endsWith("---"))) {
                return pendingLine
            }
            pendingLine = null
        }

        val match = stateRegex.matchEntire(trimmed) ?: continue
        pendingLine = index + 1
        pendingChat = match.groupValues[1].equals("H", ignoreCase = true)
    }
    return pendingLine
}

private fun containsMcdMetadataOutsideChat(body: String): Boolean {
    val metadataLineRegex =
        Regex("""^@(name|version|tags|note|uuid|mcd_version)\s*=""", RegexOption.IGNORE_CASE)
    val chatStateRegex = Regex("""^>\s*H([?_])?([!_])?(?:t(\d+|_))?\s*$""", RegexOption.IGNORE_CASE)
    var pendingChat = false

    for (line in body.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (pendingChat) {
            pendingChat = false
            continue
        }
        if (chatStateRegex.matches(trimmed)) {
            pendingChat = true
            continue
        }
        if (metadataLineRegex.containsMatchIn(trimmed)) return true
    }
    return false
}
