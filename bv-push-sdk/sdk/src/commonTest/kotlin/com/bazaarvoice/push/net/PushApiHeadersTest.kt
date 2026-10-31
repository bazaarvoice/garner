package com.bazaarvoice.push.net

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The API gateway routes by `X-BV-App-Id`, so every header set carries it.
 * H1-H5 pin the names the backend reads and the value each call sends.
 */
class PushApiHeadersTest {

    private val usAppId = 10_000_403
    private val euAppId = 20_000_403
    private val deviceId = "0b7e3c1a-5d2f-4c1e-9a7b-3f6d2e1c8a90"
    private val bearer = "bearer-value"
    private val timestamp = "2025-01-01T00:00:00Z"
    private val signature = "ab12"

    @Test
    fun h1HeaderNamesMatchBackend() {
        assertEquals("X-BV-App-Id", PushApiHeaders.APP_ID)
        assertEquals("X-BV-Device-Id", PushApiHeaders.DEVICE_ID)
        assertEquals("X-BV-Timestamp", PushApiHeaders.TIMESTAMP)
        assertEquals("X-BV-Device-Signature", PushApiHeaders.SIGNATURE)
        assertEquals("Authorization", PushApiHeaders.AUTHORIZATION)
    }

    @Test
    fun h2RegisterCarriesOnlyTheAppId() {
        assertEquals(mapOf(PushApiHeaders.APP_ID to usAppId.toString()), PushApiHeaders.forRegister(usAppId))
    }

    @Test
    fun h3BearerCallsCarryTheAppId() {
        assertEquals(
            mapOf(
                PushApiHeaders.AUTHORIZATION to PushApiHeaders.BEARER_PREFIX + bearer,
                PushApiHeaders.DEVICE_ID to deviceId,
                PushApiHeaders.APP_ID to euAppId.toString(),
            ),
            PushApiHeaders.forBearer(deviceId, euAppId, bearer),
        )
    }

    @Test
    fun h4SignedCallsCarryTheAppId() {
        assertEquals(
            mapOf(
                PushApiHeaders.DEVICE_ID to deviceId,
                PushApiHeaders.APP_ID to usAppId.toString(),
                PushApiHeaders.TIMESTAMP to timestamp,
                PushApiHeaders.SIGNATURE to signature,
            ),
            PushApiHeaders.forSigned(deviceId, usAppId, timestamp, signature),
        )
    }

    @Test
    fun h5EveryHeaderSetNamesTheSameApp() {
        val sets = listOf(
            PushApiHeaders.forRegister(euAppId),
            PushApiHeaders.forBearer(deviceId, euAppId, bearer),
            PushApiHeaders.forSigned(deviceId, euAppId, timestamp, signature),
        )
        sets.forEach { assertEquals(euAppId.toString(), it[PushApiHeaders.APP_ID]) }
    }
}
