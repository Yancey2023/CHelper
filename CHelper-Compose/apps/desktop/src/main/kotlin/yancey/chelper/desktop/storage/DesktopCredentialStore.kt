package yancey.chelper.desktop.storage

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import yancey.chelper.domain.auth.CredentialStore

/** Windows DPAPI binds persisted credentials to the current OS user. */
internal class DesktopCredentialStore(private val file: File) : CredentialStore {
    @Synchronized
    override fun read(): String? =
        if (file.exists()) Crypt32Util.cryptUnprotectData(file.readBytes()).decodeToString()
        else null

    @Synchronized
    override fun write(content: String) {
        atomicWrite(file, Crypt32Util.cryptProtectData(content.encodeToByteArray()))
    }

    @Synchronized
    override fun clear() {
        check(!file.exists() || file.delete()) { "无法清除登录凭据" }
    }
}
