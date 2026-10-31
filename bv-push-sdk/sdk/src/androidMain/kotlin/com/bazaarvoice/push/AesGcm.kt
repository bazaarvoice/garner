package com.bazaarvoice.push

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM envelope shared by both secure-store tiers (Keystore key and
 * the software fallback key): `encrypt` returns `iv || ciphertext`, `decrypt`
 * throws on tamper. Pure JCA, exercised on the JVM (androidUnitTest) and on
 * device through both tiers' round trips.
 */
public object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    public fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    public fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "truncated blob" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES),
        )
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }
}
