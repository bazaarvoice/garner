package com.bazaarvoice.push.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoVectorsTest {
    // SHA-256 against NIST FIPS 180-4 examples.
    @Test
    fun sha256Nist() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hex.encode(Crypto.sha256("abc".encodeToByteArray())),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hex.encode(Crypto.sha256(ByteArray(0))),
        )
    }

    // HMAC-SHA256 against RFC 4231 test case 1.
    @Test
    fun hmacRfc4231() {
        val key = ByteArray(20) { 0x0b }
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            Hex.encode(Crypto.hmacSha256(key, "Hi There".encodeToByteArray())),
        )
    }

    // base64url decoding with restored padding + hex encoding.
    @Test
    fun base64UrlRestoresPadding() {
        // 32 bytes 0x00..0x1f without padding.
        val raw = ByteArray(32) { it.toByte() }
        val unpadded = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        assertEquals(raw.toList(), Base64Url.decode(unpadded).toList())
    }
}
