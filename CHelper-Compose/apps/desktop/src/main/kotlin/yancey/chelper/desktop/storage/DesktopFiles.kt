package yancey.chelper.desktop.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile.mkdirs()
    val temporary = Files.createTempFile(file.parentFile.toPath(), file.name, ".tmp")
    try {
        FileOutputStream(temporary.toFile()).use {
            it.write(bytes)
            it.fd.sync()
        }
        try {
            Files.move(
                temporary,
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal class DesktopFiles(val directory: File) {
    private val loader = DesktopFiles::class.java.classLoader

    fun text(path: String): String =
        loader.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            ?: error("缺少内置资源：$path")

    fun extract(path: String): File {
        require(!path.contains("..") && !path.startsWith('/'))
        val bytes =
            loader.getResourceAsStream(path)?.use { it.readBytes() } ?: error("缺少内置资源：$path")
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val destination = File(directory, "bundled/$hash/${File(path).name}")
        if (!destination.exists()) atomicWrite(destination, bytes)
        return destination
    }

    fun nativeLibrary(): File {
        require(
            System.getProperty("os.name").startsWith("Windows") &&
                System.getProperty("os.arch") in setOf("amd64", "x86_64")
        ) {
            "当前发行包支持 Windows 10/11 x64"
        }
        // Load runtime dependencies by absolute path before JNI. Each extraction path is content-
        // addressed so an upgrade never overwrites a DLL held open by another process.
        for (name in
            listOf(
                "vcruntime140.dll",
                "vcruntime140_1.dll",
                "msvcp140_atomic_wait.dll",
                "msvcp140.dll",
            )) {
            if (loader.getResource("native/windows-x64/$name") != null)
                System.load(extract("native/windows-x64/$name").absolutePath)
        }
        return extract("native/windows-x64/CHelperJvm.dll")
    }
}
