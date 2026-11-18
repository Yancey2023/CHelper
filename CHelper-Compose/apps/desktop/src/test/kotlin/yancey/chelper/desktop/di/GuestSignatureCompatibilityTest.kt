package yancey.chelper.desktop.di

import io.ktor.client.engine.mock.MockEngine
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlin.io.encoding.Base64
import org.junit.Assert.*
import org.junit.Test
import yancey.chelper.data.remote.NetworkServices

class GuestSignatureCompatibilityTest {
    @Test
    fun commonGuestSignatureVerifiesWithTheLegacyJcaProtocol() =
        NetworkServices({ MockEngine { error("Unexpected request") } }).use { network ->
            network.guest.init("compatibility-device")
            val fingerprint = network.guest.getFingerprint()!!
            assertEquals(
                "b3b1e93798ddc07e5a7ac138c3d3df49caf3bce5de0ae3dbc7adf87c2422a997",
                fingerprint,
            )
            val params =
                AlgorithmParameters.getInstance("EC").apply {
                    init(ECGenParameterSpec("secp256r1"))
                }
            val publicKey =
                KeyFactory.getInstance("EC")
                    .generatePublic(
                        ECPublicKeySpec(
                            ECPoint(
                                BigInteger(
                                    "465476abf280bacf2704d2e084e40d8cf8c271429ba8d92ebc5f64edbfda38d",
                                    16,
                                ),
                                BigInteger(
                                    "5fcdf2a05305dd62c9e8e9774ac12f13ea3413481b354f3c6cba9c3c917b77fb",
                                    16,
                                ),
                            ),
                            params.getParameterSpec(ECParameterSpec::class.java),
                        )
                    )
            val signature = Base64.decode(network.guest.generateAuthCode(fingerprint)!!)
            assertEquals(0x30, signature[0].toInt()) // ASN.1 DER sequence, rather than IEEE P1363.
            assertTrue(
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(publicKey)
                    update(fingerprint.encodeToByteArray())
                    verify(signature)
                }
            )
        }
}
