package yancey.chelper.nativecore.loader

import java.io.File

/**
 * Android loads its packaged .so; desktop installs the bundled JNI library before using the core.
 */
object NativeLibrary {
    private var loaded = false

    @Synchronized
    fun load(file: File) {
        if (loaded) return
        System.load(file.canonicalPath)
        loaded = true
    }

    @Synchronized
    internal fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("CHelperAndroid")
        loaded = true
    }
}
