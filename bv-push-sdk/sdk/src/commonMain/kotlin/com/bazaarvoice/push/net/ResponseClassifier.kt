package com.bazaarvoice.push.net

/**
 * Maps (call, HTTP status, code) to an outcome — pure function.
 * Retryable: network/timeout/5xx incl. 503 DEPENDENCY_UNAVAILABLE
 * and INTERNAL; 429 parks on Retry-After; double-401 wipes registration.
 */
public enum class Outcome {
    SUCCESS,
    RETRY_TRANSIENT,
    PARK_RATE_LIMITED,
    RETRY_FRESH_SIGNATURE,
    REREGISTER,
    RELOGIN,
    BLOCKED,
    DROP_INPUT,
    PENDING_PREFERENCE,
    CLEAR_PENDING_LOGOUT,
}

public object ResponseClassifier {

    public fun classify(call: String, status: Int, code: String?): Outcome {
        if (status == 429) return Outcome.PARK_RATE_LIMITED
        if (status in 500..599 || status == 408) return Outcome.RETRY_TRANSIENT
        return when (code) {
            "INVALID_DEVICE_SIGNATURE" -> Outcome.RETRY_FRESH_SIGNATURE
            "DEVICE_NOT_FOUND" -> Outcome.REREGISTER
            "DEVICE_NOT_ACTIVE" -> Outcome.REREGISTER
            "INVALID_REFRESH_TOKEN" -> Outcome.RELOGIN
            "APP_NOT_ACTIVE", "APP_IDENTIFIER_MISMATCH", "ATTESTATION_REJECTED" -> Outcome.BLOCKED
            // A missing, malformed or unknown X-BV-App-Id gets INVALID_REQUEST: 412 from the push
            // gateway or 400 from the backend. Mapped by code, so both act the same.
            "CLIENT_MISMATCH" -> Outcome.REREGISTER
            "INVALID_REQUEST" -> if (call == "login") Outcome.DROP_INPUT else Outcome.REREGISTER
            else -> if (status in 200..299) Outcome.SUCCESS else Outcome.RETRY_TRANSIENT
        }
    }
}
