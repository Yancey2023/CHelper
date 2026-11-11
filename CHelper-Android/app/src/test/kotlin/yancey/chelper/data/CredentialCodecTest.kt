package yancey.chelper.data

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class CredentialCodecTest {
    private fun codec(): CredentialCodec {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return CredentialCodec { key }
    }

    @Test
    fun `credentials round trip without exposing plaintext and use fresh IVs`() {
        val codec = codec()
        val original =
            "{\"password\":\"example-password\",\"token\":\"example-token\"}".encodeToByteArray()
        val first = codec.encode(original)
        val second = codec.encode(original)
        assertArrayEquals(original, codec.decode(first))
        assertFalse(first.contentEquals(second))
        assertFalse(first.decodeToString().contains("example-password"))
        assertFalse(first.decodeToString().contains("example-token"))
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampering with ciphertext is detected`() {
        val codec = codec()
        val encoded = codec.encode("credential".encodeToByteArray())
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        codec.decode(encoded)
    }

    @Test(expected = AEADBadTagException::class)
    fun `a different key cannot decrypt an existing session`() {
        codec().decode(codec().encode("credential".encodeToByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated envelopes are rejected before decryption`() {
        codec().decode(byteArrayOf(67, 72, 86, 49))
    }
}
