package io.github.nfsandroid.core

import java.security.KeyStore
import java.security.cert.X509Certificate

/** The CA certificates Android trusts, the system's and those the user installed (DER). */
object Trust {
    fun roots(): List<ByteArray> {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        return store.aliases().toList().mapNotNull { (store.getCertificate(it) as? X509Certificate)?.encoded }
    }
}
