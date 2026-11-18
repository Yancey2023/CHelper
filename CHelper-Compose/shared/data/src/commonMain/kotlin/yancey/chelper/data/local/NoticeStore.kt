package yancey.chelper.data.local

import okio.FileSystem
import okio.Path

/** Existing notice markers stay in their historical host directory. */
class NoticeStore(
    private val directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    fun exists(name: String) = fileSystem.exists(directory / name)

    fun read(name: String): String = fileSystem.readText(directory / name)

    fun write(name: String, value: String) = fileSystem.writeText(directory / name, value)
}
