package com.bazaarvoice.push.net

import com.bazaarvoice.push.identity.IdentityInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Typed bodies mirroring the pinned OpenAPI; absent optionals are omitted. */
@Serializable
public data class DeviceRegistrationRequest(
    val appId: Int,
    val platform: String,
    val appIdentifier: String,
    val attestationToken: String,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val sdkVersion: String? = null,
)

@Serializable
public data class DeviceRegistrationResponse(
    val deviceId: String,
    val deviceApiKey: String,
)

@Serializable
public data class TokenUpdateRequest(
    val pushToken: String,
    val platform: String,
)

/**
 * Body of `POST /v1/login`. It names the consumer in exactly one way:
 * - [email] on the first login: the address as the host gave it. The backend normalises and
 *   hashes it with the same code the Bazaarvoice notification platform uses, and answers the
 *   digest as [LoginResponse.emailAddressHash].
 * - [emailAddressHash] on a later login, once both tokens have expired: that answered digest.
 *
 * The SDK never stores the address, only the digest. Absent fields are not sent. [toString]
 * redacts every value, so a log line or crash report never holds the address or the digest.
 */
@Serializable
public data class LoginRequest(
    val userId: String,
    val email: String? = null,
    val emailAddressHash: String? = null,
) {
    override fun toString(): String =
        "LoginRequest(userId=${IdentityInput.REDACTED}, email=${IdentityInput.REDACTED}, " +
            "emailAddressHash=${IdentityInput.REDACTED})"
}

/**
 * Answer of `POST /v1/login`. [emailAddressHash] is the digest the backend stored for the
 * consumer: the SDK keeps it in the secure store with the tokens, instead of the
 * address, and sends it back on a later login.
 */
@Serializable
public data class LoginResponse(
    val userRef: String,
    val authToken: String,
    val authExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val emailAddressHash: String,
)

@Serializable
public data class RefreshResponse(
    val authToken: String,
    val authExpiresAt: String,
)

@Serializable
public data class ErrorEnvelope(
    val code: String,
    val message: String,
    val correlationId: String,
    val retryable: Boolean = false,
)
