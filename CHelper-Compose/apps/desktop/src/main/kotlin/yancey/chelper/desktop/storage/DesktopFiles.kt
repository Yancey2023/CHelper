package yancey.chelper.desktop.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal fun defaultDesktopDataDirectory() =
    File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "CHelper")

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

    fun extract(path: String): File = extract(path, directory)

    private fun extract(path: String, targetDirectory: File): File {
        require(!path.contains("..") && !path.startsWith('/'))
        val bytes =
            loader.getResourceAsStream(path)?.use { it.readBytes() } ?: error("缺少内置资源：$path")
        val hash =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val destination = File(targetDirectory, "bundled/$hash/${File(path).name}")
        if (!destination.exists()) {
            try {
                atomicWrite(destination, bytes)
            } catch (e: FileSystemException) {
                // Another process may have installed and loaded the same content-addressed file.
                if (!destination.isFile) throw e
            }
        }
        return destination
    }

    fun nativeLibrary(): File {
        require(
            System.getProperty("os.name").startsWith("Windows") &&
                System.getProperty("os.arch") in setOf("amd64", "x86_64")
        ) {
            "当前发行包支持 Windows 10/11 x64"
        }
        // OpenJDK's DLL loader encodes paths using the Windows process code page.
        val encoding = Charset.forName(System.getProperty("native.encoding")).newEncoder()
        val targetDirectory =
            if (encoding.canEncode(directory.canonicalPath)) directory
            else
                defaultDesktopDataDirectory().also {
                    check(encoding.canEncode(it.canonicalPath)) {
                        "当前 Windows 代码页无法表示内核路径，请选择可表示的数据目录或启用 UTF-8"
                    }
                }
        // Load runtime dependencies by absolute path before JNI. Each extraction path is content-
        // addressed so an upgrade never overwrites a DLL held open by another process.
        for (name in
            listOf(
                "vcruntime140.dll",
                "vcruntime140_1.dll",
                "msvcp140.dll",
                "msvcp140_1.dll",
                "msvcp140_atomic_wait.dll",
            )) {
            val path = "native/windows-x64/$name"
            if (loader.getResource(path) != null)
                System.load(extract(path, targetDirectory).canonicalPath)
        }
        return extract("native/windows-x64/CHelperJvm.dll", targetDirectory)
    }
}
