package yancey.chelper.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption; neither keys nor plaintext credentials are written to disk. */
internal class CredentialCodec(private val key: () -> SecretKey) {
    fun encode(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return MAGIC + cipher.iv + cipher.doFinal(plaintext)
    }

    fun decode(encrypted: ByteArray): ByteArray {
        require(
            encrypted.size >= MAGIC.size + 12 + 16 &&
                encrypted.take(4).toByteArray().contentEquals(MAGIC)
        ) {
            "Invalid credential envelope"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(4, 16)))
        return cipher.doFinal(encrypted, 16, encrypted.size - 16)
    }

    private companion object {
        val MAGIC = byteArrayOf(67, 72, 86, 49)
    }
}

internal class EncryptedCredentialStore(
    context: Context,
    private val keyAlias: String = "yancey.chelper.session.v1",
) {
    private val encrypted = AtomicFile(File(context.noBackupFilesDir, "session.enc"))
    private val legacy = File(context.dataDir, "library/user.json")
    private val codec = CredentialCodec {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)
            ?: KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore",
                )
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                keyAlias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()
    }

    @Synchronized
    fun read(): String? {
        encrypted.openReadOrNull()?.use { input ->
            val result = codec.decode(input.readBytes()).decodeToString()
            removeLegacy()
            return result
        }
        if (!legacy.exists()) return null
        val result = legacy.readText()
        write(result)
        return result
    }

    @Synchronized
    fun write(content: String) {
        val bytes = codec.encode(content.encodeToByteArray())
        val output = encrypted.startWrite()
        try {
            output.write(bytes)
            encrypted.finishWrite(output)
        } catch (e: Exception) {
            encrypted.failWrite(output)
            throw e
        }
        removeLegacy()
    }

    @Synchronized
    fun clear() {
        encrypted.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
        removeLegacy()
    }

    private fun removeLegacy() {
        if (legacy.exists() && !legacy.delete())
            throw IOException("Unable to remove legacy credentials")
    }
}
