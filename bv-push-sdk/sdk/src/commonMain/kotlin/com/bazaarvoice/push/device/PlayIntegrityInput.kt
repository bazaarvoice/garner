package com.bazaarvoice.push.device

import kotlin.io.encoding.Base64

/**
 * Inputs of a classic Play Integrity token request, kept free of Android types so they are
 * tested on the JVM. Android only: iOS App Attest takes its challenge differently.
 */
internal object PlayIntegrityInput {

    private const val FIRST_DIGIT = '0'
    private const val LAST_DIGIT = '9'

    /**
     * Encodes the SDK's request-bound nonce the way Play Integrity requires it: Base64,
     * URL-safe alphabet (`-` and `_`), no line wrap. Play refuses any other nonce on the
     * device, before a token exists (error `NONCE_IS_NOT_BASE64`), so the raw
     * `register:<millis>` must never be passed as is.
     */
    fun nonce(raw: String): String = Base64.UrlSafe.encode(raw.encodeToByteArray())

    /**
     * Reads the host app's Google Cloud project number (the project linked to the app in Play
     * Console). Only digits, above zero; anything else is no number, and the request then
     * goes without one.
     *
     * @param raw the configured value, for example the manifest meta-data string
     * @return the number, or `null` when absent, blank or not a positive whole number
     */
    fun cloudProjectNumber(raw: String?): Long? {
        val digits = raw?.trim().orEmpty()
        if (digits.isEmpty() || !digits.all { it in FIRST_DIGIT..LAST_DIGIT }) return null
        return digits.toLongOrNull()?.takeIf { it > 0L }
    }
}
