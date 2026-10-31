package com.bazaarvoice.push.identity

/**
 * Local checks on the identity a host passes to `linkIdentity`.
 *
 * The email goes to the Garner backend as the host gave it. The backend normalises and hashes it
 * with the same code the Bazaarvoice notification platform uses, so the SDK keeps no copy of those
 * rules. Here the SDK only refuses what the backend would refuse anyway (blank or too long), so a
 * bad call fails on the device instead of after a network round trip.
 */
internal object IdentityInput {
    /** Same limit as the backend's `LoginRequest.userId`. */
    const val USER_ID_MAX_LENGTH: Int = 255

    /** Same limit as the backend's `LoginRequest.email` (the RFC 5321 path limit). */
    const val EMAIL_MAX_LENGTH: Int = 254

    /** Error text for a bad userId. */
    const val INVALID_USER_ID: String = "userId must not be blank"

    /** Error text for a bad email. Names the field, never the value. */
    const val INVALID_EMAIL: String = "email is not a valid address"

    /** Stand-in for identity values in `toString`, so logs never hold them. */
    const val REDACTED: String = "<redacted>"

    fun isValidUserId(userId: String): Boolean =
        userId.isNotBlank() && userId.length <= USER_ID_MAX_LENGTH

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && email.length <= EMAIL_MAX_LENGTH
}
