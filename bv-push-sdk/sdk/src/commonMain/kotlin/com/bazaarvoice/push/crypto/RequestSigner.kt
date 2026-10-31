package com.bazaarvoice.push.crypto

/**
 * Device request signer, byte-exact with the push API's signature check:
 * canonical = timestamp `\n` UPPER(method) `\n` path `\n` lower-hex
 * SHA-256(raw body bytes); signature = lower-hex HMAC-SHA256(canonical).
 * Key: raw 32 bytes behind the base64url deviceApiKey, not the text form.
 * Timestamp: ISO-8601 UTC instant, second precision.
 */
public object RequestSigner {

    public data class SignedHeaders(
        val deviceId: String,
        val appId: Int,
        val timestamp: String,
        val signature: String,
    )

    public fun sign(
        deviceId: String,
        appId: Int,
        deviceApiKey: String,
        method: String,
        path: String,
        body: ByteArray,
        timestamp: String,
    ): SignedHeaders {
        val bodyHash = Hex.encode(Crypto.sha256(body))
        val canonical = "$timestamp\n${method.uppercase()}\n$path\n$bodyHash"
        val key = Base64Url.decode(deviceApiKey)
        val signature = Hex.encode(Crypto.hmacSha256(key, canonical.encodeToByteArray()))
        return SignedHeaders(deviceId, appId, timestamp, signature)
    }
}
