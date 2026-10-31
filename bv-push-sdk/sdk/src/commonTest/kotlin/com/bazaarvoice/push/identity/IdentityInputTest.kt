package com.bazaarvoice.push.identity

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The SDK checks only shape; the Garner backend owns normalisation and hashing. */
class IdentityInputTest {
    private companion object {
        const val DOMAIN = "@example.com"
        const val ADDRESS = "Consumer+Shop@Example.COM"
    }

    /** An address of exactly [length] characters. */
    private fun addressOf(length: Int): String = "a".repeat(length - DOMAIN.length) + DOMAIN

    @Test
    fun emailIsAcceptedAsTyped() {
        // No local normalisation: case, tags and spaces are the backend's business.
        assertTrue(IdentityInput.isValidEmail(ADDRESS))
        assertTrue(IdentityInput.isValidEmail(" $ADDRESS "))
    }

    @Test
    fun emailLengthLimitMatchesBackend() {
        assertTrue(IdentityInput.isValidEmail(addressOf(IdentityInput.EMAIL_MAX_LENGTH)))
        assertFalse(IdentityInput.isValidEmail(addressOf(IdentityInput.EMAIL_MAX_LENGTH + 1)))
    }

    @Test
    fun blankEmailIsRefused() {
        assertFalse(IdentityInput.isValidEmail(""))
        assertFalse(IdentityInput.isValidEmail("   "))
    }

    @Test
    fun userIdRules() {
        assertTrue(IdentityInput.isValidUserId("u".repeat(IdentityInput.USER_ID_MAX_LENGTH)))
        assertFalse(IdentityInput.isValidUserId("u".repeat(IdentityInput.USER_ID_MAX_LENGTH + 1)))
        assertFalse(IdentityInput.isValidUserId(" "))
    }

    @Test
    fun errorTextNeverHoldsTheValue() {
        assertFalse(IdentityInput.INVALID_EMAIL.contains("@"))
        assertTrue(IdentityInput.INVALID_EMAIL.startsWith("email"))
    }
}
