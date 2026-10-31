package com.bazaarvoice.push.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random

private const val LOCAL_BASE_URL = "http://localhost:8080"

class ClassifierTest {
    // Response classifier table.
    @Test
    fun classifierTable() {
        assertEquals(Outcome.PARK_RATE_LIMITED, ResponseClassifier.classify("login", 429, "RATE_LIMITED"))
        assertEquals(Outcome.RETRY_TRANSIENT, ResponseClassifier.classify("login", 503, "DEPENDENCY_UNAVAILABLE"))
        assertEquals(Outcome.RETRY_TRANSIENT, ResponseClassifier.classify("login", 500, "INTERNAL"))
        assertEquals(Outcome.RETRY_FRESH_SIGNATURE,
            ResponseClassifier.classify("token", 401, "INVALID_DEVICE_SIGNATURE"))
        assertEquals(Outcome.REREGISTER, ResponseClassifier.classify("token", 404, "DEVICE_NOT_FOUND"))
        assertEquals(Outcome.REREGISTER, ResponseClassifier.classify("token", 409, "DEVICE_NOT_ACTIVE"))
        assertEquals(Outcome.RELOGIN, ResponseClassifier.classify("refresh", 401, "INVALID_REFRESH_TOKEN"))
        assertEquals(Outcome.BLOCKED, ResponseClassifier.classify("register", 403, "APP_NOT_ACTIVE"))
        assertEquals(Outcome.BLOCKED, ResponseClassifier.classify("register", 403, "ATTESTATION_REJECTED"))
        // A bad App ID header is a plain 400 INVALID_REQUEST, classified like any other.
        assertEquals(Outcome.REREGISTER, ResponseClassifier.classify("token", 400, "INVALID_REQUEST"))
        assertEquals(Outcome.REREGISTER, ResponseClassifier.classify("login", 403, "CLIENT_MISMATCH"))
        assertEquals(Outcome.DROP_INPUT, ResponseClassifier.classify("login", 400, "INVALID_REQUEST"))
        assertEquals(Outcome.SUCCESS, ResponseClassifier.classify("login", 200, null))
    }

    // Retry schedule bounds and Retry-After parsing.
    @Test
    fun retrySchedule() {
        val random = Random(42)
        val first = RetryPolicy.delayForAttempt(0, random)
        assertTrue(first in 750L..1250L)
        assertTrue(RetryPolicy.delayForAttempt(99, random) <= 30_000L)
        assertEquals(5000L, RetryPolicy.retryAfterMillis("5", 0L))
        assertEquals(1000L, RetryPolicy.retryAfterMillis("0", 0L))
        assertEquals(1000L, RetryPolicy.retryAfterMillis(null, 0L))
        // IMF-fixdate: Sun, 06 Nov 1994 08:49:37 GMT == 784111777000.
        assertEquals(784111777000L, RetryPolicy.parseHttpDate("Sun, 06 Nov 1994 08:49:37 GMT"))
        assertEquals(5000L, RetryPolicy.retryAfterMillis("Sun, 06 Nov 1994 08:49:42 GMT", 784111777000L))
        assertEquals(null, RetryPolicy.parseHttpDate("not a date"))
        assertEquals(1000L, RetryPolicy.retryAfterMillis("not a date", 0L))
        // Route literals expand at call time.
        assertEquals("/v1/devices/a/token",
            com.bazaarvoice.push.net.Routes.expand(
                com.bazaarvoice.push.net.Routes.UPDATE_TOKEN, 0, "a"))
        // Config: base URL override and the single default host (one public
        // push gateway, no environments in the SDK).
        assertEquals(LOCAL_BASE_URL, com.bazaarvoice.push.config.BVPushConfig
            .resolveBaseUrl(com.bazaarvoice.push.config.BVPushOptions(baseUrlOverride = LOCAL_BASE_URL)))
        assertEquals(com.bazaarvoice.push.config.BVPushConfig.DEFAULT_BASE_URL,
            com.bazaarvoice.push.config.BVPushConfig.resolveBaseUrl(com.bazaarvoice.push.config.BVPushOptions()))
        assertTrue(com.bazaarvoice.push.config.BVPushConfig.DEFAULT_BASE_URL.startsWith("https://"))
    }
}
