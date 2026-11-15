package yancey.chelper.data.local

import okio.FileSystem
import okio.Path

internal fun FileSystem.readText(path: Path): String = read(path) { readUtf8() }

internal fun FileSystem.writeText(path: Path, value: String) {
    path.parent?.let(::createDirectories)
    write(path) { writeUtf8(value) }
}

internal fun FileSystem.isFile(path: Path): Boolean = metadataOrNull(path)?.isRegularFile == true
