package yancey.chelper.data

import yancey.chelper.network.library.data.LibraryFunction

private val stateLineRegex =
    Regex(
        """^>\s*([ICRH_])?([?_])?([!_])?(?:t(\d+|_))?\s*$""",
        RegexOption.IGNORE_CASE,
    )
private val mcdVersion2LineRegex =
    Regex(
        """^@mcd_version\s*=\s*2$""",
        RegexOption.IGNORE_CASE,
    )
private val localMetadataLineRegex =
    Regex(
        """^@(name|version|tags|note|mcd_version|uuid)\s*=""",
        RegexOption.IGNORE_CASE,
    )
private val functionMarkerRegex = Regex("(?m)^[ \\t]*###Function###[ \\t]*\\r?$")

fun LibraryFunction.localBody(): String {
    val source = content.orEmpty()
    val lines = source.lines()
    val start = lines.indexOfFirst { it.trim() == "###Function###" }
    if (start < 0) {
        return stripLeadingLocalMcdMetadata(lines)
    }
    val end =
        lines
            .withIndex()
            .firstOrNull { (index, line) -> index > start && line.trim() == "###End###" }
            ?.index ?: -1
    if (end < 0) return source
    return lines.subList(start + 1, end).joinToString("\n").trim('\n', '\r')
}

fun LibraryFunction.usesLocalMcdV2(): Boolean {
    localIsV2?.let {
        return it
    }
    val source = content.orEmpty()
    if (hasLocalMcdV2Header(source)) {
        return true
    }
    return localBody().lineSequence().any { stateLineRegex.matches(it.trim()) }
}

fun LibraryFunction.toFullLocalMcd(): String {
    val source = content.orEmpty()
    val lines = source.lines()
    val start = lines.indexOfFirst { it.trim() == "###Function###" }
    val end =
        lines
            .withIndex()
            .firstOrNull { (index, line) -> index > start && line.trim() == "###End###" }
            ?.index ?: -1
    if (start >= 0 && end > start) {
        val sourceIsV2 = hasLocalMcdV2Header(source)
        val shouldUseV2 = usesLocalMcdV2()
        var normalizedSource =
            when {
                shouldUseV2 && !sourceIsV2 -> insertLocalMcdV2Header(source)
                !shouldUseV2 && sourceIsV2 -> removeLocalMcdV2Header(source)
                else -> source
            }
        if (!uuid.isNullOrBlank()) {
            normalizedSource = upsertLocalMcdHeader(normalizedSource, "uuid", uuid!!)
        }
        return normalizedSource
    }

    return buildString {
        append("@name=${name.orEmpty()}\n")
        append("@version=${version?.ifBlank { "1.0.0" } ?: "1.0.0"}\n")
        if (!tags.isNullOrEmpty()) append("@tags=${tags!!.joinToString(",")}\n")
        append("@note=${note.orEmpty().replace('\n', ' ').replace('\r', ' ')}\n")
        if (usesLocalMcdV2()) append("@mcd_version=2\n")
        if (!uuid.isNullOrBlank()) append("@uuid=$uuid\n")
        append("\n###Function###\n")
        append(localBody())
        append("\n###End###")
    }
}

private fun insertLocalMcdV2Header(source: String): String {
    return upsertLocalMcdHeader(source, "mcd_version", "2")
}

private fun upsertLocalMcdHeader(source: String, key: String, value: String): String {
    val markerIndex = functionMarkerRegex.find(source)?.range?.first ?: return source
    val prefix = source.substring(0, markerIndex)
    val suffix = source.substring(markerIndex)
    val existingLine = Regex("(?im)^[ \\t]*@${Regex.escape(key)}[ \\t]*=[^\\r\\n]*").find(prefix)
    if (existingLine != null) {
        return prefix.replaceRange(existingLine.range, "@$key=$value") + suffix
    }

    val newline = if ("\r\n" in source) "\r\n" else "\n"
    val cleanPrefix = prefix.trimEnd('\r', '\n')
    val leading = if (cleanPrefix.isEmpty()) "" else "$cleanPrefix$newline"
    return "$leading@$key=$value$newline$newline$suffix"
}

private fun removeLocalMcdV2Header(source: String): String {
    val markerIndex = functionMarkerRegex.find(source)?.range?.first ?: return source
    val prefix = source.substring(0, markerIndex)
    val suffix = source.substring(markerIndex)
    val versionHeaderRegex = Regex("(?im)^[ \\t]*@mcd_version[ \\t]*=[ \\t]*2[ \\t]*(?:\\r?\\n|$)")
    return versionHeaderRegex.replace(prefix, "") + suffix
}

private fun hasLocalMcdV2Header(source: String): Boolean {
    val markerIndex = functionMarkerRegex.find(source)?.range?.first
    if (markerIndex != null) {
        return source.substring(0, markerIndex).lineSequence().any {
            mcdVersion2LineRegex.matches(it.trim())
        }
    }

    for (line in source.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (!localMetadataLineRegex.containsMatchIn(trimmed)) break
        if (mcdVersion2LineRegex.matches(trimmed)) return true
    }
    return false
}

private fun stripLeadingLocalMcdMetadata(lines: List<String>): String {
    var index = 0
    while (index < lines.size && lines[index].isBlank()) index++

    var foundMetadata = false
    while (index < lines.size && localMetadataLineRegex.containsMatchIn(lines[index].trim())) {
        foundMetadata = true
        index++
    }
    if (foundMetadata) while (index < lines.size && lines[index].isBlank()) index++

    val bodyStart =
        if (foundMetadata) index
        else lines.indexOfFirst(String::isNotBlank).takeIf { it >= 0 } ?: lines.size
    return lines.drop(bodyStart).joinToString("\n")
}
