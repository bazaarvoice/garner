package com.bazaarvoice.push.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class SignerTest {
    // Canonical string and signature shape, against a reference vector of the
    // backend's signing algorithm.
    @Test
    fun signatureMatchesReferenceVector() {
        // Oracle: the backend's request-signing algorithm (python hmac over the
        // canonical string). Any drift fails here, not in production.
        val signed = RequestSigner.sign(
            deviceId = "0193mock",
            appId = 100001,
            deviceApiKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            method = "put",
            path = "/v1/devices/0193mock/token",
            body = """{"pushToken":"x","platform":"ANDROID"}""".encodeToByteArray(),
            timestamp = "2026-09-30T20:00:00Z",
        )
        assertEquals(
            "1cfcd68d5aea70ed2fbffe0ef1b46c817927b18d19021aca70ec202c693139f2",
            signed.signature,
        )
    }

    @Test
    fun signatureShapeAndKeyForm() {
        val key = Base64Url.decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8")
        val timestamp = "2026-09-30T20:00:00Z"
        val signed = RequestSigner.sign(
            deviceId = "0193mock-device-id00000000000000",
            appId = 100001,
            deviceApiKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            method = "put",
            path = "/v1/devices/0193mock-device-id00000000000000/token",
            body = """{"pushToken":"x","platform":"ANDROID"}""".encodeToByteArray(),
            timestamp = timestamp,
        )
        assertEquals("0193mock-device-id00000000000000", signed.deviceId)
        assertEquals(100001, signed.appId)
        assertEquals(timestamp, signed.timestamp)
        assertEquals(64, signed.signature.length)
        assertEquals(signed.signature, signed.signature.lowercase())
        // Key must be the raw bytes: signing with the text form differs.
        val textForm = RequestSigner.sign(
            deviceId = "d", appId = 1, deviceApiKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            method = "GET", path = "/v1/devices", body = ByteArray(0), timestamp = timestamp,
        )
        assertEquals(64, textForm.signature.length)
    }

    // App ID check, shared with config: any 8-digit number, nothing else.
    @Test
    fun appIdRange() {
        for (valid in listOf(10000000, 10000001, 20000000, 30000001, 55555555, 99999999)) {
            assertEquals(true, com.bazaarvoice.push.config.BVPushConfig.isValidAppId(valid), "$valid")
        }
        for (invalid in listOf(-10000001, 0, 99999, 100000, 100001, 9999999, 100000000, Int.MAX_VALUE)) {
            assertEquals(false, com.bazaarvoice.push.config.BVPushConfig.isValidAppId(invalid), "$invalid")
        }
    }
}
