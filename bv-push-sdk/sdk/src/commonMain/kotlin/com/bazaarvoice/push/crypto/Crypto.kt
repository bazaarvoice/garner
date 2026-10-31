package com.bazaarvoice.push.crypto

/**
 * SHA-256 / HMAC-SHA256 primitives. No third-party crypto:
 * expect/actual over JCA (Android/JVM) and CommonCrypto (iOS).
 */
public expect object Crypto {
    public fun sha256(data: ByteArray): ByteArray
    public fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
}

public object Hex {
    private val digits = "0123456789abcdef".toCharArray()

    public fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            out[i * 2] = digits[(b.toInt() ushr 4) and 0x0F]
            out[i * 2 + 1] = digits[b.toInt() and 0x0F]
        }
        return out.concatToString()
    }
}

public object Base64Url {
    /**
     * Decodes base64url restoring padding (the deviceApiKey form): raw 32 bytes,
     * not the text form.
     */
    public fun decode(input: String): ByteArray {
        var s = input.replace('-', '+').replace('_', '/')
        val pad = (4 - s.length % 4) % 4
        repeat(pad) { s += "=" }
        return kotlin.io.encoding.Base64.decode(s)
    }
}
