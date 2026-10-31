package com.bazaarvoice.push.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ModelsTest {
    private companion object {
        const val LOGIN_EMAIL = "Consumer+Shop@Example.COM"
        const val LOGIN_USER = "user-42"
        const val USER_ID_FIELD = "userId"
        const val EMAIL_HASH_FIELD = "emailAddressHash"
        const val EMAIL_FIELD = "email"
        const val LOGIN_EMAIL_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // Every body round-trips through the production Json
    // config (absent optionals omitted); regression cover for the erased-
    // serializer crash (encodeToString must use the concrete type).
    @Test
    fun bodiesRoundTrip() {
        val reg = DeviceRegistrationRequest(
            appId = 10000001, platform = "ANDROID", appIdentifier = "com.x",
            attestationToken = "sentinel", sdkVersion = "0.1.0",
        )
        val regText = json.encodeToString(reg)
        assertFalse(regText.contains("model"), regText)
        assertEquals(reg, json.decodeFromString<DeviceRegistrationRequest>(regText))

        val token = TokenUpdateRequest(pushToken = "t", platform = "ANDROID")
        assertEquals(token, json.decodeFromString(json.encodeToString(token)))

        val login = LoginRequest(userId = "u", email = LOGIN_EMAIL)
        val loginText = json.encodeToString(login)
        assertEquals(login, json.decodeFromString<LoginRequest>(loginText))

        val resp = DeviceRegistrationResponse(deviceId = "d", deviceApiKey = "k")
        assertEquals(resp, json.decodeFromString(json.encodeToString(resp)))

        val loginResp = LoginResponse("r", "a", "e1", "f", "e2", LOGIN_EMAIL_HASH)
        assertEquals(loginResp, json.decodeFromString(json.encodeToString(loginResp)))

        val refreshResp = RefreshResponse("a", "e1")
        assertEquals(refreshResp, json.decodeFromString(json.encodeToString(refreshResp)))
    }

    // The email is the only link key; a login body carries the user id and one email form, nothing else.
    @Test
    fun loginBodyCarriesOnlyItsFields() {
        val first = json.encodeToString(LoginRequest(userId = "u", email = LOGIN_EMAIL))
        assertEquals(setOf(USER_ID_FIELD, EMAIL_FIELD), json.parseToJsonElement(first).jsonObject.keys, first)
        val later = json.encodeToString(LoginRequest(userId = "u", emailAddressHash = LOGIN_EMAIL_HASH))
        assertEquals(setOf(USER_ID_FIELD, EMAIL_HASH_FIELD), json.parseToJsonElement(later).jsonObject.keys, later)
    }

    // The first login carries the plain address under "email" only.
    @Test
    fun firstLoginSendsOnlyThePlainEmail() {
        val loginText = json.encodeToString(LoginRequest(userId = "u", email = LOGIN_EMAIL))
        assertTrue(loginText.contains("\"$EMAIL_FIELD\":\"$LOGIN_EMAIL\""), loginText)
        assertFalse(loginText.contains(EMAIL_HASH_FIELD), loginText)
    }

    // A later login carries only the digest the backend answered, never an address.
    @Test
    fun laterLoginSendsOnlyTheDigest() {
        val loginText = json.encodeToString(LoginRequest(userId = "u", emailAddressHash = LOGIN_EMAIL_HASH))
        assertTrue(loginText.contains("\"$EMAIL_HASH_FIELD\":\"$LOGIN_EMAIL_HASH\""), loginText)
        assertFalse(loginText.contains("\"$EMAIL_FIELD\""), loginText)
    }

    // The login answer carries the digest the SDK keeps instead of the address.
    @Test
    fun loginResponseCarriesTheDigest() {
        val text = """{"userRef":"r","authToken":"a","authExpiresAt":"e1","refreshToken":"f",""" +
            """"refreshExpiresAt":"e2","emailAddressHash":"$LOGIN_EMAIL_HASH"}"""
        assertEquals(LOGIN_EMAIL_HASH, json.decodeFromString<LoginResponse>(text).emailAddressHash)
    }

    @Test
    fun loginToStringRedactsEveryValue() {
        val first = LoginRequest(userId = LOGIN_USER, email = LOGIN_EMAIL).toString()
        assertFalse(first.contains(LOGIN_EMAIL), first)
        assertFalse(first.contains(LOGIN_USER), first)
        val later = LoginRequest(userId = LOGIN_USER, emailAddressHash = LOGIN_EMAIL_HASH).toString()
        assertFalse(later.contains(LOGIN_EMAIL_HASH), later)
    }

    @Test
    fun unknownKeysToleratedOnResponses() {
        val text = """{"deviceId":"d","deviceApiKey":"k","future":"x"}"""
        assertEquals("d", json.decodeFromString<DeviceRegistrationResponse>(text).deviceId)
        assertTrue(json.decodeFromString<ErrorEnvelope>(
            """{"code":"c","message":"m","correlationId":"id"}""").code == "c")
    }
}
