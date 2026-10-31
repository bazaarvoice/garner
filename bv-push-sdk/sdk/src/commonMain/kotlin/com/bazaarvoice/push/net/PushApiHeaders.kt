package com.bazaarvoice.push.net

/**
 * Header names and header sets of every push API call.
 *
 * The API gateway routes each call on `X-BV-App-Id`, so EVERY call carries it: register,
 * token, login, refresh, logout, opt-out and opt-in. Pure functions, so the header
 * sets are tested without a network.
 */
internal object PushApiHeaders {
    const val APP_ID: String = "X-BV-App-Id"
    const val DEVICE_ID: String = "X-BV-Device-Id"
    const val TIMESTAMP: String = "X-BV-Timestamp"
    const val SIGNATURE: String = "X-BV-Device-Signature"
    const val AUTHORIZATION: String = "Authorization"
    const val BEARER_PREFIX: String = "Bearer "

    /** Registration: no device credential yet, only the app for routing. */
    fun forRegister(appId: Int): Map<String, String> =
        mapOf(APP_ID to appId.toString())

    /** Bearer calls (refresh, logout, opt-out, opt-in). */
    fun forBearer(deviceId: String, appId: Int, bearer: String): Map<String, String> =
        mapOf(
            AUTHORIZATION to BEARER_PREFIX + bearer,
            DEVICE_ID to deviceId,
            APP_ID to appId.toString(),
        )

    /** Device-signed calls (token update, login). */
    fun forSigned(deviceId: String, appId: Int, timestamp: String, signature: String): Map<String, String> =
        mapOf(
            DEVICE_ID to deviceId,
            APP_ID to appId.toString(),
            TIMESTAMP to timestamp,
            SIGNATURE to signature,
        )
}
