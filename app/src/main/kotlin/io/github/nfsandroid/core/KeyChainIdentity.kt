package io.github.nfsandroid.core

import android.content.Context
import android.security.KeyChain
import uniffi.nfscore.Identity
import uniffi.nfscore.NfsException
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * The client certificate [alias] from Android's KeyChain. Its private key never leaves the
 * KeyChain: the client core asks for each signature the TLS handshake needs.
 */
class KeyChainIdentity(private val context: Context, private val alias: String) : Identity {
    private fun key() = KeyChain.getPrivateKey(context, alias)
        ?: throw NfsException.PermissionDenied("the certificate \"$alias\" is not available to this app")

    override fun chain(): List<ByteArray> = KeyChain.getCertificateChain(context, alias)?.map { it.encoded }.orEmpty()

    override fun algorithm(): String = key().algorithm

    override fun sign(scheme: String, message: ByteArray): ByteArray {
        val signature = when (scheme) {
            "ECDSA_NISTP256_SHA256" -> Signature.getInstance("SHA256withECDSA")
            "ECDSA_NISTP384_SHA384" -> Signature.getInstance("SHA384withECDSA")
            "RSA_PKCS1_SHA256" -> Signature.getInstance("SHA256withRSA")
            "RSA_PSS_SHA256" -> Signature.getInstance("SHA256withRSA/PSS").apply {
                setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
            }
            else -> throw NfsException.Other("unsupported signature scheme $scheme")
        }
        signature.initSign(key())
        signature.update(message)
        return signature.sign()
    }
}
